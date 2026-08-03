# 三省六部 V3.0 P1 交付报告

日期：2026-08-03
范围：阅读批注（批红）、已读状态、SM-2 复习队列与学习统计，server + web 一起交付，并修复 web lint 配置。

## 交付结果

| 项 | 内容 | 状态 | 审查 | 合并时间 |
|---|------|------|------|----------|
| P1 | 批注新增/删除、标记已读自动建档、SM-2 1/3/7/15/30 复习、学习统计、导出/导入含复习与批注、web 复习页与 lint 修复 | 已合并 | PASS | 2026-08-03 |

审查报告：`docs/pipeline/p1/review.md`

## 验证汇总

- `cd server && npm.cmd run build`：通过。
- `cd server && npm.cmd run smoke`：通过，7 组断言 PASS，新增 `annotations/read/SM-2/stats/export-import`。
- `cd archive-assistant-web && npm.cmd run build`：通过（仅既有字体 runtime-resolve warning）。
- `cd archive-assistant-web && npm.cmd run lint`：通过（17 条 warning，其中 `src/App.tsx:52` exhaustive-deps 为本轮新增）。

## 未决风险与遗留

- web 子仓库存在 2026-08-01 既存未提交改动（写集外），已原样保留，未混入 P1 提交；后续可单独提交。
- `src/App.tsx:52` 的 exhaustive-deps warning 建议补依赖数组或显式声明。
- smoke 对 SM-2 覆盖代表路径，未逐档断言 hard/easy 与 7/15 天档。
- `importData` 按全量替换语义处理旧格式导出，导入前应提示备份。

## 交付状态

- P1 审查 PASS 后已更新状态表、提交本地 git（含 web 子仓库 P1 写集提交与 gitlink 更新）。
- 本地服务已启动，访问 `http://localhost:4318`。
