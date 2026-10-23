# AI 应用升级基线（T0）

日期：2026-10-23，Asia/Shanghai。实现分支：codex/ai-application-upgrade；功能基线：6583cbd。

## 已验证

- 默认评测路径修正为 src/test/resources/ai-evaluation/cases.jsonl，旧的 10 条样例未更改。
- 新增回归测试先出现 2 项失败，修复后 Python discovery 共 3 项通过。
- 默认 CLI 无密钥、无网络调用即可生成 10 条 prepared 样例；保留 pending 人工评分状态。
- 输出增加代码 SHA、语料内容 SHA-256 版本、baseline 模式，继续保留 Prompt 版本、输入哈希与时间戳。
- 本地结果存放于 git 忽略的 .local/ai-evaluation/，输出文件权限 0600。
- 前端 npm ci 成功；npm test 10/10 通过；npm run build 成功。
- shell 默认 Java 为 11，后端检查显式使用已安装 Homebrew JDK 21.0.12.1。

## 后端检查

首次运行 ./mvnw -B test：978 项，0 assertion failures，414 errors。错误源于 Testcontainers 找不到 Docker，相关测试类静态初始化失败，包括 QuestionRepositoryIntegrationTest 与后续依赖 RedisTestSupport 的控制器测试；不是 Python 变更导致。

现有停止状态的 physics-memory-smoke Colima 测试虚拟机已启动。仅本次进程指定 DOCKER_HOST / TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE，不改默认远程 context。完整后端重跑结果：978 项测试全部通过，0 failures、0 errors、0 skipped，耗时约 2 分 59 秒。原始失败与环境修复记录保留，未修改测试断言或业务代码。

## 实际模型能力检查

仅发送合成内容，2 次请求；凭证从原项目本地环境文件安全读取，未复制或打印。

- glm-4-flash-250414 工具调用成功，返回 lookup_fixture(value=2)，上游报告 120 tokens。
- embedding-3 返回 HTTP 429、provider code 1113；本次未得到向量，不能视为通过。
- 向量与聊天能力分别验证；不会将普通聊天成功推定为 Embedding 可用。T0a 将验证本地中文 Embedding 方案，失败云接口不自动重试。

## 可复跑命令

python3 -m unittest discover -s scripts/verification -p 'test_*.py'

python3 scripts/verification/ai_evaluation.py --model mock-no-network --output .local/ai-evaluation/new-run.jsonl

前端：在 frontend 运行 npm ci、npm test、npm run build。

后端：选择本地可用 Docker 后显式使用 JDK 21；命令中的 Docker 地址按本机上下文配置，不连接线上数据库。原始测试输出留在本计划 .superpowers 工作目录，不提交包含环境细节的日志。
