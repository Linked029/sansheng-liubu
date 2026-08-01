# P0-2 任务卡：source_id 关联

- 依赖：p0-1
- 写集：`server/src/scheduler.ts`（唯一写集）
- 验证命令：`cd server && npm run build`；p0-4 落地后 `cd server && npm run smoke`

## 目标

调度器生成候选奏折时丢失来源：`insertCandidate` 恒写 `source_id = null`。本 P 让每个候选奏折携带实际来源 `source_id`，并顺带把同文件内 `daysAgoIso` 的时间口径收敛到 p0-1 的本地无时区格式。聚合型异常奏折（全部启用源失败）无单一来源，保持 NULL。

## 验收标准

- [ ] `doRunMinistryFetch` 为每个被接受并进入拟折的 article 保留其来源（`FetchSourceResult.sourceId`），候选插入时 `items.source_id` 等于该 article 实际来源 id。
- [ ] 代码审查：候选插入路径不存在硬编码 `source_id: null`；仅全部源失败生成的聚合异常奏折保持 `source_id = NULL`，且其 ai_reason 已说明涉及源。
- [ ] `daysAgoIso(7)` 改为返回与 `created_at` 同口径的本地无时区 ISO，reject_logs/items 的 7 天去重窗口不再混用 UTC。
- [ ] `cd server && npm run build` 通过。
- [ ] p0-4 落地后 `npm run smoke` 断言：对本地 URL 源执行调度器后，生成的候选奏折 `sourceId` 等于该源 id，且对应 fetch_log 的 `source_id` 一致。

## 范围

做：

- 在 `scheduler.ts` 内将 article 与来源 sourceId 打包传递（如 `{ article, sourceId }`），贯穿 accepted、拟折、插入全链路。
- `insertCandidate` 增加 sourceId 入参并写入 `source_id`。
- 将 `daysAgoIso` 改为使用 p0-1 交付的本地格式化 helper。

不做：

- 不改手动源语义（保持现状：手动源不产出文章，调度器写一条 error fetch_log）。
- 不改 `fetcher.ts` / `types.ts`（来源归属在 scheduler 内维护）。
- 不改数据库 schema。

## 风险

- fetched 数组摊平会丢来源：实现必须把 article 与 sourceId 打包传递，避免再次摊平丢失。
- 异常奏折为多源聚合，不能强行指定单一 `source_id`；验收明确允许 NULL。
