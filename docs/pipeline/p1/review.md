# P1 审查报告（门下）

- 审查日期：2026-08-03
- 审查对象：P1 阅读与复习（server + web）
- 审查 Agent：P1 审查 Agent 第 2 轮；第 1 轮未落盘 review.md，按产物门判失败，本报告为重新审查结果。

## 一、审查范围与 diff 边界

- 主仓库 `C:\Users\ruihan\Documents\三省六部` 工作区 diff：`server/src/types.ts`、`server/src/db.ts`、`server/src/api.ts`、`server/src/smoke.ts`，均在 P1 写集内；另有未跟踪的 `plan.md`（main 产物）与 `archive-assistant-web` gitlink 变更。
- web 子仓库为独立 git 仓库，经 `git -c safe.directory=...` 读取。P1 写集 8 个文件均在本轮（2026-08-03 10:47-10:52 的 LastWriteTime）修改或新增：
  - `src/lib/api.ts`（新增）
  - `src/store/ui-store.ts`
  - `src/App.tsx`
  - `src/ui/screens/ReviewPane.tsx`（新增）
  - `src/ui/screens/HomePane.tsx`
  - `src/ui/screens/DashboardPane.tsx`（新增）
  - `src/ui/memorial/MemorialReader.tsx`
  - `.oxlintrc.json`
- 写集外核对：web 工作树另有 `package.json`、`package-lock.json`、`public/sw.js`、`vite.config.ts`、`src/lib/db.ts`、`src/lib/ai/classifier.ts`、`src/store/ai-store.ts`、`src/store/item-store.ts`、`src/store/topic-store.ts`、`src/ui/layout/V3Pane.tsx`、`src/ui/screens/ApprovalPane.tsx`、`src/ui/screens/SourcesPane.tsx`、`src/ui/screens/PreferencesPane.tsx` 等未提交改动。这些文件 LastWriteTime 为 2026-08-01 10:55-11:02，早于 P0/P1 流水线，判定为既存工作区改动，不属于本轮 P1 写集越界。P1 的 App.tsx 等文件引用的 V3Pane/ApprovalPane 等即这些既存文件。

## 二、验收标准逐条核对

1. server `build`/`smoke` 通过，smoke 覆盖批注 CRUD、标记已读、SM-2 档位、到期队列、学习统计、导出导入含复习与批注：达成。独立重跑均 exit 0，smoke 输出新增 `PASS annotations/read/SM-2/stats/export-import`（`server/src/smoke.ts:293-343`）。
2. web `build`/`lint` 通过：达成。build exit 0（3 条字体 runtime-resolve 警告，为既有资源问题）；lint exit 0（17 条 warning，其中 `src/App.tsx:52` 为本轮新引入 exhaustive-deps，其余为既存）。
3. 标记已读后 `item.status=read` 且 `read_at` 非空，reviews 自动创建（stage=0、interval=1、次日到期）：达成。`server/src/api.ts:567-587`；`server/src/db.ts:335` `ensureReviewOnRead` 使用 `interval_days=REVIEW_STAGES[0]=1`、`due_at=次日`；`server/src/smoke.ts:295-318` 断言。
4. forgot/hard/good/easy 按 1->3->7->15->30 档推进，good/easy 达顶端档后 mastered 并退出到期队列：达成。`server/src/db.ts:319` `REVIEW_STAGES=[1,3,7,15,30]`，`server/src/db.ts:405` `applyReviewFeedback` 实现 forgot->0、hard->stage-1、good->stage+1、easy->stage+2、顶端档 good/easy 置 mastered 并同步 `items.status`；`server/src/smoke.ts:319-329` 覆盖代表路径。
5. `/api/reviews/due` 返回 `status=reviewing` 且 `date(due_at)<=今日` 的条目，按到期时间排序：达成。`server/src/db.ts:356` SQL 为 `status='reviewing' AND date(r.due_at)<=? ORDER BY r.due_at ASC`；smoke 断言到期入列、mastered 出列。
6. `/api/stats/learning` 返回 dueToday、completedToday、completionRate（无任务时 null）、dueByMinistry、weeklyCount：达成。`server/src/db.ts:450` `learningStats` 返回全部字段，`total=0` 时 completionRate 为 null；`server/src/smoke.ts:330-333` 断言。
7. 批注支持新增/删除，按时间倒序返回：达成。`server/src/api.ts:526-565`；`server/src/db.ts:429` `listAnnotations` 使用 `ORDER BY created_at DESC, id DESC`；smoke 断言 2 条新增、删除 1 条后剩 1 条且顺序正确。
8. `/api/export/json` 含 reviews/annotations，导入后保留：达成。`server/src/api.ts:737-753` 导出新增 reviews/annotations；`importData` 删除并回填 reviews/annotations，仅保留与导入 items 关联的记录；`server/src/smoke.ts:335-342` 断言导入后批注保留、mastered 仍不在到期队列。

## 三、独立 verify 命令与输出摘要

以下命令于 2026-08-03 由审查 Agent 独立重跑，不采纳 main 自述。

1. server（`C:\Users\ruihan\Documents\三省六部\server`）：
   - `npm.cmd run build`：exit 0，`tsc -p tsconfig.json` 无输出。
   - `npm.cmd run smoke`：exit 0，输出 PASS health；PASS ministries/sources/preferences CRUD；PASS item approve/reject/redraft(no-ai)；PASS redraft with AI mock；PASS scheduler fetch_log/source_id/dedupe/manual；PASS timezone migration/dashboard；PASS annotations/read/SM-2/stats/export-import；SMOKE PASS。
2. web（`C:\Users\ruihan\Documents\三省六部\archive-assistant-web`）：
   - `npm.cmd run build`：exit 0，`tsc -b && vite build`，2356 modules transformed，构建成功；3 条 `/fonts/*.ttf` runtime-resolve 警告（既有）。
   - `npm.cmd run lint`：exit 0，oxlint 输出 17 条 warning；`src/App.tsx:52:18` exhaustive-deps 为本轮新增，其余为既存 irregular-whitespace、only-export-components、exhaustive-deps。

## 四、结论

PASS

## 五、残留风险

1. web 工作树存在 P1 写集外的既存未提交改动（2026-08-01），后续统一提交时需人工拆分，避免非 P1 改动混入 P1 交付。
2. `src/App.tsx:52` 新引入 exhaustive-deps warning（useEffect 缺 initTopics/initItems/initAi 依赖），lint 仍通过；建议补依赖数组或显式声明并注释。
3. smoke 对 SM-2 只覆盖代表路径（0 档 good 进 3 天档、forgot 回 1 天档、顶端档 good 掌握），未逐档断言 hard/easy 与 7/15 天档；实现与 plan 规则一致，属测试覆盖留白。
4. `importData` 在导入不含 reviews/annotations 的旧格式导出时会清空现有复习与批注，与全量替换语义一致，但导入前应提示备份。
5. 主仓库与 web 子仓库均未提交，web gitlink 仍指向基线 `8307b80`；提交主仓库前需先提交 web 子仓库并更新 gitlink。
