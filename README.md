# Physics Learning Platform

面向物理教学的学习平台，包含学生答题、错题整理、教师任务管理、AI 流式辅导和学情报告。后端采用 Java 21 / Spring Boot 3.3.8 / Spring Data JPA / MySQL，前端采用 Vue 3 / TypeScript / Vite。

项目适合作为后端实习作品展示：重点是权限边界、会话隔离、流式请求资源管理、数据库查询和数据生命周期。它是单体教学项目，目前没有生产流量或分布式部署的性能证明。

## 已实现

- 学生注册、JWT 登录、BCrypt 密码校验；公开注册仅允许学生，教师由运营初始化或已有教师创建。
- 教师管理题目、实验配置、视频和教学任务；结束任务会归档，保留答题、对话和报告。任务报告按任务 ID 取数，任务切换会取消旧流并忽略晚到响应。
- MySQL 生成列配合唯一索引约束全局仅一个活动任务；新学习记录保存任务 ID，旧记录保留空关联。
- 教师答题记录在数据库分页，支持错题和学生姓名筛选、稳定排序；列表投影不传输题目图片。
- 使用 OkHttp 对接兼容 OpenAI 格式的模型接口，通过 Spring MVC `SseEmitter` 向浏览器推送内容。
- 对话缓存按用户、任务和业务类型隔离，支持从数据库恢复、过期清理和容量限制。
- SSE 请求独立取消：断开或超时会取消上游 HTTP 调用；同一会话只允许一个活动请求，并限制全局、用户连接数及执行队列。
- Maven Wrapper 和 GitHub Actions 分别验证后端、前端与 MySQL 新建/升级后的表结构。

## 本地运行

需要 JDK 21、MySQL 8.0+（CI 使用 8.4）、Node.js 22。Maven 由 Wrapper 下载，无需全局安装。

```bash
cp .env.dev.example .env
# 修改 .env 中的数据库密码和 JWT 密钥
mysql -u root -p < src/main/resources/sql/schema_init.sql
./mvnw spring-boot:run
```

初始化 SQL 仅用于空库。已有旧版数据请先备份，再按[生产部署指南](docs/生产部署指南.md)执行增量升级。应用用 `ddl-auto=validate` 校验表结构，不会自动建表。

另一个终端启动前端：

```bash
cd frontend
npm ci
npm run dev
```

访问 [http://localhost:5173](http://localhost:5173)，后端 API 前缀为 `/api/v1`。开发环境默认关闭 AI，无需模型密钥即可运行普通功能；启用 AI 时在 `.env` 中设置 `AI_ENABLED=true` 和两个供应商密钥，需要自行确认模型权限和费用。

首次使用时在 `.env` 临时设置：

```dotenv
BOOTSTRAP_TEACHER_ENABLED=true
BOOTSTRAP_TEACHER_USERNAME=first_teacher
BOOTSTRAP_TEACHER_PASSWORD=your_own_password_at_least_12_characters
```

重启后可用该账号登录。随后关闭初始化并移除明文密码。已有同名教师不会被重置密码，同名学生会使初始化失败；新装数据库不提供默认密码。详见[项目启动指南](docs/项目启动指南.md)。

## 验证

```bash
./mvnw -B test
cd frontend && npm ci && npm test && npm run build
```

默认后端套件使用隔离的 H2 数据，不访问真实模型。MySQL 验证需要独立测试库：

```bash
# 在独立 MySQL 实例初始化 schema_init.sql 后执行；测试会写入数据
MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3306/zhixuewuli?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
MYSQL_TEST_USER=root MYSQL_TEST_PASSWORD=your_test_password \
./mvnw -B test -Dgroups=mysql -DexcludedGroups=external-api
```

CI 对 MySQL 8.4 分别执行新建库、旧版表结构升级两条路径。`src/test/resources/mysql/schema-v1.sql` 是可销毁的旧版测试夹具，**包含删库语句，只能用于独立测试实例**。

## 后端改进证据

冷持久化上下文下，25 条答题记录分别关联 25 道题的回归用例，旧列表查询执行 SQL 从 **27 条降到 2 条**（包含教师身份查询）。当前教师页面进一步使用有界分页和轻量投影。该结果来自 H2 SQL 计数，不能直接换算成生产吞吐量。

- [性能验证及复现方法](docs/性能验证.md)
- [简历表述与面试准备](docs/后端项目简历与面试.md)
- [实现记录与验证依据](docs/superpowers/implementation-ledger.md)

## 结构与边界

```text
src/main/java/com/zhixuewuli/
  controller/       HTTP API
  service/          业务规则、权限与会话管理
  repository/       JPA 查询
  common/           认证拦截器、流式客户端、异常处理
src/main/resources/sql/  新建库与增量升级 SQL
src/test/           单元、H2 集成及 MySQL 专项测试
frontend/           Vue 页面及实验演示资源
docs/               启动、部署、验证和面试说明
```

当前鉴权由 MVC 拦截器和服务层角色检查共同完成；使用 Spring Security 的密码编码等能力。AI 上游读取运行在有界工作线程中，属于阻塞 I/O；没有实现全链路非阻塞、自动续传、LRU 或 JWT 刷新机制。缓存、连接限制均为单进程范围。历史恢复仍需读取该会话的历史数据，超长会话需要进一步限制数据库读取量。负载、跨实例状态同步和外部模型可用性需要部署后单独验证。

仓库包含既有物理实验、视频和第三方资源，使用与再分发前请核对 [LICENSE](LICENSE) 及[开源组件清单](docs/开源组件清单.txt)。说明个人工作时，应区分原有教学功能和本次后端改进。
