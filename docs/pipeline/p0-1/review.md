# P0-1 审查结论（门下）

结论：PASS

## 审查方法

- 使用 `git diff --no-index` 对照 `baseline/db.ts` 与 `server/src/db.ts`，diff 仅涉及 `nowIso()`、新增 `formatLocalIso()`、`getDb()` 迁移接入与新增 `migrateTimestampFormat()`。
- 对 `work/p0-1-ws/server` 下所有非 `node_modules`、非 `dist` 文件与主仓逐文件 SHA256 比对，仅 `server/src/db.ts` 有差异，无越界改动。
- 独立执行 `cd server && npm.cmd run build`。
- 用临时 `:memory:` SQLite 调用导出的 `migrateTimestampFormat()` 做功能验证，不采用执行 Agent 的自述。

## 验证命令输出摘要

`npm.cmd run build` 退出码 0，输出：

```text
> sansheng-liubu-server@0.1.0 build
> tsc -p tsconfig.json
```

迁移功能验证（本机 Asia/Shanghai，`getTimezoneOffset()` 为 -480）：

- `2026-08-01T05:30:00.000Z` 转为 `2026-08-01T13:30:00.000`
- `2026-08-01T13:30:00+08:00` 转为 `2026-08-01T13:30:00.000`
- `2026-08-02T01:30:00+0800` 转为 `2026-08-02T01:30:00.000`
- `2026-08-01T05:30:00.000+05:00` 转为 `2026-08-01T08:30:00.000`
- 本地无时区字符串与普通文本保持不变
- `date(created_at)` 返回 `2026-08-01`，与 `todayDate()` 口径一致
- 连续两次执行迁移前后快照一致，幂等
- `nowIso()` 输出形如 `2026-08-01T14:46:31.169`，无 `Z` 或偏移
- 空库调用迁移不报错

## 验收核对

- `nowIso()` 返回本地无时区 ISO，`todayDate()` 保持本地 `YYYY-MM-DD`：通过
- 本地格式化逻辑导出为 `formatLocalIso(date)`：通过
- `getDb()` 在 `initSchema()` 后执行迁移，覆盖 spec 列出的全部表和列：通过
- 迁移逻辑导出为 `migrateTimestampFormat(db)`，且仅转换带 `Z`/偏移的字符串：通过
- 迁移后 `date(created_at)` 与本地日期对齐：通过
- `cd server && npm run build` 退出码 0：通过
- `db.ts` 内持久化时间戳写入均经过 `formatLocalIso()`，未发现 UTC ISO 直接落库路径：通过
- p0-4 smoke 的断言按计划由 p0-4 落地覆盖：待 p0-4

## 残留风险

- `api.ts` 的 `importData`/health/export 与 `scheduler.ts` 的 `daysAgoIso` 仍存在 UTC ISO 写入或比较，按 spec 分别由 p0-3/p0-2 收敛，本 P 结论不依赖它们。
- `sources.last_fetched_at` 不在本 P 迁移列中，若后续需要统一可另开写集。
- 本地无时区存储意味着跨时区移动后墙钟时间不变，符合 spec 的取舍。
