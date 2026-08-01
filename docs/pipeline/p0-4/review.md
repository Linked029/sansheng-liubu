# P0-4 独立审查结论（门下）

状态：PASS

## 验证命令输出摘要

- `npm.cmd run build`（cwd=`server`）退出码 0：`tsc -p tsconfig.json` 无错误。
- `npm.cmd run smoke`（cwd=`server`）退出码 0，输出：
  - `PASS health`
  - `PASS ministries/sources/preferences CRUD`
  - `PASS item approve/reject/redraft(no-ai)`
  - `PASS redraft with AI mock`
  - `PASS scheduler fetch_log/source_id/dedupe/manual`
  - `PASS timezone migration/dashboard`
  - `SMOKE PASS: health/CRUD/item-flow/redraft/scheduler/timezone 全部断言通过`
- 复验运行前后 `server/data/` 内文件 `LastWriteTimeUtc.Ticks` 完全一致（`Compare-Object` 差异数为 0），未触碰仓库 `data/`。
- `%TEMP%` 下 `sansheng-smoke-*` 残留目录运行前后均为 0。

## 验收核对

- `server/package.json:11` 新增 `"smoke": "tsx src/smoke.ts"`，与 spec 要求一致。
- 退出码语义正确：成功路径 `smoke.ts:305` `process.exit(0)`，失败路径 `smoke.ts:308` `process.exit(1)`，断言抛出即触发非 0。
- `SSS_DB_PATH` 在 `smoke.ts:9` 设置，早于 `smoke.ts:56-57` 的 db/api 动态 import，指向 `mkdtemp` 临时目录。
- 上轮问题 1 已修复：`smoke.ts:109-120` 在准奏流程前后读取 dashboard，断言 `archivedToday` 增量 +1（`smoke.ts:119-120`）。
- 上轮问题 2 已修复：`main()` 改为 `try/finally`，`smoke.ts:297-300` 的 finally 依次 `closeAll()` → `db.close()` → `fs.rmSync(tmpDir)`；catch（`smoke.ts:306-308`）只记录错误并 `process.exit(1)`。
- 断言覆盖 health、六部/信息源/偏好卡 CRUD（含 dailyLimit 钳制、默认六部删除 400）、准奏/驳回/打回（AI 未配置）、AI mock 重拟、调度器 ok/error fetch_log、source_id 关联、URL 去重、时区迁移与幂等、createdToday/archivedToday 本地计数。

## 残留风险

smoke 与仓库 `data/` 之间无交互（复验前后时间戳一致）；`archive-assistant-web` 存在与本轮无关的既有子模块脏状态，P0-4 写集未触碰 web。
