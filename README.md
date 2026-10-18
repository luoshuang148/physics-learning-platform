# Physics Learning Platform

面向单个共享班级的物理教学学习平台，包含学生答题、错题整理、教师任务管理、AI 流式辅导和学情报告。后端采用 Java 21 / Spring Boot 3.3.8 / Spring Data JPA / MySQL，前端采用 Vue 3 / TypeScript / Vite。

项目适合作为后端实习作品展示：重点是权限边界、会话隔离、流式请求资源管理、数据库查询和数据生命周期。它是单体教学项目，目前没有生产流量或分布式部署的性能证明。

## 已实现

- 学生注册、JWT 登录、BCrypt 密码校验、登录限流；登录只接受唯一用户名，真实姓名不再作为登录凭证；退出或改密即时撤销已有令牌；公开注册仅允许学生，教师由运营初始化或已有教师创建。
- 教师管理题目并发布纯题目任务，填写任务名后从题库选择题目；结束任务会归档，保留答题、对话和报告。任务报告按任务 ID 取数，任务切换会取消旧流并忽略晚到响应。
- 单班内允许并行发布多个作业，任务状态为草稿/已发布/已截止/已归档，并支持开始与截止时间；任务发布保存有序题目快照，旧页面操作通过预期任务 ID 和状态校验保护。
- 作答绑定任务、题目与幂等请求 ID，重试不会重复记分；原始成绩与补练分开，错题本按最后一次尝试显示掌握状态。题库改动不影响新保存的历史内容，历史图片按内容哈希共享、不随题库修改。
- 教师答题记录和学生的历史/错题列表使用数据库分页，列表投影不传输图片和完整解析，详情按记录权限加载；最近会话按真实作答时间与 `attemptOrder` 选取。
- 任务进度统一按当前启用学生统计；答案开放规则集中判断，活动任务首次作答前不返回答案和解析；任务首次提交使用共享行锁，归档/替换保持排他锁。
- 学生端保留独立的 3D 实验入口，可自由切换原子物理和宇宙航天实验，不绑定教师任务，也不包含过渡视频。
- 使用 OkHttp 对接兼容 OpenAI 格式的模型接口，通过 Spring MVC `SseEmitter` 向浏览器推送内容。
- 对话缓存按用户、任务和业务类型隔离，只恢复最近的有界窗口，通过数据库聚合保留累计 token 与序号；持久化失败明确反馈。
- AI 输出统一净化 HTML 并保留数学公式；移除调试账户入口和二级令牌缓存。报告使用匿名标签、题目快照及服务端统计，记录模型、生成时间与输入摘要。
- 教师总览使用数据库聚合；公开健康检查不含细节，运行指标仅教师可访问。
- SSE 请求独立取消：断开或超时会取消上游 HTTP 调用；同一会话只允许一个活动请求，并限制全局、用户连接数及执行队列。
- Maven Wrapper、JUnit、Playwright 与 GitHub Actions 覆盖后端、前端、MySQL 全新/升级路径、Docker 启动、浏览器主流程及备份恢复。

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

CI 对 MySQL 8.4 分别执行全新库和旧版 V2→V3 升级，包含 MIGRATED/MISSING 旧题及历史记录保留检查。`src/test/resources/mysql/schema-v1.sql` 是可销毁的旧版测试夹具，**包含删库语句，只能用于独立测试实例**。

## 后端改进证据

冷持久化上下文下，25 条答题记录分别关联 25 道题的回归用例，旧列表查询执行 SQL 从 **27 条降到 2 条**（包含教师身份查询）。当前教师页面进一步使用有界分页和轻量投影。该结果来自 H2 SQL 计数，不能直接换算成生产吞吐量。

- [性能验证及复现方法](docs/性能验证.md)
- [简历表述与面试准备](docs/后端项目简历与面试.md)
- [本轮产品与后端改进记录](docs/superpowers/product-hardening-ledger.md)
- [浏览器、备份与实测复现](docs/交付验证.md)
- [AI 评测样例与人工评分方法](docs/ai-evaluation/README.md)
- [首轮实现记录](docs/superpowers/implementation-ledger.md)

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

当前业务 API 由 MVC 拦截器和服务层角色检查鉴权，Actuator 由独立的 Spring Security 过滤链验证令牌与教师角色。AI 上游读取运行在有界工作线程中，属于阻塞 I/O；没有实现全链路非阻塞、自动续传、LRU 或 JWT 刷新机制。缓存、连接限制均为单进程范围。历史上下文只读取最近 N 条，累计值另做聚合；报告采用文字投影，输入上限 5000 次作答、单字段 4096 字符及 128 KiB UTF-8；超限拒绝生成。教师默认只分析当前任务；成功报告按次保留，最新查询具有确定顺序。本地合成数据的 HTTP 实测不能代表生产容量；跨实例状态同步和外部模型可用性需单独验证。历史无快照的记录只可回退当前题库，报告会标记这一局限；固定 AI 评测样例尚待教师评分。

仓库包含既有物理实验、视频和第三方资源，使用与再分发前请核对 [LICENSE](LICENSE) 及[开源组件清单](docs/开源组件清单.txt)。说明个人工作时，应区分原有教学功能和本次后端改进。
