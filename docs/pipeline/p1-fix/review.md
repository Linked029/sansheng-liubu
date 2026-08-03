# P1 微信 URL 抓取修复审查（门下）

- 审查对象：`server/src/fetcher.ts`、`server/src/smoke.ts`、`plan.md`（仅状态记录）
- 审查时间：2026-08-03
- 审查方式：独立 `git diff` 审阅 + 独立重跑 build/smoke；未修改任何代码或 `plan.md`
- 结论：**REWORK**

## 独立验证输出（server 目录）

命令 1：`npm.cmd run build`

- 退出码 0；`tsc -p tsconfig.json` 无错误输出。

命令 2：`npm.cmd run smoke`

- 退出码 0，输出：
  - PASS health
  - PASS ministries/sources/preferences CRUD
  - PASS item approve/reject/redraft(no-ai)
  - PASS redraft with AI mock
  - PASS wechat-style url fetch
  - PASS scheduler fetch_log/source_id/dedupe/manual
  - PASS timezone migration/dashboard
  - PASS annotations/read/SM-2/stats/export-import
  - SMOKE PASS

参考（非独立验证）：`work/p1-fix-verify.log` 中真实 URL 抓取返回 `articleCount: 1`、标题为真实文章标题、`fullTextLength: 2368`。本次审查按验收要求只独立复跑 build/smoke，未独立复跑外网 URL。

## 验收标准逐条对照

1. 验收 1（`<title>` 为空时回退 `og:title`）：通过。`fetcher.ts:75-79` 回退链包含 `meta[property="og:title"]`，`smoke.ts:256` 断言标题为 `微信测试标题`；旧逻辑在此 fixture 上会得到 URL，标题断言可守住该回归。
2. 验收 2（meta 描述为空时摘要回退正文前缀）：实现通过（`fetcher.ts:80-84`、`fetcher.ts:111`），但 fixture 断言偏弱，详见问题 1。
3. 验收 3（`#js_content` 纳入正文提取）：实现已加入（`fetcher.ts:87-88`），但 smoke fixture 未真正走到该分支，详见问题 1。
4. 验收 4（新增 `PASS wechat-style url fetch`）：命令输出存在，但断言覆盖不足，见问题 1。
5. 验收 5（build/smoke 通过）：通过。

## 问题

1. [P1] `smoke.ts` 的微信 fixture 未真正验证 `#js_content` 提取，`PASS wechat-style url fetch` 无法守住验收 3，也会漏掉摘要/全文相关回归。
   - 证据 1：`smoke.ts:215-220` fixture 正文经 cheerio 提取后仅 154 字，低于 `fetcher.ts:101` 的 200 字阈值；独立脚本验证新代码实际选择 `body` 兜底（`container.is('#js_content') === false`、`container.is('body') === true`），`#js_content` 分支从未执行。
   - 证据 2：`smoke.ts:257-258` 的全文/摘要断言只检查正文文本“出现”，而该文本同时存在于 `body`；用旧逻辑（无 `#js_content`、无 `og:*` 回退）对同一 fixture 模拟，摘要与全文断言依然全部通过，仅标题断言失败。即删除 `#js_content` 选择器后 smoke 仍会全绿。
   - 证据 3：`smoke.ts:217` 与 `plan.md` 声称 fixture“确保超过两百字阈值”，与 fixture 实际 154 字不符。
   - 修改建议：
     a. 将 `#js_content` 内文本补足到 200 字以上，确保触发该选择器分支；
     b. 在 `#js_content` 之外、`body` 之内加入哨兵文本（如“不应进入正文”），并断言 `fullText` 不包含哨兵，或断言 `fullText` 与 `#js_content` 文本完全一致，使 body 兜底时断言失败；
     c. 同步修正 `smoke.ts:217` 注释；如需守摘要回退，可再补一条 `og:description` 非空时摘要取 `og:description` 的断言。
   - 完成后重跑 `npm.cmd run build`、`npm.cmd run smoke`，确认 `PASS wechat-style url fetch` 仍通过且新断言可区分 body 兜底。

## 下一步建议

- 由 main 按问题 1 修改 `server/src/smoke.ts` 后重新提交；`plan.md` 状态记录由 main 自行维护。
- 门下复验时除 build/smoke 外，复核 fixture 文本长度与哨兵断言，确认 `#js_content` 分支确实被选中。

## 第 2 轮复验

- 审查时间：2026-08-03
- 审查方式：独立 `git diff` 审阅 + 从 `smoke.ts` 原样解析 fixture 并用 cheerio 独立统计 + 模拟 fetcher 选择器顺序 + 负向对照 + 独立重跑 build/smoke；未修改任何代码或 `plan.md`。
- 结论：**PASS**

### 独立验证输出摘要

- fixture 解析：`#js_content` 文本（按 fetcher 同规则归一化）长度 311 字，明显超过 `fetcher.ts:102` 的 200 字阈值；`body` 文本 335 字。
- 哨兵位置：`SENTINEL_OUTSIDE_CONTENT` 在 `#js_content` 之外（`#js_content` 不含、`body` 含），对应 `smoke.ts:227`。
- 选择器模拟：按 `fetcher.ts:87-98` 的选择器顺序独立跑一遍，首个命中项为 `#js_content`，非 body 兜底。
- 负向对照：把 `#js_content` 文本压短到 11 字后，无 200 字以上容器，选择器回退 `body`，全文将包含哨兵；证明 `smoke.ts:266` 的哨兵断言能区分 `#js_content` 与 body 兜底。
- 命令 1：`npm.cmd run build` 退出码 0，`tsc -p tsconfig.json` 无错误输出。
- 命令 2：`npm.cmd run smoke` 退出码 0，输出包含 `PASS wechat-style url fetch` 与 `SMOKE PASS: health/CRUD/item-flow/redraft/scheduler/timezone 全部断言通过`。
- 范围说明：本次按验收要求只独立复跑 build/smoke 与本地 fixture，未独立复跑外网微信 URL。

### 验收标准逐条对照

1. 验收 1（`<title>` 为空回退 `og:title`，不等于 URL）：PASS。`smoke.ts:211` `<title>` 为空、`smoke.ts:212` `og:title=微信测试标题`，`smoke.ts:264` 断言标题相等；`fetcher.ts:74-79` 回退链正确。
2. 验收 2（meta 描述为空时摘要回退正文前缀，不等于 URL）：PASS。fixture 无 `meta[name=description]` 且 `og:description` 为空（`smoke.ts:213`），`fetcher.ts:80-84,111` 使 `summary` 取 `fullText` 前缀，`smoke.ts:267` 断言 `summary` 以正文开头。
3. 验收 3（`#js_content` 被实际选中提取正文，不是 body 兜底）：PASS。fixture 正文 311 字，独立选择器模拟命中 `#js_content`，非 body 兜底。
4. 验收 4（哨兵断言有效）：PASS。`smoke.ts:266` 断言 `fullText` 不含哨兵；负向对照证明 `#js_content` 不足 200 字时回退 body 会引入哨兵，断言能守住分支。
5. 验收 5（build/smoke 通过）：PASS。两条命令退出码均为 0。

## 下一步建议

- 本轮 PASS，可进入收口；建议 main 更新 `plan.md` 状态为第 2 轮 PASS 并继续后续交付。
- 后续如有真实微信 URL 回归需要，可在联网环境补跑 `work/p1-fix-verify.log` 对应的外网 URL 验证。
