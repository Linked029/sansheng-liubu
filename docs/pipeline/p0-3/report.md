# P0-3 交接单：打回重拟再生

## 完成内容

- `ai.ts` 新增导出函数 `redraftArticle` 与 `RedraftResult`：
  - 入参包含原 title/summary/fullText/qualityScore、打回原因、ministry、preference、settings。
  - AI 已配置时，基于原正文、打回原因、六部与偏好重新生成 title/summary/aiReason/qualityScore，qualityScore 经 0-100 clamp。
  - AI 未配置时返回原 title/summary/qualityScore，aiReason 带“打回重拟”、原因与“AI 未配置”标注。
  - AI 已配置但调用失败（端点为空、非 2xx、解析失败或异常）时返回原稿，aiReason 带“打回重拟”、原因与“AI 调用失败”标注。
- `api.ts` 的 `POST /api/items/:id/redraft` 改为 async：
  - 仅 candidate/pending 可打回，其余状态返回 400；不存在返回 404，行为保持。
  - 无论 AI 是否配置，status 置为 pending、redraft_count +1，approved_at/archived_at 保持 NULL。
  - AI 未配置时 title/summary/qualityScore 不变，ai_reason 含原因与“未配置 AI”标注。
  - AI 已配置时更新为生成结果，title/summary/ai_reason/quality_score 写入生成值。
- `api.ts` 的 `importData`：createdAt 落库由 `toISOString()` 改为 `formatLocalIso`，archivedAt 缺省值同步使用本地无时区 ISO。
- 未改准奏/驳回行为，未改 `draftArticle` 现有逻辑，未改数据库 schema。

## 改动文件清单

| 文件 | baseline SHA256 | 当前 SHA256 |
| --- | --- | --- |
| `server/src/ai.ts` | `1BC2AC3D0A25413D5586A044E5C085D39067A86817E78A40AFB05AF4E70938AA` | `426A3B449039E851B64AAB063A8C6D2835393D3A27A94DFF503C5BF34A081986` |
| `server/src/api.ts` | `F4503F03DBC2D831FC35D7D73979E7A634524C167D0C9BBE8C8D45B2020A7DC0` | `7680C465E3962BF2910D6592BC85F129086C9BEAB7F8286DC468B6E5FDC2CB7C` |

## 复现验证

1. 构建（必须退出码 0）：

```powershell
cd C:\Users\ruihan\Documents\三省六部\work\p0-3-ws\server
npm.cmd run build
```

结果：`tsc -p tsconfig.json` 通过，退出码 0。

2. 行为自检（临时 SQLite + 本地 mock，已在本 P 执行通过）：

```powershell
node -e "const path=require('node:path');process.env.SSS_DB_PATH=path.join(process.env.TEMP,'p0-3-smoke.sqlite');const{createApp,importData}=require('./dist/api.js');..."
```

关键断言结果：

- AI 未配置：`status=pending`、`redraftCount=1`、title/summary/qualityScore 保持不变，`aiReason="打回重拟：标题太笼统（AI 未配置，保留原标题与摘要）"`。
- AI 已配置（本地 mock 返回固定 JSON，qualityScore=950）：title/summary/aiReason 更新，`qualityScore=100`（clamp），`status=pending`、`redraftCount=2`、`approvedAt/archivedAt=null`。
- 已归档条目打回：400；不存在条目打回：404。
- importData 后 `created_at/archived_at` 为 `YYYY-MM-DDTHH:mm:ss.SSS` 本地无时区格式，不再含 `Z`。

## 偏离记录

无。

## 遗留风险与给下一个 P 的提示

- 新建数据库的默认 `ai` 设置自带 `baseUrl=https://api.deepseek.com/v1` 与 `modelName=deepseek-chat`，`isAiConfigured` 只看 baseUrl/modelName，因此 smoke 测“AI 未配置”路径前需先 `PUT /api/settings/ai` 将两者置空，否则会走真实网络调用。
- AI 请求超时为 60s（`AbortSignal.timeout(60000)`），smoke 测“AI 已配置”路径必须使用本地 mock，避免等待网络超时。
- `importData` 对显式传入的 `archivedAt` 仍按原值落库（本次仅收敛默认回退路径），若 p0-4 需要严格统一，可断言导出再导入的 round-trip 均为本地无时区 ISO。
- `npm run build` 会在 `server/dist/` 生成编译产物，属于预期构建输出。
