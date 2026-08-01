# P0-3 审查结论（门下）

结论：**PASS**

## 审查对象

- spec：`docs/pipeline/p0-3/spec.md`
- plan：`docs/pipeline/plan.md`
- baseline：`baseline/ai.ts`、`baseline/api.ts`（`baseline-manifest.txt` 仅登记这两个文件）
- 工作区改动：`server/src/ai.ts`、`server/src/api.ts`
- 交接单：`docs/pipeline/p0-3/report.md`

## 范围检查

- `git diff --no-index baseline/ai.ts server/src/ai.ts`：仅新增 `RedraftResult` 与 `redraftArticle`。
- `git diff --no-index baseline/api.ts server/src/api.ts`：仅新增导入、redraft 路由改 async 并接入重拟、`importData` 时间口径改 `formatLocalIso`。
- 工作区 `server/src` 其余文件（db.ts、fetcher.ts、index.ts、migrate.ts、scheduler.ts、types.ts）SHA256 与主仓一致，无越界改动。
- 未改 schema，未改 `draftArticle`，未改审批/驳回路由。

## 验证命令输出摘要

1. 构建（独立重跑）：

```powershell
cd C:\Users\ruihan\Documents\三省六部\work\p0-3-ws\server
npm.cmd run build
```

输出：`tsc -p tsconfig.json` 通过，退出码 0。

2. 独立行为验证（临时 SQLite + 本地 mock，node 脚本）：8/8 断言通过。

| 断言 | 结果 |
| --- | --- |
| 创建 candidate 条目 | PASS |
| AI 未配置：status=pending、redraft_count=1、title/summary/qualityScore 保持原值、approved_at/archived_at=NULL、ai_reason 含“打回重拟”+原因+“AI 未配置” | PASS |
| AI 已配置（mock 返回 qualityScore=950）：title/summary/aiReason 更新、qualityScore clamp 为 100、redraft_count=2、approved_at/archived_at=NULL | PASS |
| AI 已配置（mock 返回 -10）：qualityScore clamp 为 0、redraft_count=3 | PASS |
| AI 调用失败（HTTP 500）：title/summary/qualityScore 保留原值、ai_reason 含“AI 调用失败”、redraft_count=4 | PASS |
| 已归档条目打回返回 400 | PASS |
| 不存在条目返回 404 | PASS |
| importData：created_at/archived_at 为本地无时区 `YYYY-MM-DDTHH:mm:ss.SSS`，不含 `Z` | PASS |

## 逐条验收对照

- `redraftArticle` 导出：`server/src/ai.ts:212`；入参含 title/summary/fullText/qualityScore、原因、ministry、preference、settings（`ai.ts:219-225`）；未配置返回原 title/summary/qualityScore 且 aiReason 带“打回重拟”+原因+“AI 未配置”（`ai.ts:226-233`）；已配置返回新字段且 qualityScore clamp 0-100（`ai.ts:257-266`，clamp 见 `ai.ts:207-209`）；空 endpoint、非 2xx、解析/异常均 fallback 并标注“AI 调用失败”（`ai.ts:256-268`）。
- redraft API 为 async：`server/src/api.ts:476`；仅 candidate/pending 可打回（`api.ts:484`），其余返回 400，不存在返回 404（`api.ts:480-482`）；单条 UPDATE 内 `redraft_count = redraft_count + 1` 且 `approved_at = NULL, archived_at = NULL`（`api.ts:504-506`）。
- `importData` 的 createdAtIso 改由 `formatLocalIso(new Date(createdAt))` 输出，默认 archivedAt 沿用同一本地值（`api.ts:793-794`），`importData` 内不再使用 `toISOString()`。
- `npm run build` 通过（见上）。

## 问题清单

无。

## 残留风险

- `npm run smoke` 属 p0-4 落地范围，本 P 行为覆盖由本次审查的临时 SQLite + 本地 mock 完成，p0-4 仍需补正式 smoke 断言。
- `importData` 对显式传入的 `archivedAt` 仍按原值落库（未统一转本地无时区 ISO），交接单已披露，属 p0-4 可收口项，不构成本 P 验收阻塞。
- 默认 settings 自带 baseUrl/modelName，未配置路径需先清空两项设置；smoke 需先 `PUT /api/settings/ai` 置空再测（交接单已提示）。
