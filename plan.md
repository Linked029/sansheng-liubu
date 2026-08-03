# 三省六部 P1 生产计划（产物优先版）

一句话需求：补齐阅读批注（批红）、已读状态、SM-2 复习队列与学习统计，server + web 一起交付，并修复 web lint。

方案摘要：服务端在现有 SQLite 表（reviews/annotations）上补业务逻辑与 API：标记已读自动建复习、简化 SM-2 档位 1→3→7→15→30、到期队列、学习统计、批注 CRUD、导出/导入含复习与批注；Web 新增复习页、工作台复习统计、阅读器批注与标记已读；修复 .oxlintrc.json 使 lint 可跑。默认模式：main 直实现 + 1 个独立审查 Agent 全量 verify。

## 状态表

| P | 状态 | 执行 Agent | 审查结论 | 合并时间 |
|---|------|-----------|----------|----------|
| P1 | 已合并 | main（直实现） | PASS | 2026-08-03 |

## P1：阅读与复习

- 目标：P1 验收全部可机器验证。
- 写集：server/src/types.ts、server/src/db.ts、server/src/api.ts、server/src/smoke.ts；archive-assistant-web/src/lib/api.ts、store/ui-store.ts、App.tsx、ui/screens/ReviewPane.tsx（新增）、ui/screens/HomePane.tsx、ui/screens/DashboardPane.tsx、ui/memorial/MemorialReader.tsx、.oxlintrc.json。
- 验收标准：
  1. server `npm.cmd run build` 通过，`npm.cmd run smoke` 通过，smoke 新增批注 CRUD、标记已读、SM-2 档位、到期队列、学习统计、导出/导入含复习与批注断言。
  2. web `npm.cmd run build` 通过，`npm.cmd run lint` 通过。
  3. 标记已读后 item.status=read 且 read_at 非空，reviews 自动创建（stage=0、interval=1、次日到期）。
  4. 复习反馈 forgot/hard/good/easy 按 1→3→7→15→30 档位推进；30 天档 good/easy 后 status=mastered 并退出到期队列。
  5. /api/reviews/due 返回 status=reviewing 且 date(due_at)<=今日 的条目，按到期时间排序。
  6. /api/stats/learning 返回 dueToday、completedToday、completionRate（无任务为 null）、dueByMinistry、weeklyCount。
  7. 批注支持新增/删除，按时间倒序返回。
  8. /api/export/json 含 reviews/annotations，导入后保留。
- 验证命令：
  - `cd server && npm.cmd run build && npm.cmd run smoke`
  - `cd archive-assistant-web && npm.cmd run build && npm.cmd run lint`
- 验证摘要（2026-08-03）：
  - server `npm.cmd run build` 通过；`npm.cmd run smoke` 通过，新增断言 PASS annotations/read/SM-2/stats/export-import。
  - web `npm.cmd run build` 通过；`npm.cmd run lint` 通过（仅既有 warning）。
  - GitHub 技能同步：codex-skills `1bf5a41..cfe44dd` 已推送。
  - 第 2 轮审查：上一轮审查 Agent 未落盘 `docs/pipeline/p1/review.md`，按产物门判失败；已重开审查 Agent，结论以落盘 review.md 为准。
  - 第 2 轮审查结论：PASS，落盘 `docs/pipeline/p1/review.md`；server build/smoke、web build/lint 独立重跑均通过。
  - 2026-08-03：用户确认继续把 multi-agent-pipeline 技能同步到 GitHub（Linked029/codex-skills main 分支）。
  - 2026-08-03：技能同步执行中；本地副本与克隆均为 `cfe44dd`，`git push origin main` 确认远程同步状态。
- 偏离记录：（暂无）

## P1 修复记录（2026-08-03）

- 问题：吏部新建微信归档来源 `https://mp.weixin.qq.com/s/uIVsbu0LBkWNRYsduJU7Kg`，智能抓取后标题为 URL 截断、摘要为完整 URL、全文为空。
- 定位：`server/src/fetcher.ts` 的 `fetchUrl` 仅取 `<title>`、`meta[name=description]` 与通用正文容器，未适配微信 `og:*` 元信息与 `#js_content`，且可能命中反爬页。
- 状态：第 2 轮审查 PASS（第 1 轮 REWORK 已闭环）；真实 URL 复验通过：标题取 `og:title`、摘要回退正文前缀、全文非空；已合入，审查落盘 `docs/pipeline/p1-fix/review.md`。
- 2026-08-03 二次复验：第二个微信链接 `https://mp.weixin.qq.com/s/yXvWLsj044f9Dv11x76TIw` 实测通过，标题“Codex 装完别急写代码，先装这 5 个 Skill”、摘要正常、全文 4299 字；输出见 `work/p1-fix-verify-2.log`。
- 问题 2（Web 新增归档）：对话框“AI 智能归纳”仍走浏览器 CORS 代理抓网页，微信页抓取失败后回退 URL 填充标题/摘要、全文为空；改为优先调本地 `/api/fetch-web`（复用已修复的 `fetchUrl`），代理仅作兜底。
- 问题 2 状态：第 1 轮独立审查 PASS，落盘 `docs/pipeline/p1-fix-web/review.md`；已合入提交并重启服务，实测 `/api/fetch-web` 对该微信链接返回正确标题/摘要/全文（全文 4299 字）。
- 问题 3（Web 智能归纳覆盖抓取结果）：浏览器实测 `https://mp.weixin.qq.com/s/uIVsbu0LBkWNRYsduJU7Kg`，本地抓取成功、全文已填充，但 `handleAiClassify` 随后用 `rawText=URL` 跑 classify 并把结果覆盖标题/摘要（未配置 AI 时就是 URL 截断）。修复：有来源 URL 时抓取成功直接用抓取结果填充标题/摘要/全文并结束，抓取失败直接报错返回，不再用 URL 跑归纳。
- 问题 3 状态：第 1 轮独立审查 PASS；浏览器实测标题/摘要/全文正确；已合入，审查落盘 `docs/pipeline/p1-fix-web-ui/review.md`。
- 问题 4（Service Worker 缓存旧包）：浏览器实测仍跑旧代码，根因是 `public/sw.js` 缓存旧 `index.html` 与旧 bundle；修复为 `archive-assistant-v2` + 导航请求 network-first，清缓存后新代码生效。
- 问题 4 状态：第 1 轮独立审查 PASS（`archive-assistant-v2` + 导航 network-first）；已合入。
- 问题 5（博客园正文容器）：`fetchUrl` 增加 `#cnblogs_post_body`/`.postBody` 选择器，smoke 新增 `PASS cnblogs-style url fetch`；实测博客园链接标题/摘要正确、全文不再含导航文本；已随问题 3/4 一并审查 PASS 合入。
