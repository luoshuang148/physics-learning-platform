# 开发工具脚本说明

本项目提供了一套精简且实用的开发工具脚本，所有命令均经过验证，真实可用。

## 目录结构

```
zhixuewuli-backend/
├── dev-toolkit.bat          # 主入口菜单（推荐）
└── scripts/
    ├── start-dev.bat        # 启动开发服务器
    ├── run-tests.bat        # 运行各类测试
    ├── fast-test.bat        # 快速并行测试
    ├── code-quality.bat     # 代码质量检查
    ├── run-external-api-tests.bat  # 外部 API 测试
    └── test-ai-simple.ps1   # AI 服务快速测试
```

## 使用方法

### 方式一：使用主菜单（推荐）

在项目根目录运行：
```bash
dev-toolkit.bat
```

这将显示一个交互式菜单，包含所有可用选项。

### 方式二：直接运行单个脚本

#### 1. 启动开发服务器
```bash
scripts\start-dev.bat
```
功能：
- 自动检查 Java 21+ 和 Maven 3.8+ 环境
- 检测并处理 8080 端口占用
- 启动 Spring Boot 应用（dev 环境）

#### 2. 运行测试
```bash
scripts\run-tests.bat
```
提供以下测试选项：
- 单元测试（unit 标签）
- 集成测试（integration 标签）
- 完整测试（所有测试）
- 自定义测试类

#### 3. 快速测试（高性能模式）
```bash
scripts\fast-test.bat
```
特点：
- 针对多核 CPU 优化（并行执行）
- 自动编译项目
- 适合快速验证代码

#### 4. 代码质量检查
```bash
scripts\code-quality.bat
```
提供：
- 编译检查（快速验证语法）
- SpotBugs 静态分析
- 完整质量检查

#### 5. 外部 API 测试
```bash
scripts\run-external-api-tests.bat
```
用于测试真实的外部 API 服务：
- 阿里云 AI 服务
- 智谱 AI 服务

#### 6. AI 服务测试（PowerShell）
```bash
powershell -ExecutionPolicy Bypass -File scripts\test-ai-simple.ps1
```
功能：
- 自动登录获取 Token
- 测试 AI 实时问答
- 测试学生报告生成
- 测试对话历史查询

## 环境要求

- **Java**: JDK 21 或更高版本
- **Maven**: Maven 3.8 或更高版本
- **数据库**: MySQL 8.0+
- **操作系统**: Windows

## 环境变量配置

确保在 `.env` 文件或系统环境变量中设置：

```bash
SPRING_PROFILES_ACTIVE=dev
DB_USER=your_db_user
DB_PASSWORD=your_db_password
ALIYUN_AI_API_KEY=your_aliyun_key
ZHIPU_AI_API_KEY=your_zhipu_key
APP_JWT_SECRET=your_jwt_secret
```

## 常见问题

### Q: 端口被占用怎么办？
A: `start-dev.bat` 会自动检测并提供两个选项：
   - 强制终止占用进程（输入 y）
   - 手动关闭后重试

### Q: 测试失败如何处理？
A: 查看输出日志，常见原因：
   - 数据库连接失败：检查 MySQL 是否启动
   - 环境变量缺失：检查 .env 配置
   - 端口冲突：运行脚本自动清理

### Q: 如何调整并行测试的性能参数？
A: 编辑 `fast-test.bat`，修改以下变量：
   ```batch
   set PARALLEL_FORK=0.67C    # Fork 数量（CPU 核心数的 67%）
   set PARALLEL_THREAD=2      # 每 Fork 的线程数
   ```

## 最佳实践

1. **日常开发**：使用 `dev-toolkit.bat` 菜单选择对应功能
2. **快速验证**：使用 `fast-test.bat` 进行并行测试
3. **提交前检查**：使用 `code-quality.bat` 进行质量检查
4. **AI 功能测试**：先启动应用，然后运行 `test-ai-simple.ps1`

## 技术说明

### 测试分组策略

项目使用 TestNG 的 groups 功能对测试进行分类：

- **unit**: 纯单元测试
- **ai-sync**: AI 同步服务测试
- **ai-async**: AI 异步服务测试
- **integration-controller**: 控制器集成测试
- **integration-repository**: 数据访问层集成测试
- **external-api**: 外部 API 集成测试（需要真实 API 密钥）

### SpotBugs 配置

使用 SpotBugs 进行静态代码分析，配置文件：
- `spotbugs-security-include.xml` - 包含的检查项
- `spotbugs-security-exclude.xml` - 排除的检查项

## 更新日志

### v2.0 (2026-03-16)
- ✓ 全面精简所有脚本，移除冗余代码
- ✓ 修复不存在的测试套件引用
- ✓ 统一错误提示格式
- ✓ 优化用户体验（更清晰的提示信息）
- ✓ 增强 AI 测试脚本功能
- ✓ 添加完整的文档说明
