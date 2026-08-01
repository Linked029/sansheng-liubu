# P0-1 交接报告（时间戳本地化）

## 完成内容

- `nowIso()` 改为返回本地无时区 ISO：`YYYY-MM-DDTHH:mm:ss.SSS`，无 `Z` 或 `+HH:MM` 偏移。
- 新增并导出 `formatLocalIso(date)`，作为唯一本地格式化出口。
- 新增并导出 `migrateTimestampFormat(db)`，`getDb()` 初始化时在 `initSchema(db)` 之后自动调用，幂等迁移存量时间戳。
- 迁移覆盖：`items.created_at/approved_at/archived_at/read_at`、`reviews.due_at/last_reviewed_at`、`annotations.created_at`、`fetch_logs.created_at`、`reject_logs.created_at`、`scheduler_state.last_run_at`。
- 迁移只转换带 `Z` 或 `+HH:MM`/`+HHMM` 偏移的完整 ISO 时间戳；本地无时区字符串与普通文本保持不变；重复执行结果不变。
- `todayDate()` 保持本地 `YYYY-MM-DD`，未改语义。
- 数据库 schema 未改动；未触碰 `scheduler.ts`、`api.ts` 等其他文件。

## 改动文件清单

- `server/src/db.ts`
  - baseline SHA256：`2BF5EB479A57B731B61BFE262B84C76DAF9C6AC5C940F4F8E72F86B43038BA4F`
  - 改动后 SHA256：`FC8EC15E9958AFC03871D1E0C38ED0259D17E9669BA77188EA0D1DBA7481AC2B`
- `docs/pipeline/p0-1/report.md`（本报告，新增文件，不在 baseline 清单内）

## 如何复现验证

命令：

```powershell
cd C:\Users\ruihan\Documents\三省六部\work\p0-1-ws\server
npm.cmd run build
```

结果（退出码 0）：

```text
> sansheng-liubu-server@0.1.0 build
> tsc -p tsconfig.json
```

附加功能自检（临时 `:memory:` SQLite，调用导出的 `migrateTimestampFormat`）：

- `nowIso()` 返回形如 `2026-08-01T14:43:44.522`，正则校验通过。
- `2026-08-01T05:30:00.000Z` 转为 `2026-08-01T13:30:00.000`（本机 Asia/Shanghai）。
- `+08:00`、`-05:00` 偏移分别按本地时钟正确换算。
- `date(created_at)` 与本地日期 `2026-08-01` 一致。
- 已转换值重复迁移前后快照一致（幂等）；本地无时区字符串与普通文本保持不变。

## 偏离记录

无。

## 遗留风险与给下一个 P 的提示

- `api.ts` 的 `importData` 仍写 `new Date(...).toISOString()`（UTC ISO），由 p0-3 改为 `formatLocalIso` 口径；本次迁移只做存量兜底。
- `scheduler.ts` 的 `daysAgoIso` 仍返回 UTC ISO，由 p0-2 改为本地无时区口径。
- 迁移正则只匹配完整 ISO 且带 `Z`/偏移的字符串；若 p0-4 smoke 断言包含空格分隔或更混合的存量格式，需同步扩展正则。
- `sources.last_fetched_at` 不在本次迁移列中，如后续需要统一，应另开写集。
- 本地无时区存储意味着跨时区移动后墙钟时间不变；跨时区需求按 spec 另开 P。
