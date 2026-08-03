# P1-Fix-Web 独立审查回执（门下）

- 日期：2026-08-03
- 审查人：门下（独立审查 Agent）
- 结论：**PASS**

## 审查范围

- `server/src/fetcher.ts`（新增导出 `fetchUrlArticle`）
- `server/src/api.ts`（新增 `POST /api/fetch-web`）
- `server/src/smoke.ts`（新增 `/api/fetch-web` 断言）
- `archive-assistant-web/src/lib/api.ts`（新增 `fetchWeb` 方法）
- `archive-assistant-web/src/lib/fetch-web.ts`（新增：本地优先、代理兜底）
- `archive-assistant-web/src/ui/screens/AddItemDialog.tsx`（import 切换）
- `plan.md`（状态记录）

按边界要求未审查 `archive-assistant-web/src/lib/ai/classifier.ts`，也未修改任何代码或 `plan.md`。

## 验收标准逐条核对

1. `POST /api/fetch-web` 对微信样式 HTML 返回 og:title、摘要、非空全文：PASS。
   - `server/src/api.ts:284-306` 调用 `fetchUrlArticle` 并原样返回 `title/summary/fullText/sourceUrl`。
   - `server/src/smoke.ts:269-274` 独立断言 200、`title === '微信测试标题'`、`fullText` 含 `微信正文内容`。
   - 同一 `article.summary` 已由 `server/src/smoke.ts:264-267` 在 fetcher 层断言为正文前缀回退。
2. `/api/fetch-web` 对非 http(s) URL 返回 400：PASS。
   - `server/src/api.ts:287-290` 使用 `/^https?:\/\//i` 校验并返回 400。
   - `server/src/smoke.ts:275-276` 对 `not-a-url` 断言 `status === 400`。
3. Web `fetchWebPage` 优先本地、本地失败才回退代理：PASS。
   - `archive-assistant-web/src/lib/fetch-web.ts:13-28` 先调 `api.fetchWeb(url)`，仅 catch 或非 `ok` 才进入 `30-47` 行代理列表。
   - `archive-assistant-web/src/lib/api.ts:215-219` 将 `fetchWeb` 映射到 `/fetch-web`，`request` 统一拼 `/api` 前缀。
4. `AddItemDialog.tsx` 的“AI 智能归纳”使用新 `fetchWebPage`：PASS。
   - `AddItemDialog.tsx:8` 从 `../../lib/fetch-web` 导入，`AddItemDialog.tsx:181` 调用；不再依赖 `classifier.ts` 内旧代理实现。
   - 全仓库检索（排除 `classifier.ts`）确认仅新 `fetch-web.ts` 持有代理 URL，无其他入口直接使用代理抓取。
5. server build/smoke 与 web build/lint 四条命令独立重跑全部通过：PASS。

## 独立验证输出摘要

- server `npm.cmd run build`：exit 0，`tsc -p tsconfig.json` 无错误。
- server `npm.cmd run smoke`：exit 0，输出含 `PASS wechat-style url fetch`，末尾 `SMOKE PASS: health/CRUD/item-flow/redraft/scheduler/timezone 全部断言通过`。
- web `npm.cmd run build`：exit 0，`tsc -b && vite build` 成功，`✓ built in 445ms`；仅 3 条字体运行时解析提示，非错误。
- web `npm.cmd run lint`：exit 0，oxlint 仅 warnings（exhaustive-deps、no-irregular-whitespace 等），无 error。

## 非阻断观察（不构成 REWORK）

- smoke 对 `/api/fetch-web` 未直接断言 `summary` 非空，但端点透传同一 `article.summary` 且 fetcher 层已断言；后续可补一条增强覆盖。
- `AddItemDialog.tsx:181-188` 在 `fetchWebPage` 失败时静默继续并清空错误提示，本地与代理均失败时仍可能回退 URL 填充；该逻辑为既存行为，不在本 diff 内，建议后续单独处理。
- `AddItemDialog.tsx:184` 仅将全文前 800 字写入全文框，完整归档全文若为目标，可另立需求调整。

## 结论

本 diff 满足全部 5 项验收标准，无阻断问题，审查结论为 **PASS**。
