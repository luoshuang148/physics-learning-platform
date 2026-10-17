# 智学物理后端项目

## 🎯 项目简介

智学物理是一个面向物理教学的智能化学习平台后端服务，基于 Spring Boot 3.3.8 构建，提供 AI 辅助教学、错题分析、学习路径规划等核心功能。项目采用分层架构设计，集成阿里云和智谱 AI 双重大模型，通过 HanLP 中文分词技术实现智能化的错题归因分析，为教师和学生提供精准的教学反馈和个性化学习建议。

### 核心技术

#### 🔧 **高并发实时通信架构**
- **SSE 长连接管理**: 基于 Spring `SseEmitter` 实现流式推送，支持超时回调、错误处理和自动重连配置
- **手动会话状态管理**: 自主设计内存级会话管理器 (`SessionManager`)，使用 `ConcurrentSkipListMap`和`ConcurrentHashMap` 实现线程安全的上下文追踪
- **异步任务编排**: AI 调用通过 `AIStreamExecutor` 统一处理流式操作异常，确保错误处理的标准化
- **并发安全控制**: 会话上下文 (`SessionContextInternalDTO`) 使用读写锁 (`ReentrantReadWriteLock`) 保护交互记录，`ConcurrentHashMap` 管理活跃 SSE 连接

#### 🧠 **AI 引擎深度集成**
- **双模型适配**: 阿里云 DashScope SDK 与智谱 AI Java Client 的统一封装，提供一致的流式调用接口
- **流式响应标准化**: 通过 `AIStreamCallback` 回调接口（AI 客户端专用）统一处理内容推送、完成事件和错误处理
- **会话上下文管理**: 基于数据库 (`ConversationHistory`) 和内存双层存储，支持连续的 AI 交互和历史记录查询

### 核心功能模块

#### 1️⃣ **AI 智能辅导系统**
- **实时问答**: 基于 SSE 长连接的流式问答服务，通过 `SseEmitter`逐字推送 AI 响应
- **错题分析**: 针对学生错题进行 AI 智能分析，提供知识点讲解和解题思路引导
- **学生报告**: 基于阿里 AI 生成个性化学习报告，分析答题情况和知识点掌握程度
- **教师分析**: 为教师提供班级整体学习情况/单题作答学情分析报告，支持教学决策
- **会话管理**: 自主实现 `SessionManager`，基于内存和数据库双层存储，支持连续的 AI 交互

#### 2️⃣ **答题记录管理**
- **答题提交**: 学生在线答题，系统自动判断正误并记录答题详情
- **错题本功能**: 自动收集学生错题，支持按个人/他人维度查询错题记录
- **会话追踪**: 按学习会话组织答题记录，支持查询最近会话和历史记录
- **数据分析**: 提供答题统计、错误率分析等多维度数据查询
- **权限控制**: 严格的数据访问权限管理，学生仅可访问个人数据，教师可查看全班数据

#### 3️⃣ **题目与视频资源管理**
- **题目管理**: 支持选择题的创建、编辑、删除，包含选项、答案、解析、难度等级等信息
- **视频管理**: 教学视频的上传和管理，支持知识点标签和视频时长元数据
- **关联映射**: 题目与视频的智能关联，支持视频讲解推荐
- **多媒体支持**: 题目支持附带图片，解析支持视频映射，提升学习体验

#### 4️⃣ **智能错题分析系统**
- **错误统计**: 按题目维度统计错误人次和错误率，帮助教师识别高频错题
- **学生详情**: 展示每道题答错的学生列表及具体错误原因
- **词云分析**: 基于 HanLP 中文分词技术，对学生自述错误原因进行语义分析，生成可视化词云
- **AI 题目分析**: 教师可对特定题目发起 AI 分析请求，获取深度学情诊断
- **实验专题优化**: 针对高中物理实验专题定制专业术语词典和停用词表，优化分词准确性

#### 5️⃣ **用户与权限管理**
- **JWT 认证**: 基于 JWT 的无状态身份认证机制，支持 Access Token 动态刷新
- **角色权限**: 区分学生和教师两种角色类型，实施细粒度的接口访问控制
- **用户管理**: 支持用户注册、登录（用户名 / 真实姓名均可）、信息更新、账户状态管理等完整生命周期管理
- **班级管理**: 支持班级信息的维护和学生班级归属管理

#### 6️⃣ **教师事件管理**
- **事件发布**: 教师可发布指定实验任务，即全局教学事件，全局事件在系统中唯一
- **事件查询**: 所有认证用户（学生/教师）可查询全局事件信息
- **事件删除**: 支持全局事件的删除操作，删除后自动清理关联数据（答案记录、对话历史、学生报告）

#### 7️⃣ **实验配置管理**
- **实验创建**: 教师可创建新的实验配置，包含实验名称、描述、题目列表和排序序号
- **实验删除**: 支持根据 ID 删除实验配置
- **实验更新**: 支持增量更新实验信息（只更新提供的字段）
- **实验查询**: 支持根据 ID 查询单个实验或获取所有实验列表
- **题目关联**: 实验与题目智能关联，支持有序题目 ID 列表管理
- **权限控制**: 严格的教师权限验证，学生仅可查询不可修改
- **唯一性校验**: 实验名称全局唯一、排序序号全局唯一、题目 ID 存在性验证

### 技术特色

🔹 **SSE 实时流式通信**: 基于 `SseEmitter` 实现 AI 响应的逐字推送，支持超时回调、错误处理和重连配置  
🔹 **SSE 连接中断检测**: `StreamUtils` 通过 `safeRemoveEmitter` 将 `SessionContextInternalDTO.cancelled` 设为 `true`，AI 服务调用 `isCancelled()` 检测客户端断开/超时/错误，自动提前退出节省资源  
🔹 **前端手动中断**: 学生可点击"停止"按钮（`AIFloatWidget`、`StudentReportView`）、"停止分析"按钮（`StudentWrongBookView`）通过 `AbortController` 中断 SSE 连接  
🔹 **手动内存会话管理**: 自主实现 `SessionManager`，使用 `ConcurrentHashMap` 按复合键存储会话，`ConcurrentSkipListMap` 按序列号组织交互记录，支持 LRU 淘汰策略  
🔹 **会话上下文**: `SessionContextInternalDTO` 通过 `volatile boolean cancelled` 字段追踪连接状态，AI 服务实时检测并提前退出节省资源  
🔹 **全异步任务处理**: AI 调用采用异步非阻塞模式，通过 `AIStreamExecutor` 统一处理异常  
🔹 **HanLP 领域 NLP**: 针对高中物理实验（原子物理、万有引力与宇宙航天）定制专业术语词典和停用词表  
🔹 **多层防御性编程**: 分层异常捕获 + 超时控制 + 连接数限制，`StreamUtils` 统一管理活跃 SSE 连接  

### 代码质量与文档规范

🔹 **完善的 Javadoc 注释**: 所有公共类和方法均提供标准 Javadoc 注释，包含参数说明、返回值描述和使用示例  
🔹 **Swagger API 文档**: 使用 OpenAPI 3.0 注解完整描述所有 REST 接口，支持在线调试和交互式文档浏览  
🔹 **中文注释友好性**: 关键业务逻辑、复杂算法和配置项均提供中文注释，降低团队协作成本  
🔹 **代码质量检查**: 集成 SpotBugs 静态分析工具，自动检测代码缺陷和安全隐患  

### 系统架构

```
┌─────────────────────────────────────────────────────┐
│              Controller Layer                       │
│  (SSE 流式控制器 / RESTful API / JWT 拦截器)          │
└─────────────┬───────────────────────────────────────┘
              │ 异步回调 + 事件驱动
┌─────────────▼───────────────────────────────────────┐
│              Service Layer                          │
│  (业务逻辑 / AI 代理 / 会话管理 / 事务管理)           │
├─────────────────────────────────────────────────────┤
│         SessionManager (ConcurrentHashMap 存储会话 + ConcurrentSkipListMap 组织交互记录)
│         AIStreamExecutor (异常处理模板)                               │
│         StreamUtils (活跃连接计数/超时控制)          │
└─────────────┬───────────────────────────────────────┘
              │ JPA Repository + 连接池优化
┌─────────────▼───────────────────────────────────────┐
│            Repository Layer                         │
│           (数据持久化层)                             │
└─────────────┬───────────────────────────────────────┘
              │ MySQL 8.4 + HikariCP
┌─────────────▼───────────────────────────────────────┐
│          External AI Services                       │
│    (阿里云 AI / 智谱 AI - 异步非阻塞调用)              │
└─────────────────────────────────────────────────────┘
```

## 🛠️ 技术栈

- **核心框架**: Spring Boot 3.3.8 + Spring Security 6 + Spring Data JPA
- **语言版本**: Java 21 (Record、var 关键字、Switch 表达式)
- **数据库**: MySQL 8.4 + HikariCP 连接池
- **AI 集成**: 阿里云 DashScope SDK + 智谱 AI Java Client
- **NLP 处理**: HanLP 中文分词引擎 (自定义高中物理实验术语词典)
- **实时通信**: Spring SseEmitter (超时回调/错误处理/重连配置)
- **并发工具**: CompletableFuture、ConcurrentHashMap、ConcurrentSkipListMap、ReentrantReadWriteLock
- **构建工具**: Maven 3.8+
- **测试框架**: JUnit 5 + Mockito + WireMock

## ⚙️ 环境配置

### 多环境配置策略
本项目采用标准的多环境配置管理：
- **开发环境（dev）**: 日常开发使用，连接真实 MySQL 数据库
- **测试环境（test）**: 运行测试时使用，使用 H2 内存数据库，自动 Mock 外部服务

### 标准端点访问
- **应用接口**: `http://localhost:8080/api/v1`
- **API文档**: `http://localhost:8080/swagger-ui.html`
- **健康检查**: `http://localhost:8080/api/v1/actuator/health` (已配置，未使用)
- **指标监控**: `http://localhost:8080/api/v1/actuator/metrics` (已配置，未使用)

## 🚀 快速开始


### 登录说明

平台支持“用户名 / 真实姓名”两种方式登录，若同一标识既为某用户真实姓名、又为另一用户用户名，则优先匹配真实姓名。

注册/修改用户名时，用户名不能与任何真实姓名重复（单向唯一性约束）。

**最简单的方式**：在项目根目录双击运行或命令行执行
```bash
dev-toolkit.bat
```

这将显示交互式菜单，包含以下功能：
```
1. 启动开发服务器     → 自动检查环境、处理端口占用、启动应用
2. 运行测试          → 单元测试、集成测试、自定义测试
3. 快速测试          → 高性能并行测试，适合快速验证
4. 代码质量检查      → 编译检查 + SpotBugs 静态分析
5. 外部 API 测试     → 测试阿里云/智谱 AI 服务
6. AI 服务测试       → 一键测试 AI 问答、报告生成等功能
7. 查看项目信息      → 技术栈、端点信息等
8. 退出              → 关闭工具箱
```

### 方式二：直接运行单个脚本

如果不想使用菜单，可以直接运行：

```bash
# 启动开发服务器
scripts\start-dev.bat

# 运行测试
scripts\run-tests.bat

# 快速测试（推荐）
scripts\fast-test.bat

# 代码质量检查
scripts\code-quality.bat

# 外部 API 测试
scripts\run-external-api-tests.bat

# AI 服务测试（PowerShell）
powershell -ExecutionPolicy Bypass -File scripts\test-ai-simple.ps1
```

### 方式三：手动命令

```bash
# ✅ 正确的启动命令（重要：这是唯一正确的启动方式）
mvn spring-boot:run "-Dspring.profiles.active=dev"

# ❌ 错误的启动命令示例（AI 经常试错的）
# mvn spring-boot:run -Dspring-boot.run.profiles=dev
# mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev
# mvn spring-boot:run -Dspring.profiles.active=dev（不加引号在 PowerShell 中会出错）
```

## 💡 推荐使用场景

### 日常开发
```bash
# 1. 启动开发服务器（选项 1）
dev-toolkit.bat → 选择 1

# 2. 修改代码后快速验证（选项 3）
dev-toolkit.bat → 选择 3

# 3. 提交前质量检查（选项 4）
dev-toolkit.bat → 选择 4
```

### 测试 AI 功能
```bash
# 1. 先启动开发服务器（选项 1）
dev-toolkit.bat → 选择 1

# 等待应用启动完成后...

# 2. 运行 AI 服务测试（选项 6）
dev-toolkit.bat → 选择 6
```

### 运行特定测试
```bash
# 直接运行单个脚本更快捷
scripts\run-tests.bat  # 然后选择测试类型
```

## ⚙️ 环境准备

### 必需环境
- ✅ **Java**: JDK 21 或更高版本（已验证：Java 21.0.10）
- ✅ **Maven**: Maven 3.8 或更高版本（已验证：Maven 3.9.14）
- ✅ **数据库**: MySQL 8.0+

### 环境变量配置

创建 `.env` 文件在项目根目录：
```bash
SPRING_PROFILES_ACTIVE=dev
DB_USER=your_username
DB_PASSWORD=your_password
ALIYUN_AI_API_KEY=your_aliyun_key
ZHIPU_AI_API_KEY=your_zhipu_key
APP_JWT_SECRET=your_jwt_secret
```

## 🚀 生产部署（单机无 Nginx）

当前仓库已支持前后端合并部署（同一应用容器），推荐部署流程：

```bash
# 1) 准备生产环境变量
cp .env.production.example .env

# 2) 启动（首次或更新都可使用）
docker compose up -d --build

# 3) 检查状态
docker compose ps
docker compose logs -f app
```

- 前端入口：`http://<你的域名或公网IP>/api/v1/`
- 健康检查：`http://<你的域名或公网IP>/api/v1/actuator/health`
- 详细说明见：`docs/生产部署指南.md`

### 运行测试
```bash
# 纯单元测试（包括 AI 同步/异步测试）
# 注：默认配置已包含这些分组，可直接使用 mvn test
mvn test -Dgroups="unit,ai-sync,ai-async"

# 控制器 + Repository 集成测试
mvn test -Dgroups="integration-controller,integration-repository"

# 完整测试（默认配置：单元 + 集成，排除外部 API 测试）
mvn test

# 指定测试类
mvn test -Dtest="QuestionControllerIntegrationTest"

# 外部 API 测试（使用真实 AI 服务，需配置 API Key）
mvn clean test -Pexternal-api-test

# 仅运行特定外部 API 测试
mvn clean test -Pexternal-api-test -Dtest="AliyunAIExternalApiTest"   # 阿里云 AI
mvn clean test -Pexternal-api-test -Dtest="ZhipuAIExternalApiTest"    # 智谱 AI
```

## 测试策略

### 测试分类
1. **单元测试** - 服务层业务逻辑测试（`service/unit/`），使用 H2 数据库
2. **集成测试** - 控制器层和 Repository 层测试（`controller/`, `repository/`），使用 H2 数据库
3. **外部 API 测试** - 测试与阿里云/智谱 AI 的真实交互（需配置 API Key，使用 dev 环境）

### 测试环境规范
⚠️ **重要**: 项目使用独立的 `test` 环境运行测试：
- 测试自动使用 `application-test.yml` 配置
- 使用 H2 内存数据库，快速执行且数据隔离
- 外部 AI 服务通过 WireMock 等工具 Mock
- 无需手动指定 `-Dspring.profiles.active=test`（Maven 自动识别）

### 测试执行脚本
```bash
# 使用测试脚本（交互式选择）
scripts\run-tests.bat

# ✅ 正确的测试命令格式（自动使用 test 环境）
mvn test -Dtest="QuestionControllerIntegrationTest"

# ❌ 错误的测试命令示例
# mvn test -Dspring-boot.run.profiles=dev  # 测试不应使用 dev 环境
# mvn test --spring.profiles.active=test  # 参数格式错误
```

## 代码质量检查

```bash
# 使用质量检查脚本
scripts\code-quality.bat

# 或者手动执行
mvn compile test-compile          # 编译检查
mvn spotbugs:check               # 静态分析
mvn checkstyle:check             # 代码风格检查
```

## 项目结构

```
src/
├── main/
│   ├── java/com/zhixuewuli/
│   │   ├── controller/          # 控制器层（10 个 Controller）
│   │   ├── service/             # 服务层（10 个服务模块）
│   │   ├── repository/          # 数据访问层（8 个 Repository）
│   │   ├── entity/              # 实体类（9 个实体）
│   │   ├── dto/                 # 数据传输对象（PageRequestDTO、request/response DTOs 若干）
│   │   └── common/              # 公共组件（client/config/constant/exception/interceptor/mapstruct/result/util）
│   │   └── Application.java     # Spring Boot 启动类
│   └── resources/
│       ├── application.yml      # 主配置文件
│       ├── application-dev.yml  # 开发环境配置
│       ├── application-prod.yml # 生产环境配置
│       ├── logback-spring.xml   # 日志配置
│       └── sql/                 # SQL 脚本（schema、test_data 等）
└── test/
    ├── java/com/zhixuewuli/
    │   ├── controller/          # 控制器集成测试（11 个集成测试 + 2 个基础设施测试）
    │   ├── repository/          # Repository 集成测试（8 个测试类）
    │   ├── service/
    │   │   ├── unit/            # 服务层单元测试（11 个测试类）
    │   │   ├── integration/     # 服务层集成测试
    │   │   ├── ai/              # AI 服务测试
    │   │   └── external/        # 外部 API 测试
    │   └── common/              # 公共测试组件（base/factory/interceptor/matcher/test/util）
    └── resources/
        ├── application-test.yml # 测试专用配置（H2 数据库+Mock 服务）
        ├── sql/                 # 测试数据 SQL（13 个场景数据文件 + 1 个说明文档）
        └── wiremock/            # WireMock Mock 配置
```

## 开发规范

### 配置管理
- **开发环境**：使用 `dev` 环境，连接真实 MySQL 数据库，开启热部署
- **测试环境**：使用 `test` 环境（H2 内存数据库 + Mock 外部服务），隔离快速执行
- **生产环境**：使用 `prod` 环境，严格安全配置，关闭调试端点
- 敏感信息通过环境变量或 `.env` 文件覆盖配置文件中的默认值

### 并发编程规范
- 所有 AI 调用必须通过 `AIStreamExecutor` 提交至异步线程池
- 会话管理器 (`SessionManager`) 使用 `ConcurrentHashMap` 存储会话，读写锁保护 `ConcurrentSkipListMap` 中的交互记录
- SSE 连接需配置超时回调、错误回调和完成回调，通过 `StreamUtils` 统一管理，连接断开时设置 `cancelled` 标志
- AI 服务通过 `isCancelled()` 检测连接中断，自动提前退出节省资源
- 活跃连接数通过 `AtomicInteger`计数，超过阈值时拒绝新连接

### 测试规范
- 单元测试：不依赖 Spring 上下文，快速执行（使用 H2 数据库）
- 集成测试：需要 Spring 上下文，验证组件协作（使用 H2 数据库 + WireMock）
- 外部 API 测试：使用真实 AI 服务（需配置 API Key，使用 dev 环境）
- 测试数据：通过 SQL 脚本加载，每次测试后自动清理

### 代码质量
- 遵循 Java 编码规范，使用 Record 简化 DTO 定义
- 保持合理的代码覆盖率（目标 >70%）
- 定期执行静态代码分析（SpotBugs + Checkstyle）

## 常见问题

### 端口占用问题
脚本会自动检测并提示处理 8080 端口占用情况。

### 测试环境配置
- **开发环境**：使用 `dev` 环境，连接真实 MySQL 数据库
- **测试环境**：使用 `test` 环境，使用 H2 内存数据库，自动 Mock 外部服务
- 运行测试时 Maven 自动识别并使用 `application-test.yml` 配置

### 数据库连接
确保MySQL服务正常运行，数据库 `zhixuewuli` 已创建。

## ❓ 常见问题快速解决

### Q1: 端口被占用怎么办？
**A:** 启动脚本会自动检测并询问你是否强制终止占用进程，输入 `y` 即可。

### Q2: SSE 连接断开如何处理？
**A:** 
1. **前端手动中断**：学生可点击"停止"按钮中断 SSE 连接，系统会提前退出 AI 调用节省资源
2. **客户端自动断开**：浏览器关闭或网络中断时，`StreamUtils` 自动检测并设置 `cancelled` 标志
3. **后端超时处理**：后端设置合理的超时时间（默认 60s），超时后自动关闭连接
4. **上下文恢复**：会话管理器会保留最近 10 条对话历史，重连后可恢复上下文

### Q3: 异步任务执行失败如何排查？
**A:**
1. 查看应用日志中 AI 调用的异常堆栈
2. 检查 AI API Key 是否有效且未超限
3. 验证网络连接和 DNS 解析是否正常
4. 使用 `/actuator/metrics` 端点查看线程池状态

### Q4: 测试失败怎么办？
**A:** 
1. 检查 MySQL 是否启动
2. 检查环境变量配置
3. 查看错误日志定位具体问题

### Q5: 如何查看详细的测试输出？
**A:** 测试输出默认会显示在控制台，如需查看详细日志，可以：
1. 检查 `src/test/resources/application-test.yml` 中的日志级别配置
2. 将 `com.zhixuewuli` 包的日志级别设置为 `DEBUG`
3. 查看 Maven 输出的完整日志

### Q6: PowerShell 脚本无法运行？
**A:** 使用以下命令绕过执行策略：
```bash
powershell -ExecutionPolicy Bypass -File scripts\test-ai-simple.ps1
```

### Q7: 会话管理器如何保证线程安全？
**A:**
1. 使用 `ConcurrentHashMap`存储会话映射，键为复合键 (sessionId:type:questionId)
2. 会话内部使用 `ConcurrentSkipListMap`按序列号组织交互记录
3. 关键操作使用 `ReentrantReadWriteLock` 保护（如添加交互记录、生成序列号）
4. 通过 `AtomicInteger` 维护 token 计数器，保证并发递增

## 📊 性能优化实践

### 并发性能调优
针对多核 CPU 和 IO 密集型场景的优化策略：

**测试并行化配置** (`fast-test.bat`)：
- Fork 数：CPU 核心数 × 0.67（`-Dsurefire.forkCount=0.67C`）
- 每 Fork 线程数：2（`-Dsurefire.threadCount=2`）
- JVM 堆内存：6GB（`MAVEN_OPTS=-Xmx6G`）
- 预期效果：8 核 CPU 上测试时间从 120s 降至 35s

**应用层并发优化**：
- AI 调用采用异步非阻塞模式，`AIStreamExecutor` 统一处理异常
- 数据库连接池使用 HikariCP，支持连接超时和最大连接数限制
- SSE 长连接基于 `SseEmitter`实现，`StreamUtils`统一管理活跃连接（带连接数限制检查）
- 会话缓存采用 `ConcurrentHashMap` 存储会话，`ConcurrentSkipListMap` 按序列号组织交互记录，支持基于数量的 LRU 淘汰
- 会话上下文 (`SessionContextInternalDTO`) 使用读写锁保护交互记录，`cancelled` 标志支持连接中断检测

如需调整参数，编辑 `scripts/fast-test.bat` 第 60-63 行：
```batch
-Dsurefire.forkCount=0.67C ^    # 调整并行度
-Dsurefire.threadCount=2 ^      # 调整每进程线程数
```

## 测试策略与质量保障

### 测试分层架构

采用**金字塔型测试策略**，确保测试效率和覆盖率的平衡：

```
        ┌─────────────────┐
        │  外部 API 测试   │ ← 少量但关键（真实 AI 服务调用）
        │  (2 个测试类)    │
        └─────────────────┘
              ↓
    ┌─────────────────────┐
    │   Controller 集成测试 │ ← 端到端验证（WireMock + MockMvc）
    │   (11 个测试类 + 2 个基础设施)      │
    └─────────────────────┘
              ↓
  ┌─────────────────────────┐
  │  Service 单元测试        │ ← 核心业务逻辑（同步 + 异步双模式）
  │  (11 个测试类 + AI 专项)  │
  └─────────────────────────┘
              ↓
┌───────────────────────────────┐
│   Repository 集成测试         │ ← 数据访问层验证
│   (8 个测试类)                │
└───────────────────────────────┘
```

### 核心测试亮点

#### ✅ **1. AI 模块分层测试**

针对 AI 流式服务和异步任务的特殊性，设计了**四层**测试体系：

**第一层：Service 同步测试** (`@Tag ai-sync`)
- 完全 Mock AI 客户端和 `StreamUtils`
- 专注验证业务逻辑：会话管理、权限检查、提示词构建
- 代表文件：`ZhiPuAIAssistantSyncTest.java`, `AliyunAIReportSyncTest.java`

**第二层：Service 异步测试** (`@Tag ai-async`)
- 使用 `CountDownLatch`和`AtomicReference`捕获异步回调
- 验证 `CompletableFuture` 任务提交和线程池行为
- 代表文件：`ZhiPuAIAssistantAsyncTest.java`, `AliyunAIReportAsyncTest.java`

**第三层：Controller 集成测试**
- WireMock 模拟真实 AI API 响应
- MockMvc 验证 SSE 流式输出格式
- 端到端测试整个调用链路
- 代表文件：`AIControllerAliyunIntegrationTest.java`, `AIControllerZhiPuIntegrationTest.java`

**第四层：内存泄漏守卫测试** (`@Tag ai-async`)
- 验证 SSE 连接正常/异常中断后，异步计数与连接计数回落到基线
- 确保 `StreamUtils` 不会产生内存泄漏
- 代表文件：`ZhiPuAIAssistantServiceLeakGuardTest.java`

---

#### ✅ **2. 高并发场景测试覆盖**

针对 SSE 长连接和会话管理的并发安全性，设计专项测试：

**SessionManager 并发测试**：
- 10 个线程并发添加交互记录，验证序列号连续性
- 验证 `ConcurrentSkipListMap` 的线程安全性
- 验证 LRU 淘汰策略在并发场景下的正确性

**SessionContextInternalDTO 锁机制测试**：
- 验证读写锁 (`ReentrantReadWriteLock`) 保护交互记录的并发安全
- 验证写锁保护序列号生成和记录添加
- 验证读锁保护查询操作

**StreamUtils 连接管理测试**：
- 验证连接数限制检查和超时回调机制
- 验证活跃连接统一管理

---

### 测试执行策略

#### 测试分类与分组

使用 JUnit 5 `@Tag`实现测试分组，支持灵活执行：

```bash
# 纯单元测试（包括 AI 同步/异步测试）
mvn test -Dgroups="unit,ai-sync,ai-async"

# 控制器 + Repository 集成测试
mvn test -Dgroups="integration-controller,integration-repository"

# 完整测试（默认配置：单元 + 集成，排除外部 API 测试）
mvn test

# 外部 API 测试（使用真实 AI 服务，需配置 API Key）
mvn clean test -Pexternal-api-test
```

#### 并行测试优化

针对多核 CPU 优化的并行测试配置 (`fast-test.bat`)：
- Fork 数：CPU 核心数 × 0.67（`-Dsurefire.forkCount=0.67C`）
- 每 Fork 线程数：2（`-Dsurefire.threadCount=2`）
- JVM 堆内存：6GB（`MAVEN_OPTS=-Xmx6G`）
- **实测效果**：测试时间降至 66s

---

### 测试质量指标

#### 覆盖率统计

按**应测试的核心代码**计算：

```
应测试的核心代码 = 33 (业务) + 4 (AI) + 7 (工具) = 44
已测试的核心代码 = 44

有效覆盖率 = 44 / 44 = 100%
```

#### 测试文件规模

| 测试类型 | 测试类数量 | 说明 |
|----------|-----------|------|
| Controller 集成测试 | 11 | 端到端验证 |
| Controller 基础设施测试 | 2 | 测试基础设施 |
| Service 单元测试 | 11 | 同步 + 异步双模式 |
| AI 专项测试 | 5 | 分层测试（含内存泄漏守卫） |
| Repository 集成测试 | 8 | 数据访问验证 |
| 工具类测试 | 7 | 核心工具验证 |

---

## 📝 最佳实践

### 开发效率优化
1. **每天第一次开发**
   - 运行 `dev-toolkit.bat` 选择 1 启动服务
   - 检查应用是否正常启动，验证 SSE 端点可连接

2. **代码修改后**
   - 手动重启应用（Spring Boot 约 5-8 秒）
   - 运行 `dev-toolkit.bat` 选择 3 快速验证核心功能
   - 或手动运行 `scripts\fast-test.bat` 并行执行测试

3. **提交代码前**
   - 运行 `dev-toolkit.bat` 选择 4 质量检查
   - 确保所有测试通过，SpotBugs 无严重警告
   - 检查并发相关代码的线程安全性

4. **测试新功能**
   - 编写测试类，优先使用单元测试验证业务逻辑
   - 运行 `dev-toolkit.bat` 选择 2 运行自定义测试
   - 涉及 AI 调用的功能需配置 API Key 并运行外部 API 测试

### 性能调试技巧
5. **SSE 连接问题排查**
   - 使用浏览器开发者工具查看 Network 面板
   - 检查 `text/event-stream` Content-Type 是否正确
   - 验证心跳包和超时设置

6. **异步任务监控**
   - 通过 `/actuator/metrics` 查看线程池活跃度
   - 使用 VisualVM 等工具观察 CompletableFuture 执行情况
   - 日志中记录关键异步操作的耗时

## 📚 文档说明

### 项目文档

| 文档 | 用途 |
|------|------|
| `README.md` | 项目说明和使用指南（本文档） |
| `docs/` | 前后端交互 API、数据字典、错误处理等详细文档 |
| `scripts/README.md` | scripts 文件夹中脚本的详细说明 |

### 代码注释规范

**Javadoc 标准注释**：
- 所有公共类、方法、字段均提供 Javadoc 注释
- 包含 `@param`、`@return`、`@since` 等标准标签
- 复杂方法提供使用示例和注意事项
- 工具类提供完整的功能说明和设计意图

**Swagger API 文档**：
- 所有 Controller 接口使用 `@Operation` 注解描述功能
- 请求参数和响应模型使用 `@Schema` 注解详细说明
- 支持访问 `/swagger-ui.html` 在线浏览和调试 API
- 每个接口标注可能的响应码和错误场景

**中文注释友好性**：
- 关键业务逻辑提供中文注释解释设计意图
- 复杂算法步骤逐行注释说明
- 配置项提供中文说明和推荐值
- 异常处理注释说明触发条件和恢复建议

**代码质量保障**：
- 使用 SpotBugs 进行静态代码分析，检测潜在缺陷
- 集成 FindSecBugs 安全扫描插件，识别安全风险
- 通过 Maven 插件自动化执行代码检查

---

**祝开发愉快！** 🚀