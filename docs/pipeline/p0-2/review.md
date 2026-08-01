# P0-2 审查结论：PASS

## 审查证据

- 对比 `baseline/scheduler.ts` 与 `server/src/scheduler.ts`（`git diff --no-index`）：改动仅为
  - `fetched` / `accepted` 由 `DraftArticle[]` 改为 `{ article, sourceId }[]`，`sourceId` 取自 `FetchSourceResult.sourceId`；
  - `insertCandidate` 新增 `sourceId` 参数，`items.source_id` 由 `null` 改为 `sourceId`；
  - `daysAgoIso` 由 `d.toISOString()` 改为 `formatLocalIso(d)`；
  - 其余为函数签名与解构适配，无行为外改动。
- 范围核查：`server/src` 与主仓逐文件 SHA256 对比，唯一差异为 `scheduler.ts`；未改动 `fetcher.ts` / `types.ts` / `db.ts` / 数据库 schema；`docs/pipeline/p0-2/report.md` 为交接单产物。
- 候选插入路径无硬编码 `source_id: null`：`insertCandidate` 使用 `source_id: sourceId` 与 `@source_id` 绑定（`scheduler.ts:151-194`）。
- 聚合异常奏折保持 NULL：`insertExceptionItem` 的 SQL 显式写 `NULL`（`scheduler.ts:207`），`ai_reason` 为 `采集失败原因：{errorText}`，其中 `errorText` 含各源名称与错误信息，已说明涉及源。
- `daysAgoIso` 使用 `formatLocalIso`（`scheduler.ts:316-320`）；`db.ts:242-251` 返回本地无时区 ISO `YYYY-MM-DDTHH:mm:ss.mmm`，与 `nowIso` / `created_at` 同口径，reject_logs 7 天去重窗口不再混用 UTC。
- 无其他 article 摊平丢来源路径：`fetched.push(...accepted)` 传播的是 `{ article, sourceId }` 元组，accepted → 拟稿 → 排序 → 插入全链路均携带 sourceId。
- `fetcher.ts` 中 `FetchSourceResult.sourceId` 恒等于 `source.id`（`fetcher.ts:12/16/19/27`），与 fetch_logs 的 `source_id` 口径一致。

## 独立验证

命令：`cd server && npm.cmd run build`

输出摘要：

```text
> sansheng-liubu-server@0.1.0 build
> tsc -p tsconfig.json
```

退出码：`0`

`server/src/scheduler.ts` SHA256：`419D61B9349BB858B8CB0C1EDAF5919F1E8B9CE476A42583BF2539E9D50CAAF8`，与执行交接单一致。

## 残留风险

p0-4 的 `npm run smoke` 尚未落地，sourceId 与 fetch_log 一致性的运行时断言留待 p0-4 验证；`countItemsCreatedToday` 仍使用 `date('now','localtime')`，与本 P 本地无时区 `created_at` 口径一致，但本 P 未改动该处。
