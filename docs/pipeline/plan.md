# 三省六部 V3.0 P0 生产计划

一句话需求：以现有 server 实现为起点，补齐 P0 采集审批闭环的 4 个缺口（时区统计口径、source_id 关联、打回重拟再生、冒烟测试与 verify），使 V3.0 验收标准逐条可机器验证。

方案摘要：统一持久化时间口径为“本地无时区 ISO”并对存量 UTC 行做幂等迁移，从根上消除 `date('now','localtime')` 与 UTC ISO 混用；调度器为每个候选奏折携带实际来源 `source_id`（聚合型异常奏折除外）；打回重拟在 AI 已配置时用原正文与打回原因重新生成 title/summary/aiReason/qualityScore，未配置或调用失败时保留原稿并标注，`redraft_count` 一律 +1；server 新增 `npm run smoke`，用临时 SQLite 覆盖 health、六部/信息源/偏好卡 CRUD、奏折创建到准奏/驳回/打回全流程、调度器写 fetch_log，并回归前三 P 断言。P0 不写 web，web verify 保持 `npm run build` + `npm run lint`。

## 状态表

| P | 状态 | 执行 Agent | 审查结论 | 合并时间 |
|---|------|-----------|----------|----------|
| p0-1 | 已合并 | Arendt | PASS | 2026-08-01 |
| p0-2 | 已合并 | Ramanujan | PASS | 2026-08-01 |
| p0-3 | 待执行 | - | - | - |
| p0-4 | 待执行 | - | - | - |

## 流水线约定

- 每个 P 由执行 Agent 在独立工作区实现，审查 Agent 独立重跑 verify；PASS 后由 main 合并并打 commit：`P0-1: 统一统计时区口径为本地时间`、`P0-2: 调度器候选奏折关联 source_id`、`P0-3: 打回重拟再生`、`P0-4: 增加冒烟测试与 verify`。
- 本规划产物先落在 `work/pipeline-planner/docs/pipeline/`；main 开始执行前将 plan.md 与各 spec 落到目标仓库 `docs/pipeline/`，作为流水线文档最终位置。
- 写集按文件隔离：p0-1 只写 `server/src/db.ts`，p0-2 只写 `server/src/scheduler.ts`，p0-3 只写 `server/src/ai.ts` 与 `server/src/api.ts`，p0-4 只写 `server/package.json` 与新增 `server/src/smoke.ts`；任何 P 不得越界。
- server verify：`npm run build` + `npm run smoke`；web 本轮无写集，验证命令保持 `npm run build` + `npm run lint`。

## P0~Pn

### p0-1 时区统计修复

- 目标：统一持久化时间戳与按日统计口径为本地时间，存量 UTC 数据迁移，dashboard 与调度器统计不再跨时区错位。
- dependsOn：无
- 写集：`server/src/db.ts`
- 验收标准：见 `p0-1/spec.md`；核心为 `nowIso()` 返回本地无时区 ISO、幂等迁移、`npm run build` 通过。
- 验证命令：`cd server && npm run build`；p0-4 落地后 `cd server && npm run smoke`

### p0-2 source_id 关联

- 目标：调度器候选奏折关联实际信息源，并顺带收敛 `scheduler.ts` 内 `daysAgoIso` 的时间口径。
- dependsOn：p0-1
- 写集：`server/src/scheduler.ts`
- 验收标准：见 `p0-2/spec.md`；核心为候选奏折 `sourceId` 等于实际源 id、聚合异常奏折保持 NULL、`npm run build` 通过。
- 验证命令：`cd server && npm run build`；p0-4 落地后 `cd server && npm run smoke`

### p0-3 打回重拟再生

- 目标：打回重拟真正再生内容；AI 已配置时重拟，未配置/失败时保留原稿并标注；同时收敛 `api.ts` importData 的时间口径。
- dependsOn：p0-1
- 写集：`server/src/ai.ts`、`server/src/api.ts`
- 验收标准：见 `p0-3/spec.md`；核心为两种 AI 状态下的 redraft 行为与 `redraft_count` 语义、`npm run build` 通过。
- 验证命令：`cd server && npm run build`；p0-4 落地后 `cd server && npm run smoke`

### p0-4 冒烟测试与 verify

- 目标：server 增加可重复的 `npm run smoke`，用临时 SQLite 覆盖 P0 验收并回归前三 P。
- dependsOn：p0-1、p0-2、p0-3
- 写集：`server/package.json`、`server/src/smoke.ts`（新增）
- 验收标准：见 `p0-4/spec.md`；核心为 health、CRUD、奏折全流程、调度器写 fetch_log、时区/source_id/重拟断言全部通过且退出码 0。
- 验证命令：`cd server && npm run build && npm run smoke`；web 保持 `cd archive-assistant-web && npm run build && npm run lint`

## V3.0 验收映射（本轮 P0）

- “每部按配置时间完成抓取；失败源生成异常奏折” → p0-4 调度器断言（本地 URL 源 ok + fetch_log；手动源 error fetch_log 按现状固定）。
- “门下省可准奏/驳回；准奏后进入对应部归档” → p0-4 奏折全流程断言。
- “统计与 source 归属正确” → p0-1/p0-2 断言（dashboard 本地今日计数、候选 `sourceId` 关联）。
- 阅读/批注/复习/问答属 P1/P2，不在本轮。
