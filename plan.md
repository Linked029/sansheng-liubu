---
# 三省六部 P0~P2 安全修复 + 桥接计划（四轮契约）

## 总览

| 轮次 | 标签 | 核心 | 文件集 | 预计改动量 |
|------|------|------|--------|-----------|
| P0 | 安全基底 | 认证中间件 + importData 预校验备份 + Gemini 密钥双层保护 + 完成率修正 | api.ts, ai.ts, db.ts, smoke.ts | ~80 行 |
| P1.1 | 数据完整 | rejectItem 软删除 + 探索去重补 reject_logs + 死代码清理 + 注释修正 | api.ts, exploration.ts, search.ts, db.ts, smoke.ts | ~60 行 |
| P1.2 | SM-2 桥接 | archiveExplorationItem → items 建行 → 标记已读 → SM-2 复习 | exploration.ts, api.ts, db.ts, smoke.ts | ~70 行 |
| P2 | 健壮性 | searchWeb 错误传播 + catchUpMissed 并发控制 + 格式碎片清理 | search.ts, scheduler.ts, exploration.ts, smoke.ts | ~40 行 |

## 共享上下文

- `SSS_TOKEN` 未设置时行为向后兼容（所有写操作不拦）
- `SSS_AI_KEY` 未设置时 fallback settings 表
- 所有旧 smoke 断言保持 PASS
- P1.1 不涉及前端改动（rejected status 仅 API 层面）

---

## P0：安全基底

### 状态：completed

### 契约

1. **认证中间件** — 新增 `requireWriteAuth` Express 中间件，所有 POST/PUT/DELETE 端点检查 `Authorization: Bearer <token>`，token 从 `SSS_TOKEN` 环境变量读取（多个 token 用逗号分隔）。`/api/health` 返回 `authEnabled: true/false`。

2. **importData 预校验 + 备份** — 事务前校验导入数据的 `ministries[].id` 非空、`items[].title` 非空、`items[].status` 在合法枚举内。校验失败返回 400 + 具体字段路径。事务内先将现有数据行数快照到临时表，COMMIT 后清理。

3. **Gemini 密钥双层保护** — `buildRequest()` 中 Gemini 引擎改用 `x-goog-api-key` header 传密钥。`readAppSettings()` 中 `apiKey` 优先取 `SSS_AI_KEY` 环境变量。

4. **完成率修正** — `learningStats()` 中 `total` 改为 `dueToday`，完成率 = `completedToday / dueToday`，`dueToday === 0` 时返回 `null`。

### 验收命令

```bash
cd server && npm run build && npm run smoke
```

### 验收标准
- `PASS auth: no SSS_TOKEN → writes allowed`
- `PASS auth: SSS_TOKEN set → writes rejected without token`
- `PASS auth: SSS_TOKEN set → writes allowed with correct token`
- `PASS import validate: missing title → 400`
- `PASS import validate: invalid status → 400`
- `PASS import validate: valid data → 201`
- `PASS gemini key: env SSS_AI_KEY overrides settings`
- `PASS completion rate: due=5 completed=3 → 60%`

---

## P1.1：数据完整（待启动）

### 契约

1. **rejectItem 软删除** — 不修改 schema，驳回时 SET `status='rejected'` + 记录 `reject_logs`。`listItems()` 默认过滤 `rejected`。新增 `POST /api/items/:id/restore` 恢复端点。
2. **探索去重补 reject_logs** — `isExplorationUrlDuplicate()` 补充 7 天内 reject_logs 检查。
3. **死代码清理** — 删除 `fulfillSearchDirection()`。
4. **注释修正** — `search.ts` 顶部 DuckDuckGo → Bing。

### 验收命令
```bash
cd server && npm run build && npm run smoke
```

---

## P1.2：SM-2 桥接（待启动）

### 契约

1. `archiveExplorationItem()` 在 `items` 表 INSERT 一行（`source_id=NULL`, status='archived'）。URL 去重：已存在则跳过。
2. 标记已读 → `ensureReviewOnRead()` → SM-2 档位正常推进。

### 验收命令
```bash
cd server && npm run build && npm run smoke
```

---

## P2：健壮性（待启动）

### 契约

1. `searchWeb` 错误传播 — 非 2xx throw Error。
2. `catchUpMissed` 并发控制 — `for...of` + `await`。
3. 格式修复 — exploration.ts 换行 + 删除死 segments 计算。

### 验收命令
```bash
cd server && npm run build && npm run smoke
```
