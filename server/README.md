# 三省六部 V3.0 本地服务

P0 最小可用闭环：SQLite 数据模型 + 本地 HTTP API + 本机调度器 + V2 数据迁移。

## 启动

```bash
npm install
npm run dev        # 开发模式，监听 http://localhost:4318
npm run build      # TypeScript 编译到 dist/
node dist/index.js # 生产模式
```

服务启动后会：

- 在 `server/data/sansheng.sqlite` 初始化 SQLite 数据库，并写入默认六部与偏好卡；
- 启动每 30 秒检查一次的本机调度器，按每部 `scheduleCron` 定时抓取；
- 启动时补抓当天已到时间但尚未执行的六部任务；
- 若 `archive-assistant-web/dist` 存在，直接托管 Web 前端。

## 数据迁移

把 V2 版 IndexedDB JSON 导出文件导入 SQLite：

```bash
npm run migrate -- <v2-export.json>
```

迁移会把 `topics` 映射为 `Ministry`，`items` 映射为已归档奏折，并导入 AI 设置。

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard` | 三省工作台统计 |
| GET/POST/PUT/DELETE | `/api/ministries` | 六部/自定义主题 |
| GET/POST | `/api/ministries/:id/sources` | 信息源列表/新增 |
| PUT/DELETE/POST test | `/api/sources/:id` | 信息源编辑/删除/抓取测试 |
| GET/PUT | `/api/preferences/:ministryId` | 每部偏好卡 |
| GET/POST/PUT/DELETE | `/api/items` | 奏折与手动录入 |
| POST | `/api/items/:id/approve` | 准奏归档 |
| POST | `/api/items/:id/reject` | 驳回并记入日志 |
| POST | `/api/items/:id/redraft` | 打回重拟 |
| POST | `/api/scheduler/run` | 手动触发抓取（可按部） |
| GET | `/api/fetch-logs` | 采集日志 |
| GET | `/api/reject-logs` | 驳回日志 |
| GET/PUT | `/api/settings` | AI 引擎与自动准奏阈值 |
| GET/POST | `/api/export/json` `/api/import/json` | 全量备份/恢复 |
| GET | `/api/export/markdown` | Obsidian Markdown 导出 |

## 环境变量

- `PORT`：服务端口，默认 `4318`；
- `SSS_DB_PATH`：SQLite 文件路径，默认 `server/data/sansheng.sqlite`。
