# P0-4 任务卡：冒烟测试与 verify

- 依赖：p0-1、p0-2、p0-3
- 写集：`server/package.json`、`server/src/smoke.ts`（新增；不修改业务源码）
- 验证命令：`cd server && npm run build && npm run smoke`；web 本轮无写集，验证命令保持 `cd archive-assistant-web && npm run build && npm run lint`

## 目标

为 P0 建立可重复的机器验收：`npm run smoke` 用临时 SQLite + 本地 HTTP mock（不依赖外网与真实 AI 密钥），覆盖 health、六部/信息源/偏好卡 CRUD、奏折创建到准奏/驳回/打回全流程、调度器写 fetch_log，并回归 p0-1/2/3 的时区、source_id、重拟断言。P0 完成后，server 的 verify = build + smoke。

## 验收标准

- [ ] `server/package.json` 新增 `"smoke": "tsx src/smoke.ts"`；`npm run smoke` 退出码 0 表示全部断言通过，非 0 失败。
- [ ] smoke 使用 `SSS_DB_PATH` 指向临时目录 SQLite（在加载 db 模块前设置，建议用动态 import），结束后删除临时目录并关闭本地 server 监听；不触碰仓库 `data/` 目录。
- [ ] health：GET `/api/health` 200 且 `ok=true`。
- [ ] CRUD：创建自定义六部 → 该部下创建信息源（读回一致）→ 更新信息源 → 读取/更新偏好卡（可断言 dailyLimit 钳制到 1-10）→ 删除自定义六部；删除默认六部返回 400。
- [ ] 奏折全流程：创建 candidate → 准奏 → archived 且 approvedAt/archivedAt 非空；再次创建 → 驳回 → items 中不存在且 reject_logs 有对应记录；再次创建 → 打回（AI 未配置）→ pending、`redraft_count=1`、title/summary/qualityScore 不变、ai_reason 含原因与“未配置 AI”。
- [ ] 重拟 AI 路径：本地 mock OpenAI-compatible `/chat/completions` 返回固定 JSON，PUT `/api/settings/ai` 指向 mock 后打回 → title/summary/aiReason/qualityScore 等于 mock 返回值，`redraft_count` 再 +1。
- [ ] 调度器：本地 HTTP 页面作为 url 源，POST `/api/scheduler/run` 后 fetch_logs 有该源 ok 记录（date 等于本地今日），items 出现 candidate 且 `sourceId` 等于该源 id；再次运行因 URL 去重 newCandidates=0。
- [ ] 手动源：创建 kind=manual 源后运行调度器，fetch_logs 出现该源记录且 status='error'、error 含“手动源无需定时抓取”（按现状语义固定，不改业务行为）。
- [ ] 时区回归：直插 `...Z` 存量行并调用 p0-1 迁移函数后，断言已转本地无时区且 `date(created_at)` 等于本地日期；dashboard createdToday/archivedToday 按本地今日计数（断言用跑前/跑后增量，避免互扰）。
- [ ] 全部通过后执行 `cd server && npm run build` 仍通过；web 未被 P0 写集触碰，其 build+lint 作为回归命令保留。

## 范围

做：

- 新增 `server/src/smoke.ts`，修改 `server/package.json` 增加 smoke script。

不做：

- 不改任何业务源码（db.ts、scheduler.ts、ai.ts、api.ts 均由前三 P 写集负责）。
- 不改 web 代码与依赖。

## 风险

- `SSS_DB_PATH` 必须在 `db.ts` 模块求值前设置，否则会落到仓库 `data/`；实现用动态 import 规避。
- better-sqlite3 为原生模块，Windows 下需确保 npm install 已完成；smoke 不新增第三方依赖。
- 本地 mock 端口冲突：HTTP 服务用 `listen(0)` 动态端口，避免固定端口占用。
- 断言互相污染：计数类断言必须基于基线增量。
