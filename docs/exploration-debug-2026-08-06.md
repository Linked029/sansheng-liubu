# 探索卷宗调试记录（2026-08-06）

## 概述

本日在验证探索卷宗功能过程中，发现并修复了一系列搜索管线问题，包括搜索引擎选择、CSS 选择器适配、去重策略、可重复搜索、AI 拆词优化等。

---

## 问题链

### 1. DuckDuckGo 被墙 → 切换到 Bing
- **现象**: 5 个搜索词全部 `fetch failed`
- **根因**: DuckDuckGo 在中国大陆不可达
- **修复**: `search.ts` 中 `searchDuckDuckGo` → `searchWeb`，改用 `https://www.bing.com/search`
- **待办**: 后续可升级为多引擎回退（Bing → Baidu → Google）

### 2. Bing CSS 选择器不匹配
- **现象**: searchWeb 调用成功但返回 0 条
- **根因**: 沿用了 DuckDuckGo 的 `.result` / `.result__a` 选择器，Bing 的 HTML 结构是 `#b_results .b_algo` / `h2 a` / `.b_caption p`
- **修复**: 更新 cheerio 选择器为 Bing 的 DOM 结构
- **验证**: 直接调用 `searchWeb` 返回 9 条结果

### 3. 跨方向搜索结果被去重拦截
- **现象**: 第二个方向搜索"基于 ESP32 的智能手环设计"返回 0 条
- **根因**: `isExplorationUrlDuplicate` 检查了 `exploration_items` 表，第一个方向的 ESP32 搜索结果占用了所有 URL，第二个方向全部被去重
- **修复**: 去重只查 `items` 表（正式归档文章），不查 `exploration_items`（临时卷宗）。同一方向重搜可追加新结果，跨方向各自搜到相同 URL 也不互锁

### 4. 方向执行一次就被锁定
- **现象**: 方向 status 变成 "fulfilled" 后无法再次搜索
- **根因**: `runExplorationPipeline` 末尾调用了 `fulfillSearchDirection()`
- **修复**: 移除该调用，方向永远保持 active，可重复搜索。原始设计理念就是一周内反复探索

### 5. AI 拆词全加技术前缀
- **现象**: 搜索"基于 ESP32 的智能手环设计"时所有搜索词都带"ESP32"，结果全是 ESP32 芯片文档，没有手环相关内容
- **根因**: `decomposeDirection` 的 prompt 要求每条搜索词都保留原文关键词
- **修复**: prompt 改为三路拆词策略：
  - 1~2 词保留原文组合
  - 1~2 词去掉具体技术栈，只围绕应用场景本身
  - 其余词展开技术实现/教程/对比

### 6. 相关性过滤过严导致中文结果全灭
- **现象**: 修复拆词后"智能手环 功能 设计"这类搜索词返回 0 条
- **根因**: 空格分词 + 子串匹配对中文不友好。"智能手环设计"无法匹配到标题中"ESP32 智能手环"（断字不同）
- **尝试**: 多种方案（2-gram、完整片段、EN/CN 边界插入空格）均有不同程度的中文匹配失败
- **当前方案**: 临时关闭相关性过滤，让用户手动丢弃不相关结果。后续可优化为基于搜索词本身的轻量匹配

### 7. 方向删除功能缺失
- **现象**: 用户无法删除已完成的探索方向
- **修复**: 
  - `exploration.ts`: 新增 `deleteSearchDirection()`
  - `api.ts`: 新增 `DELETE /api/exploration/directions/:id` 路由
  - `ExplorationPane.tsx`: 每个方向旁添加垃圾桶按钮
- **注意**: 删除方向会级联删除关联的 exploration_items（CASCADE DELETE）

---

## 修改文件

### 主仓库
| 文件 | 变更 |
|------|------|
| `server/src/search.ts` | DuckDuckGo→Bing、CSS 选择器、去重策略、同批次去重、相关性过滤(禁用) |
| `server/src/ai.ts` | decomposeDirection prompt 三路拆词策略 |
| `server/src/exploration.ts` | isExplorationUrlDuplicate 只查 items、新增 deleteSearchDirection |
| `server/src/api.ts` | 新增 DELETE 方向路由、import deleteSearchDirection |

### Web 子仓库
| 文件 | 变更 |
|------|------|
| `src/ui/screens/ExplorationPane.tsx` | 完整重写：方向可反复搜索、垃圾桶删除按钮、UI 整理 |
| `src/lib/api.ts` | 新增 deleteDirection API 函数 |

---

## 当前状态

- ✅ 搜索后端: Bing（国内可用）
- ✅ CSS 选择器: Bing DOM 匹配正确
- ✅ 去重: 只检查已归档文章
- ✅ 可重复搜索: 方向保持 active
- ✅ 方向可删除: 垃圾桶按钮
- ✅ AI 拆词: 三路策略（保留原文 + 去技术栈 + 展开）
- ⚠️ 相关性过滤: 已禁用（避免中文误杀），用户手动筛选
- 📋 待办: 多搜索引擎回退、轻量中文相关性过滤