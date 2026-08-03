# 三省六部 · 聚合拾遗

本地优先的个人知识库：信息采集 → 审批 → 归档 → 阅读 → 复习，以“六部”组织内容，server + web 一体交付。

## 功能

- 六部工作流：候选（中书省）→ 待批（门下省）→ 已归档（尚书省）
- 信息源：Feed / URL / 手动录入，按偏好卡定时抓取，失败自动生成异常奏折
- 阅读：全文阅读、批注（批红）新增/删除、标记已读
- 复习：SM-2 间隔重复，档位 1/3/7/15/30 天，忘记/困难/良好/轻松四档反馈
- 统计：今日到期、今日完成、完成率、每部到期、本周学习
- 数据：JSON / Markdown 导出，JSON 导入（含 reviews 与 annotations）

## 目录结构

```text
server/                  # Express + SQLite 本地服务与调度器
archive-assistant-web/   # React + Vite 前端（独立 Git 仓库）
docs/pipeline/           # 流水线文档（plan/review/final-report）
plan.md                  # 生产计划与状态表
```

## 快速开始

```powershell
cd server
npm install
npm run dev
```

服务默认地址：http://localhost:4318（构建后直接访问同一端口）。

前端开发模式：

```powershell
cd archive-assistant-web
npm install
npm run dev
```

## 验证

```powershell
cd server
npm run build
npm run smoke

cd ../archive-assistant-web
npm run build
npm run lint
```

## 相关仓库

- 前端：[Linked029/archive-assistant-web](https://github.com/Linked029/archive-assistant-web)
