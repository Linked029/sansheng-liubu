# P0-2 执行报告：scheduler source_id 关联

## 完成内容

- `doRunMinistryFetch` 不再把 article 摊平丢失来源：`fetched` 与 `accepted` 改为携带 `{ article, sourceId }`，`sourceId` 取自 `FetchSourceResult.sourceId`，贯穿 accepted、拟稿、排序、插入全链路。
- `insertCandidate` 新增 `sourceId` 入参，写入 `items.source_id = sourceId`；候选插入路径不再存在硬编码 `source_id: null`。
- 仅“全部源失败”的聚合异常奏折保留 `source_id = NULL`，其 `ai_reason` 仍包含失败来源名称与原因。
- `daysAgoIso(7)` 改为使用 p0-1 交付的 `db.formatLocalIso`，返回与 `created_at` 同口径的本地无时区 ISO，reject_logs 7 天去重窗口不再混用 UTC。
- 代码自检：候选插入路径无硬编码 `source_id: null`；聚合异常路径 SQL 保留 `NULL`；`daysAgoIso` 使用 `formatLocalIso`。

## 改动文件清单

| 文件 | 说明 | SHA256 |
| --- | --- | --- |
| `server/src/scheduler.ts` | baseline（改动前） | `6BB0B00378B3EC3DD3A1E79A7AEF3A9D6B1720A8293449BDF7A48507A5C2B4B4` |
| `server/src/scheduler.ts` | 改动后 | `419D61B9349BB858B8CB0C1EDAF5919F1E8B9CE476A42583BF2539E9D50CAAF8` |
| `docs/pipeline/p0-2/report.md` | 本报告 | 见交接产物 |

仅修改 `server/src/scheduler.ts`（唯一写集），并新增本报告。

## 如何复现验证

在 `C:\Users\ruihan\Documents\三省六部\work\p0-2-ws\server` 下执行：

```powershell
npm.cmd run build
```

结果：`tsc -p tsconfig.json` 执行成功，退出码 0，无 TypeScript 错误（strict 编译通过）。

SHA256 复核：

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath 'C:\Users\ruihan\Documents\三省六部\work\p0-2-ws\server\src\scheduler.ts'
```

结果：`419D61B9349BB858B8CB0C1EDAF5919F1E8B9CE476A42583BF2539E9D50CAAF8`。

## 偏离记录

无。未改动 `fetcher.ts` / `types.ts` / `db.ts` / 数据库 schema，未运行 git 命令，未触碰主干 `server/`。

## 遗留风险与给下一个 P 的提示

- 本 P 仅保证“进入拟稿的 article”携带来源；若后续在 `fetched` 之后新增二次过滤或合并路径，需继续以 `{ article, sourceId }` 打包传递，避免再次摊平丢来源。
- `countItemsCreatedToday` 仍使用 SQLite `date('now','localtime')`，与本地无时区 `created_at` 口径一致，本 P 未改动；p0-4 的 smoke 断言若涉及该统计，建议按同一本地口径断言。
- p0-4 落地 smoke 时，建议断言：本地 URL 源执行调度后，生成候选的 `sourceId` 等于该源 id，且对应 `fetch_logs.source_id` 一致；全部源失败时聚合异常奏折 `source_id` 为 NULL。
