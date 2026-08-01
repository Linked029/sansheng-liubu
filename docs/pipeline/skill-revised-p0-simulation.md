# P0 模拟执行对比结论（修订版 skill）

日期：2026-08-01
对象：以三省六部 P0 为样本，对比“旧完整仪式”与“修订版小 P 模式”的执行开销。

## 旧流程实际开销（本次 P0 实测）

| 项 | 数值 |
|----|------|
| 子 Agent | 7 个（3 执行 + 4 审查） |
| main spawn | 7 次 |
| main wait | 11 次 |
| resume/send_input 补交 | 5 次 |
| 独立工作区复制 | 3 次 |
| report/review 全文读回 | 4 份 |
| 未落盘即结束的回合 | 4 个 |
| main shell 验证/合并操作 | 约 20 次 |
| 用户催促消息 | 10+ 条 |

成果：4 个 P 独立审查 PASS、4 次逐 P 提交、`npm run smoke` 机器验收通过。

## 修订版小 P 模式模拟（同一 P0 重跑）

流程：4 个 P 均由 main 直实现（写集均 ≤2 文件）→ 每个 P 自跑 verify → 1 个独立审查 Agent 对整个 P0 全量 verify → final-report → 硬闸门 2。

| 项 | 估算 |
|----|------|
| 子 Agent | 1 个（独立审查） |
| main spawn | 1 次 |
| main wait | 1 次 |
| resume/send_input | 0 次 |
| 独立工作区 | 0 个 |
| main 工具调用 | 24-30 次 |
| 用户交互 | 2 个硬闸门（开始 + 交付确认） |

预估收益：工具调用约减半；上下文约 0.4-0.7 倍；墙钟时间显著缩短；不再因逐 P 仪式产生中途停摆。

## 实时样本（本次 skill 修订，验证小 P 模式可行）

main 直接实现 4 个文件（SKILL.md + 3 references），1 个独立审查 Agent 验证：

- 1 次 spawn、3 次 wait、2 次 resume/send_input；
- 审查 3 轮：REWORK（案例数据与 docs 记录不一致）→ REWORK（结论表口径未同步）→ PASS；
- `quick_validate.py` 输出 `Skill is valid!`。

结论：小 P 模式主流程成立；两轮 REWORK 全部是案例数据自洽问题，若提交审查前先核对口径可省 2 轮。

## 最终结论

1. 修订版把固定开销从“每 P 完整仪式”降为“main 直实现 + 单审查”，P0 规模下收益明显。
2. 产物门能保证“落盘”，但保证不了“内容正确”；案例数据先自洽再交审查，是避免返工的关键。
3. 剩余风险：历史开销数字来自会话记录、不可复核；`quick_validate.py` 依赖 PyYAML（已安装）；web lint 为既有配置问题，与 P0 无关。
