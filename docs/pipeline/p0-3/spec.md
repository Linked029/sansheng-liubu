# P0-3 任务卡：打回重拟再生

- 依赖：p0-1
- 写集：`server/src/ai.ts`、`server/src/api.ts`（redraft 路由与 importData 时间口径，均在本 P 写集内）
- 验证命令：`cd server && npm run build`；p0-4 落地后 `cd server && npm run smoke`

## 目标

打回重拟目前只改状态与 ai_reason，不真正重拟。改为：AI 已配置时基于原 `full_text` + 打回原因 + 六部/偏好重新生成 title/summary/aiReason/qualityScore；未配置或调用失败时保留原 title/summary/qualityScore 并标注；`redraft_count` 一律 +1。同时把 `api.ts` importData 的 UTC `toISOString()` 收敛为 p0-1 的本地无时区口径。

## 验收标准

- [ ] `ai.ts` 新增导出函数（如 `redraftArticle`）：入参含原 title/summary/fullText、打回原因、ministry、preference、settings；AI 已配置时返回新 title/summary/aiReason/qualityScore（qualityScore 0-100 clamp）；未配置或调用失败时返回原 title/summary/qualityScore，且 aiReason 带“打回重拟”与原因标注。
- [ ] `POST /api/items/:id/redraft` 改为 async：AI 未配置时 status → pending、`redraft_count` +1，title/summary/qualityScore 不变，ai_reason 含原因与“未配置 AI”标注。
- [ ] AI 已配置（smoke 用本地 mock 返回固定 JSON）时，title/summary/aiReason/qualityScore 更新为生成结果，status → pending、`redraft_count` +1、approved_at/archived_at 保持 NULL。
- [ ] 仅 candidate/pending 可打回，其余状态返回 400；不存在返回 404（行为保持）。
- [ ] `importData` 中 createdAt/archivedAt 落库不再用 `toISOString()`，改用 p0-1 统一 helper 输出本地无时区 ISO。
- [ ] `cd server && npm run build` 通过。
- [ ] p0-4 落地后 `npm run smoke` 覆盖未配置与已配置（mock）两条路径。

## 范围

做：

- 新增重拟生成函数与 redraft API 的再生逻辑。
- importData 时间戳改用本地格式化 helper。

不做：

- 不改准奏/驳回行为。
- 不改 `draftArticle` 现有拟折逻辑。
- 不改数据库 schema（无需新列）。

## 风险

- AI 调用失败需回退，不能丢原稿；回退路径与“未配置”路径行为一致。
- `redraft_count` 只能 +1，不能在失败路径重复自增。
