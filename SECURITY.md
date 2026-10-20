# 安全政策

## 支持范围

| 版本 | 支持情况 |
|------|----------|
| `main` 分支 | ✅ 持续维护 |
| 其他分支 / Fork | ❌ 不提供安全支持 |

## 报告漏洞

**请勿在公开 Issue 中披露安全漏洞。**

请通过 GitHub 的 [Private vulnerability reporting](https://github.com/luoshuang148/physics-learning-platform/security/advisories/new) 提交，或发送邮件至仓库维护者（见 GitHub 主页）。

报告中请尽量包含：

- 漏洞类型与影响（如越权、注入、密钥泄露）
- 复现步骤或 PoC
- 影响版本 / 提交哈希
- 你认为可行的修复思路（可选）

我们会在 **7 个工作日内** 回复确认，并在修复后公开致谢（如你愿意）。

## 安全相关设计说明

本项目已实现的安全措施（供审计参考）：

- 密码使用 BCrypt 存储；JWT 带 token 版本，改密/登出即时吊销
- 登录限流（IP + 用户名维度）由 Redis Lua 原子执行
- 教师接口 `@TeacherOnly`（`@PreAuthorize`），Service 层校验资源归属
- AI 输出 HTML 净化；上传图片按内容哈希隔离
- 公开健康检查不暴露细节；运行指标仅教师可访问
- 敏感配置（数据库、JWT 密钥、模型 Key）只从环境变量读取，`.env` 已被 Git 忽略

仍存在的已知边界见 [README](README.md#现状与边界)。请勿在未授权的情况下对部署实例进行破坏性测试。
