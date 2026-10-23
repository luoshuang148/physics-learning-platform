# AI 学习助手 v2 环境基线

日期：2026-10-24（Task 1）  
状态：M0 基线已锁定；模型与向量效果**未验证**，不以本文件宣称任何准确率。

本文件只记录「当前机器/配置实际长什么样」，供后续任务与面试复现对照。
**「已配置」不等于「实际调用成功」**——真实模型调用结果只进 `v2-results.md`（Task 8）。

## 1. 运行环境快照

| 项 | 值 | 来源 |
|---|---|---|
| Java | OpenJDK 21.0.12.1（LTS） | `java -version` |
| 构建 | Maven Wrapper（`./mvnw`） | 仓库内 `mvnw` |
| 后端端口 | 8080 | `SERVER_PORT` / `.env` |
| 前端开发端口 | 5173（Vite） | `frontend/vite.config.ts` |
| 前端预览端口 | 4173（`vite preview`） | 已加入 CORS 允许列表 |
| 数据库 | MySQL，`DB_HOST:DB_PORT/DB_NAME`（本地 `.env`） | `spring.datasource` |
| 缓存/会话 | Redis，默认 `127.0.0.1:6379` | `REDIS_*` |
| 向量库 | Qdrant，默认 `http://localhost:6333` | `APP_KNOWLEDGE_QDRANT_URL` |
| 本地 Embedding | BGE-small-zh-v1.5-quantized，512 维 | `KnowledgeProperties` |

## 2. 配置键与占位映射（已补）

本轮补上了此前缺失的 YAML 占位映射，使 `.env` 形式键能正确绑定：

| 环境变量（.env） | Spring 配置路径 | 默认值 |
|---|---|---|
| `AI_ENABLED` | `ai.context.enabled` | `false`（dev）/ `true`（prod） |
| `APP_KNOWLEDGE_ENABLED` | `app.knowledge.enabled` | `false` |
| `APP_KNOWLEDGE_STORAGE_PATH` | `app.knowledge.storage-path` | `data/knowledge` |
| `APP_KNOWLEDGE_QDRANT_URL` | `app.knowledge.qdrant-url` | `http://localhost:6333` |
| `APP_KNOWLEDGE_QDRANT_API_KEY` | `app.knowledge.qdrant-api-key` | 空 |
| `APP_KNOWLEDGE_EMBEDDING_MODEL` | `app.knowledge.embedding-model` | `bge-small-zh-v1.5-quantized` |
| `APP_KNOWLEDGE_EMBEDDING_DIMENSIONS` | `app.knowledge.embedding-dimensions` | `512` |
| `APP_KNOWLEDGE_INDEX_WORKERS` | `app.knowledge.index-workers` | `2` |
| `APP_KNOWLEDGE_ALLOW_LEXICAL_FALLBACK` | `app.knowledge.allow-lexical-fallback` | `false` |
| `AI_AGENT_TOOL_CALLING_ENABLED` | `ai.agent.tool-calling-enabled` | `false` |
| `AI_AGENT_BASE_URL` / `AI_AGENT_API_KEY` / `AI_AGENT_MODEL` | `ai.agent.*` | 空 |
| `AI_LEARNING_ASSISTANT_MODE` | `ai.learning-assistant.mode` | `WORKFLOW` |
| `AI_LEARNING_ASSISTANT_*`（轮次/超时） | `ai.learning-assistant.*` | 6 轮 / 8 工具 / 60s / 25s / 5s |

敏感值（JWT 密钥、模型 API Key）只写本地 `.env`，不入库、不进日志。
`.env.dev.example` 保持「示例 + 空密钥」形态。

## 3. 能力状态口径

`GET /ai/capabilities` 只回答两件事：

1. **已配置**：开关与连接信息是否齐备（`agentReady`/`knowledgeReady`/`reports`/`tutoring`）。
2. **最近一次真实调用状态**：`reportStatus`/`tutoringStatus` 取自 `AIMetrics` 的最近记录；
   从未调用过则为 `UNKNOWN`，不会为检查状态额外调用付费模型。

绑定与诚实性由 `AiEnvironmentBindingTest` 保障：
- `.env` 形式键能绑定到对应 `@ConfigurationProperties`；
- 全部开关关闭时 `agentReady`/`knowledgeReady` 为 `false`；
- Tool Calling 缺 `base-url`/`model` 时即使开关打开也报 `agentReady=false`。

## 4. 只读环境检查

```bash
python3 scripts/verification/check_ai_environment.py        # 人类可读
python3 scripts/verification/check_ai_environment.py --json # 机器可读
```

脚本只做 TCP/HTTP 连通与开关读取，**不输出密钥、不调用模型**。
退出码非 0 仅表示存在 `error` 级问题（如找不到 Java）。

2026-10-24 实测输出摘要：Java/MySQL/Redis/Qdrant 均可达；Agent 连接信息未填（`AI_AGENT_TOOL_CALLING_ENABLED=false`）；真实模型调用与题库索引状态均为「未验证」。

## 5. 已知基线缺口（留给后续任务）

| 缺口 | 归属任务 |
|---|---|
| `app.knowledge` 此前无 YAML 映射（本轮已补） | Task 1 ✅ |
| Agent Tool Calling 未配置真实供应商，`agentReady=false` | Task 5 |
| 题库 `ai_practice_enabled` / 索引任务表尚未存在 | Task 2 |
| 普通问答未接题库 RAG | Task 4 |
| 评测仍为 corpus-v1（讲义向），未迁移到题库场景 | Task 8 |
| 页面刷新后对话历史未完整恢复 | Task 4 |

## 6. 复现（开发机）

```bash
cp .env.dev.example .env   # 填本地 DB/密钥
./mvnw spring-boot:run     # 后端 :8080
npm --prefix frontend run dev   # 前端 :5173
python3 scripts/verification/check_ai_environment.py
```

依赖真实 MySQL/Qdrant 的集成测试单列；未执行项不得记为通过。
启动方式如有变更，只改本文件与 `README.md`，不把本机路径/SSH 隧道写成硬依赖。
