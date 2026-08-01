# P0-4 交接报告（冒烟测试与 verify）

## 完成内容

- `server/package.json` 新增 `"smoke": "tsx src/smoke.ts"` 脚本。
- 新增 `server/src/smoke.ts`：用 `SSS_DB_PATH` 指向临时目录 SQLite（动态 import db/api，避免落到仓库 `data/`），覆盖：
  - health：`GET /api/health` 200 且 `ok=true`；
  - CRUD：创建自定义六部、创建/更新信息源、偏好卡读取与 `dailyLimit` 钳制（99→10、0→1）、删除默认六部返回 400、删除自定义六部成功；
  - 奏折全流程：创建 candidate → 准奏 → archived 且 approvedAt/archivedAt 非空；创建 → 驳回 → 条目删除且 reject_logs 有记录；创建 → 打回（AI 未配置）→ pending、redraft_count=1、原稿保留、ai_reason 含原因与“AI 未配置”；
  - 重拟 AI 路径：本地 mock OpenAI-compatible `/chat/completions` 返回固定 JSON，重拟结果等于 mock 返回值、redraft_count 再 +1、approvedAt/archivedAt 保持 NULL；
  - 调度器：本地 URL 源运行后候选 sourceId 等于源 id、fetch_logs 有 ok 记录且 date 等于本地今日、再次运行 URL 去重 newCandidates=0、手动源产生 error fetch_log 且含“手动源无需定时抓取”；
  - 时区回归：直插 `...Z` 存量行后调用 `migrateTimestampFormat`，转为本地无时区 ISO、`date(created_at)` 等于本地日期、重复迁移幂等、dashboard `createdToday` 按本地今日计数。
- dashboard 回归补充 `archivedToday`：准奏流程前后断言增量 +1，覆盖 spec 的 createdToday/archivedToday 双口径。
- 清理：`main()` 使用 `try/finally`，finally 内依次关闭本地 server 与 mock 监听、关闭 SQLite、删除临时目录；失败路径同样走 finally 清理，catch 只负责报错并以退出码 1 结束，不触碰仓库 `data/`。

## 改动文件清单

| 文件 | 说明 | SHA256 |
| --- | --- | --- |
| `server/package.json` | 新增 smoke script | `6BBAA5D70D73AD07240FD6A39094D511BD12208498306DBDB2126DF49C7964DE` |
| `server/src/smoke.ts` | 新增冒烟测试 | `DD47F4CEF884EE91DC4035214E718D3511BFC1A12396A8B6FDB0BCC98744500A` |

## 如何复现验证

在 `server/` 下执行：

```powershell
npm.cmd run build
npm.cmd run smoke
```

结果（均退出码 0）：

```text
> sansheng-liubu-server@0.1.0 build
> tsc -p tsconfig.json

PASS health
PASS ministries/sources/preferences CRUD
PASS item approve/reject/redraft(no-ai)
PASS redraft with AI mock
PASS scheduler fetch_log/source_id/dedupe/manual
PASS timezone migration/dashboard
SMOKE PASS: health/CRUD/item-flow/redraft/scheduler/timezone 全部断言通过
```

## 偏离记录

- P0 写集约定中 p0-4 原计划由独立执行 Agent 实现；用户确认改为 main 直接实现 + 独立审查 Agent 全量验证，写集与验收标准不变。
- 审查 REWORK 返工：补 `archivedToday` 断言；清理改为 `try/finally` 且失败路径不再吞删除异常。返工后 build+smoke 均退出码 0。

## 遗留风险与给下一个 P 的提示

- smoke 的“手动源 error 提示”断言按现状语义固定为“手动源无需定时抓取”，后续如改文案需同步更新断言。
- `api.ts` 的 health/export 响应级时间仍为 UTC ISO（API 响应而非持久化），spec 允许；如需统一可在 P3 打磨处理。
- web 本轮无写集，回归命令保持 `cd archive-assistant-web && npm run build && npm run lint`。
