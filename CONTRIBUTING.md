# 贡献指南

感谢你对智学物理项目的关注。无论是修复缺陷、完善文档还是提出新功能，都欢迎参与。

## 如何贡献

### 报告问题

请先搜索 [Issues](https://github.com/luoshuang148/physics-learning-platform/issues) 确认问题尚未被记录，然后使用 **Bug report** 模板提交，并尽量附上：

- 复现步骤与期望行为
- 环境信息（OS / JDK / Node / MySQL 版本）
- 相关日志或截图（注意脱敏）

### 功能建议

使用 **Feature request** 模板，说明使用场景与替代方案。设计层面的讨论请先开 Issue，避免直接提交大规模 PR。

### 提交代码

1. Fork 仓库并基于 `main` 创建分支

   ```bash
   git checkout -b feature/your-feature
   ```

2. 保持改动聚焦：一个 PR 解决一类问题，避免混入无关格式化改动

3. 本地验证

   ```bash
   ./mvnw -B test
   cd frontend && npm ci && npm test && npm run build
   ```

   涉及数据库结构时，请补充对应的 Flyway 迁移脚本与测试。

4. 编写清晰的提交信息（建议使用 `feat:` / `fix:` / `docs:` / `refactor:` / `test:` 前缀）

5. 发起 Pull Request，填写动机、改动点与验证方式

## 开发约定

- **后端**：Java 21，遵循现有分层（`controller` / `service` / `repository`）；权限校验在 Service 边界完成；不引入与现有栈冲突的框架
- **前端**：Vue 3 + TypeScript + Composition API；页面文案避免空话，交互与现有页面保持一致
- **数据库**：只通过 Flyway 迁移变更 schema，禁止在代码里 `ddl-auto: update`
- **安全**：不要提交 `.env`、密钥、真实用户数据；新接口默认要求认证，教师接口使用 `@TeacherOnly`
- **测试**：缺陷修复应附回归测试；新功能至少覆盖服务层主路径

## 行为准则

参与本项目即表示你同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

## 许可证

贡献内容默认以 [MIT License](LICENSE) 授权。
