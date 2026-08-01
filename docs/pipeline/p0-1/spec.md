# P0-1 任务卡：时区统计修复

- 依赖：无
- 写集：`server/src/db.ts`（唯一写集；为保持写集不重叠，`server/src/scheduler.ts` 的 `daysAgoIso` 与 `server/src/api.ts` 的 importData 分别在 p0-2、p0-3 收敛）
- 验证命令：`cd server && npm run build`；p0-4 落地后 `cd server && npm run smoke`

## 目标

根因修复：持久化时间戳统一为“本地无时区 ISO”（`YYYY-MM-DDTHH:mm:ss.SSS`，无 `Z`/偏移），`todayDate()` 保持本地 `YYYY-MM-DD`，并对存量 UTC 行做幂等迁移。这样 dashboard 的 `date(created_at)`/`date(archived_at)`、scheduler 的 `date(created_at) = date('now','localtime')`、fetch_logs.date、scheduler_state.last_run_date 全部落在同一本地口径，不再把 UTC 字符串与本地日期直接比较。

## 验收标准

- [ ] `db.ts` 中 `nowIso()` 改为返回本地无时区 ISO，不含 `Z` 或 `+HH:MM` 偏移；`todayDate()` 继续返回同一本地时钟的 `YYYY-MM-DD`。
- [ ] 本地格式化逻辑封装为导出函数（如 `formatLocalIso(date)`），供 p0-3 的 importData 与 p0-4 的 smoke 直接调用。
- [ ] `getDb()` 初始化时执行幂等迁移：对 `items.created_at/approved_at/archived_at/read_at`、`reviews.due_at/last_reviewed_at`、`annotations.created_at`、`fetch_logs.created_at`、`reject_logs.created_at`、`scheduler_state.last_run_at` 中带 `Z` 或时区偏移的存量字符串，统一转为本地无时区 ISO；重复打开数据库不改变已转换值。
- [ ] 迁移逻辑封装为导出函数（如 `migrateTimestampFormat(db)`），供 smoke 直接调用验证幂等性。
- [ ] 迁移后，dashboard 按日统计 SQL 无需加 `'localtime'` 修饰即与 `todayDate()` 对齐。
- [ ] `cd server && npm run build` 通过。
- [ ] 代码审查可验证：`db.ts` 中任何持久化时间戳写入/迁移都经过本地格式化函数，不存在把 UTC ISO 直接落库的路径（API 响应级健康时间除外）。
- [ ] p0-4 落地后 `npm run smoke` 断言：直插一条 `...Z` 存量行并调用迁移函数后，值转为本地无时区且 `date(created_at)` 等于本地对应日期；dashboard `createdToday` 只计本地今日。

## 范围

做：

- 修改 `nowIso()` 语义、新增本地格式化 helper、新增并接入幂等存量迁移。

不做：

- 不改 `scheduler.ts` 的 `daysAgoIso`（p0-2 写集）。
- 不改 `api.ts` 的 importData 时间戳（p0-3 写集）。
- 不改 cron 的本地执行语义（保持按本地时钟判断到点）。
- 不改数据库 schema。

## 风险

- 存量数据格式混杂：迁移必须幂等且只匹配带时区标记的字符串，避免重复转换或误转普通文本。
- 本机时区变化：本地无时区存储意味着跨时区移动后墙钟时间不变；对个人本机工具可接受，跨时区需求需另开 P。
