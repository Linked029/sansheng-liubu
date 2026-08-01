# 三省六部 V3.0 P0 交付报告

日期：2026-08-01
范围：P0 采集审批闭环 4 个缺口（时区口径、source_id 关联、打回重拟再生、冒烟测试与 verify）。

## 交付结果

| P | 内容 | 状态 | 审查 | 合并 commit |
|---|------|------|------|-------------|
| p0-1 | 统一统计时区口径为本地时间 | 已合并 | PASS | `3eefeb1` |
| p0-2 | 调度器候选奏折关联 source_id | 已合并 | PASS | `52ecde3` |
| p0-3 | 打回重拟再生 | 已合并 | PASS | `2d89f0f` |
| p0-4 | 增加冒烟测试与 verify | 已合并 | PASS | `80dc5c4` |

## 验证汇总

- `cd server && npm.cmd run build`：通过。
- `cd server && npm.cmd run smoke`：通过，6 组断言覆盖 health、CRUD、奏折全流程、AI mock 重拟、调度器 fetch_log/source_id/去重、时区迁移与 dashboard 计数。
- `cd archive-assistant-web && npm.cmd run build`：通过（P0 未改 web）。
- `cd archive-assistant-web && npm.cmd run lint`：失败，原因为既有 `.oxlintrc.json` 无法被 oxlint 解析，与 P0 无关。

## 未决风险

- `api.ts` 的 health/export 响应级时间仍为 UTC ISO（非持久化字段，spec 允许）。
- `importData` 显式传入的 `archivedAt` 按原值落库，未做时区归一（下次打开数据库时由 p0-1 迁移兜底）。
- `sources.last_fetched_at` 不在 p0-1 迁移列中。
- web lint 配置需单独修复。

## 下一步建议

- P1：奏折阅读批注、已读状态、SM-2 复习队列与学习统计。
- 独立小任务：修复 web `.oxlintrc.json` 解析问题。
- 若需要跨时区移动语义，另开 P 定义时间策略。
