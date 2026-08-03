# Web 新增归档/缓存修复独立审查

审查角色：门下（审查 Agent）
审查日期：2026-08-03
审查范围：`archive-assistant-web/src/ui/screens/AddItemDialog.tsx`、`archive-assistant-web/public/sw.js`、`server/src/fetcher.ts`、`server/src/smoke.ts`、`plan.md`
禁止项：未审查 `classifier.ts` 等写集外文件；未修改任何代码或 plan.md。

## 结论

PASS

## 逐条验收对照

1. `AddItemDialog.tsx`：`handleAiClassify` 在存在来源 URL 时先 `fetchWebPage`；成功则直接以抓取结果填充标题（28 字）、摘要（96 字）、全文（10000 字）并 return，失败则 `setAiError(result.error)` 后 return，两分支均不再调用 `smartSummarize`。无来源 URL 时才走 `smartSummarize({ rawText: text })`。符合验收标准 1。
2. `public/sw.js`：`CACHE_NAME` 升级为 `archive-assistant-v2`；`fetch` 中对 `mode === "navigate"` 走 network-first（在线返回最新响应并回写缓存，失败回退 `caches.match(e.request)` 再回退 `/`）；非导航 GET 资源维持 stale-while-revalidate；`install` 预缓存 `/` 与 `/index.html`，`activate` 清理旧缓存。新增 `/api/` 请求不拦截，避免 API 被缓存，与修复目标一致。符合验收标准 2。
3. `fetcher.ts`：正文选择器列表新增 `#cnblogs_post_body`、`.postBody`，位于通用容器选择器之前；`nav` 在正文提取前已被移除，博客园样式页正文不会包含导航文本。符合验收标准 3。
4. `server/src/smoke.ts`：新增博客园样式 fixture（`<title>` + `meta description` + `#cnblogs_post_body` + `nav` 导航哨兵），并通过 `/api/fetch-web` 断言标题来自 meta、正文来自 `#cnblogs_post_body`、全文不含 `NAV_SENTINEL`，输出 `PASS cnblogs-style url fetch`。符合验收标准 4。
5. 独立验证命令全部通过（见下）。web lint 仅有既有 warning，无 error，按约定 warning 不计失败。符合验收标准 5。

## 验证命令输出摘要（独立重跑，2026-08-03）

- server `npm.cmd run build`：exit 0，`tsc -p tsconfig.json` 无输出错误。
- server `npm.cmd run smoke`：exit 0，含 `PASS cnblogs-style url fetch`，末尾 `SMOKE PASS: health/CRUD/item-flow/redraft/scheduler/timezone 全部断言通过`。
- web `npm.cmd run build`：exit 0，`tsc -b && vite build` 完成，产物写入 `dist/`；仅 3 条字体未解析的既有提示（不影响构建结果）。
- web `npm.cmd run lint`：exit 0，`oxlint` 仅报告既有 warning（如 `AddItemDialog.tsx:54` exhaustive-deps 为修改前既有行，本 diff 未引入新告警）。

## 备注

- diff 边界内改动与声明一致，`git diff --check` 无空白错误。
- `AddItemDialog.tsx` 中文文本为有效 UTF-8，无乱码落盘。
- 浏览器端 `fetchWebPage` 本地代理抓取与 SW 缓存行为属于集成风险，建议合入后由 main 做一次浏览器实测（微信/博客园各一链接，并验证 v2 生效）。
