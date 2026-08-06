# 三省六部安全边界（本地阶段 / 分发阶段）

日期：2026-08-06

## 约定

当前处于“本地功能齐全”阶段，安全目标以不阻塞本地双轨采集、三层自动化、本地搜索/embedding 闭环为准。分发测试前再按清单加强安全。

## 本地阶段已生效边界

- 服务仅绑定 `127.0.0.1`，不监听局域网。
- 写入接口可选 `SSS_TOKEN` Bearer 认证；未配置时保持本地向后兼容。
- AI 密钥掩码：设置页与 `GET /api/settings` 只返回掩码值；掩码值不会回写数据库；导出 JSON 剔除 ai/presets 的 apiKey；导入时不覆盖已有真实密钥。
- 统一内部 AI 配置入口 `getInternalAiSettings()`：`SSS_AI_KEY` 优先，settings 表兜底，调度器与探索管线同入口。
- SSRF：统一 fetcher 层做 DNS 解析后 IP 校验 + 重定向逐跳校验；拦截 loopback（除非 `SSS_ALLOW_LOCALHOST_FETCH=1`）、私网、链路本地、metadata、IPv4-mapped IPv6 内网形态。
- 信息源创建/更新、导入与 `PUT /api/items/:id` 校验 `http(s)` scheme；信息源 location 同时做内网/保留地址初筛。
- `SSS_ALLOW_LOCALHOST_FETCH=1` 只放行 loopback，不放行私网或 metadata。

## 本地搜索 / embedding 落地时的预留

当 SearXNG、embedding 等服务部署到局域网（NAS、另一台机器）时，新增精确 CIDR 白名单（如 `SSS_ALLOW_PRIVATE_FETCH_CIDR=192.168.0.0/16`），禁止恢复全局放行。

## 分发测试前清单

- 强制认证，移除“未配置 `SSS_TOKEN` 时放行”的本地兼容分支。
- TLS / 反向代理，服务不再直暴露 `127.0.0.1`。
- CORS 收紧为固定来源；补齐 CSRF 防护。
- 请求限流与超时预算（AI、搜索、抓取各链路）。
- 密钥管理（环境变量/系统凭据库）与审计日志。
- 依赖漏洞扫描与运行时升级策略。
- 多用户隔离与数据权限模型（若支持多用户）。
