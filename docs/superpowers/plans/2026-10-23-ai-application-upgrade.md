# 教学平台 AI 应用能力升级 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. 本次按用户要求在隔离 worktree 中逐项实施；进度与验证证据记录在被忽略的 `.superpowers/sdd/2026-10-23-ai-application-upgrade/progress.md`。

**Goal:** 在现有教学平台中交付“有资料依据的错题辅导、能调用业务工具的复习助手、可复现的效果评测”，形成可用于 AI 应用开发岗位投递与技术面试的完整项目。

**Architecture:** 保留 Java 单体、Vue 前端与现有作业/错题/报告链路。新增班级知识库、检索增强、受限工具调用与运行记录；MySQL 是数据和权限的权威来源，Qdrant 保存可重建向量索引。Python 负责离线评测和实验分析，不重写业务后端。

**Tech Stack:** Java 21、Spring Boot 3.3、Vue 3、TypeScript、MySQL 8、Redis、现有 RabbitMQ、现有 OpenAI 兼容模型接口；拟新增 PDFBox 3.x、Qdrant 与独立配置的 Embedding 接口。Java AI 接入候选为现有 HTTP 方式与 LangChain4j，Spring AI 仅在兼容现有基线时列入候选；T0a 比较后只选一种新模块接入方式。新增依赖具体版本在 T0/T0a 验证后锁定，不使用浮动 latest 镜像。

**Spec:** 本文件第 1–8 节是本计划的设计基线；第 9 节将其拆成任务，第 10–14 节规定验证与交付。用户已要求按计划开始实施，功能状态以任务勾选、提交记录及进度账本中的验证证据为准。

**编写日期 / 时区：** 2026-10-23，Asia/Shanghai。

**实施决策（T0a）：** G0 技术链路已通过；新模块采用 LangChain4j 1.22.0，使用 512 维本地中文 BGE（1.22.0-beta32）。云 Embedding 429/1113 未视为通过。内容人工/教师评分仍为 pending；已发现的摩擦与动能过度推断保留为反例。详见 docs/ai-evaluation/g0-results.md。

**修订记录：** 2026-10-23，v1.1：增加最小 RAG 验证与 Java 接入方案比较；先建立固定工作流再引入 Agent；补开源参考与能力判定标准；更新任务依赖和工时。业务目标及原有接口兼容要求不变。

**代码基线：** 6583cbd。编写时工作区无未提交修改。以下新类、表、端点均为拟议设计，不能当作已经实现的功能。

## Global Constraints

- 保留现有 Java 21、Spring Boot 3.3、Vue 3、MySQL 8 主体架构，不整体换语言或拆微服务。
- API 继续使用 /api/v1 上下文路径；新增 Controller 的映射不重复拼接该前缀。
- 不修改已经发布的 Flyway 迁移；新迁移号执行前检查冲突，以下 V12–V15 是基于当前 V11 的预分配。
- 知识库按班级授权；学生只访问本人作答与复习计划；工具参数中的身份不得覆盖认证身份。
- 保留作业题目快照、首次作答与补练区分、提交幂等、答案可见性和历史会话隔离语义。
- 新功能默认关闭；缺少向量服务、Embedding 配置或工具模型时，基础教学功能仍可启动和使用。
- 真实模型调用只在显式选择的评测/外部测试中执行；普通 CI 使用本地样例与模拟上游。
- 评测答案与评分标准不得混入模型输入；开发集和留出集分离；结果标注数据来源和样本量。
- 日志不输出 API 密钥、原始图片或完整学生对话；模型执行记录展示步骤摘要，不展示内部推理过程。
- 本文数值指标是待验证目标，不是现有成绩；本地样例和并发实验不能写成生产使用规模。
- 使用外部模型 API 是本方案的基础。RAG 必须按问题检索外部资料再生成；Agent 必须基于工具结果动态决策；普通问答、固定数据库查询或固定步骤不得冒充这两种能力。
- 先通过 T0a 最小 RAG 检查点，再扩充完整知识库；先实现共享工具上的固定工作流，再与模型动态调用对照。失败时回到对应数据/接口问题，不继续堆界面和编排。

## Review Focus

1. 班级调整、文档撤回、索引尚未删除时，检索、引用原文和缓存均重新鉴权；由 T2/T4/T6 测试覆盖。
2. PDF 没有文字、公式跨行或图片缺失时，明确说明资料不足，避免生成貌似有出处的结论；由 T3/T6 测试覆盖。
3. 文档重建、Embedding 模型变更和进程重启后，旧新向量不能混用，已发布版本可继续读；由 T4 覆盖。
4. 模型返回非法工具、重复工具调用、畸形参数、超时或用户取消时，执行有界且不产生重复计划；由 T10/T11/T12 覆盖。
5. 新 SSE 事件被旧前端误当正文、历史回答丢引用、答案通过练习推荐泄露时，保留兼容性和原有保护；由 T7/T9/T13 覆盖。

## 1. 项目定位与范围

建议对外定位：**面向物理学习的 AI 辅导与个性化复习平台**。保留原项目名称也可以，README 首屏突出已实现的 AI 能力和实验结果。

主场景：学生完成作业 → 打开错题 → AI 基于题目历史快照和教师讲义解释 → 查看依据 → 请求三天复习建议 → 助手查询本人学习记录并挑选练习 → 学生确认保存 → 补练后查看变化。

### 1.1 验证检查点与两级交付

在两个交付版本之前新增 **G0 最小 RAG 检查点**：在隔离实验目录中，用一份人工核对的短讲义和 10 个开发问题，跑通“切分→Embedding→检索→调用模型→展示真实来源”。不先建设完整上传后台、全量评测集或复杂 Agent。该检查点是学习与选型验证，不是可部署的业务功能。

| 版本 | 必须交付 | 对应任务 | 投递价值 |
|---|---|---|---|
| M1 可投递版本 | G0 通过后建设可管理的知识库、带来源的错题辅导、固定数据集、基线对比、演示文档 | T0、T0a、T1–T8 | 展示 RAG、业务集成、权限与效果验证，完成即可开始投递 |
| M2 完整增强版本 | 知识点标签、共享业务工具、固定工作流、Function Calling Agent、复习计划、补练反馈、完整实验 | T9–T16 | 增加受控执行、结构化输出与工作流/Agent 对照 |

M1 验收后形成独立版本。不要等全部增强项完成才投简历。M2 也不以所有优化实验都取得正向结果为前提。

### 1.2 第一版内容范围

- 聚焦高中物理“牛顿运动定律”“功与机械能”两个主题，这是规划建议，内容整理时核对实际题目难度与学段。
- 教师人工核对 10–15 个知识点、6–10 份短讲义、40–60 道可用于推荐的题；复用题库中合适的题，不为达到数量重复造题。
- 支持 UTF-8 Markdown、TXT、含文本层 PDF；上传限制 10 MiB、PDF 不超过 100 页、单班活动文档不超过 50 份。
- 首版不做 OCR、复杂表格/电路图识别、自动生成新题、自动判主观题、学习效果因果推断。
- 不做模型微调、自建 GPU 推理、多 Agent、通用 MCP 平台、Kubernetes 或跨实例会话路由；只有目标岗位与测量结果支持时再立项。

## 2. 已有实现与需要复用的资产

| 当前模块 | 已看到的实现 | 本次处理 |
|---|---|---|
| AIController / ZhiPuAIAssistantServiceImpl | 问答、错题分析、图片输入、SSE、历史会话 | 增加可选 RAG 路径，保留现有入口和旧行为 |
| SessionManager / StreamUtils | 会话隔离、历史、配额、取消与资源回收 | 新路径复用；取消必须传递到检索、Embedding、模型和工具 |
| ReportPreparation | 题目快照、确定性统计、证据摘要、Prompt 版本与输入哈希 | 作为新报告/计划的事实来源设计参考；不能让模型重算成绩 |
| AnswerAccessPolicy / ClassAccessService | 题目答案保护、班级与教师关系 | 新工具、检索和引用接口必须复用相同权限语义 |
| Question | 内容、选项、答案、解析、难度、图片 | 目前未看到结构化知识点标签，M2 前补齐 |
| AIMetrics | 上游耗时、首段内容耗时、成功/错误/取消 | 补应用全链路阶段、模型、Token 来源、费用估算 |
| frontend/src/api/ai.ts | SSE 解析，已有 evidence 回调 | 扩展类型与事件分派，避免引入第二套流解析器 |
| scripts/verification/ai_evaluation.py | 最多 10 条样例采集、usage、耗时、人工待评标记 | 保留采集模式，增加经过平台真实链路的评测模式 |
| src/test/resources/ai-evaluation/cases.jsonl | 10 条固定样例，Java 测试断言数量为 10 | 保留原文件；新语料独立版本化，不破坏旧合同 |
| GitHub Actions / Flyway / MySQL 测试 | 既有后端、前端、迁移与消息链路验证 | 增量加入新模块，外部收费 API 不加入普通流水线 |

已发现的前置修正：评测脚本默认读取 docs/ai-evaluation/cases.jsonl，而当前样例在 src/test/resources/ai-evaluation/cases.jsonl。T0 通过测试修正默认路径，并保持 --cases 可覆盖。

## 3. 设计选择与模块边界

已考虑三条路线：在现有单体内增量建设、另建 Python AI 服务并接回平台、另建独立 AI 项目。选择第一条，原因是权限/题库/作答/会话都可复用，交付链路更短。Python 用于评测；若后续出现独立算力或任务调度需求，再考虑服务拆分。

| 能力 | 建议方案 | 原因与约束 |
|---|---|---|
| 资料提取 | Markdown/TXT 本地提取；文本 PDF 用 PDFBox | 与 Java 后端一致；保留 PDF 页码，扫描件返回明确失败原因 |
| 词法检索 | MySQL FULLTEXT ngram | 复用已有数据库，支持中文；必须在真实 MySQL 测试，不用 H2 假装验证 |
| 语义检索 | 单个 Qdrant 服务，通过端口接口访问 | 新增一个可选服务；索引可从 MySQL/原始文件重建 |
| Embedding | 单独的模型/端点/维度配置 | 不假定聊天模型或其密钥必然支持向量接口 |
| 融合 | 双路各取 10，RRF 常数 60，最终最多 5 段 | 先提供可复现的默认值；阈值与参数只在开发集校准 |
| 重排序 | M2 后的可选实验 | 只有正确率收益值得额外延迟与费用时才保留 |
| 复习工作流与 Agent | 共享工具的固定工作流先落地，再增加 Java 有界单助手循环 | 权限、状态和取消由业务层负责；动态决策带来的收益须与固定工作流比较 |
| 模型接入 | 旧客户端保留，新模块经端口接入；T0a 对比直接 HTTP 与 LangChain4j | 不强制重写协议，不同时引入多个 AI 框架；只做选型所需的最小验证 |
| 计划数据 | 服务端校验后存 MySQL | 模型只提议，题目 ID、权限、数量与日期由代码校验 |

Qdrant 只返回候选 ID/版本/分数；原文从 MySQL 回读并重新授权，再进入模型上下文。索引携带 classId 与 revisionId 过滤，不能替代数据库鉴权。缓存键至少包含班级、文档活动版本与 Embedding 版本；首版不缓存最终个性化回答。

### 3.1 Java 接入选型检查点

T0a 必须产出 dependency-decisions.md 中的同题对照：相同模型端点、相同资料和 Embedding，分别用现有 HTTP 方式与一个 Java AI 库跑最小检索问答；工具部分用本地模拟工具返回固定结果，检查模型是否能提出并接收工具调用。优先考察 LangChain4j 的核心模块，避免为自动配置先升级 Spring Boot。若候选库不兼容，记录依赖树与原因，直接 HTTP 仍为可行结果。

选择标准是能否保留原流式问答、取消能否传递、来源能否保留、工具消息能否正确往返、依赖是否兼容，以及适配代码与维护成本。框架和 HTTP 是接入实现选项，不是评测中的质量对照组；切换框架本身不能宣称回答更准确。

最终只保留一种新模块适配方式。本文列出的 EmbeddingGateway、KnowledgeVectorStore、ToolChatGateway 仍作为业务边界；可委托所选库实现，也可用已有 HTTP 客户端实现。学生身份、班级授权、历史快照、计划校验与评测不交给框架隐式决定。T0a 记录最终适配类和文件清单，T4/T10 按该决策实施。

## 4. 数据与文件结构

以下路径相对仓库根目录；新增 Java 类沿用 com.zhixuewuli 包，按当前 entity / repository / service / controller 风格组织。

### 4.1 迁移与数据合同

| 迁移 | 新增数据 | 核心字段 / 不变量 |
|---|---|---|
| V12__knowledge_library.sql | knowledge_documents、knowledge_revisions、knowledge_chunks | 文档 class_id、owner_id、active_revision_id；版本 hash、状态、extractor_version、embedding_model、dimensions；chunk 的 ordinal、page、section、text、hash；版本切换原子完成 |
| V13__ai_run_evidence.sql | ai_runs、ai_run_steps、ai_message_sources；conversation_history 可空 ai_run_id | run 的 user/session/event/mode/status、execution_mode（WORKFLOW/AGENT；辅导为 null）、model/prompt/indexVersion、inputHash、usage、usageSource、stageDurations；来源绑定 chunk/revision；旧会话无 run 仍可读 |
| V14__question_knowledge_points.sql | knowledge_points、question_knowledge_points；任务题目和作答快照的标签字段 | code 唯一；一个题目可有多个点；旧快照缺标签时标为未知，不倒灌当前标签冒充历史事实 |
| V15__learning_plans.sql | learning_plans、learning_plan_items | user_id、event_id、sourceAnswerRevision、status、request_id、version；唯一(user_id, request_id)；item 保存题目快照和知识点依据 |

文档状态：UPLOADED → INDEXING → READY；失败为 FAILED，可显式重试。新版本 READY 后才替换 active_revision_id；撤回后所有检索与原文接口立即不可读，后台索引清理由重试任务完成。

复习计划状态：DRAFT → ACTIVE → COMPLETED / CANCELLED。模型生成的建议先展示，只有用户保存请求才创建计划；进度来自平台作答记录，不能由模型自行宣布完成。

### 4.2 新模块文件布局

- service/knowledge/：KnowledgeDocumentService、KnowledgeDocumentStore、KnowledgeAccessPolicy、KnowledgeTextExtractor、KnowledgeChunker、KnowledgeIndexService、KnowledgeIndexRecovery；负责上传、提取、版本、索引和撤回。
- service/knowledge/search/：EmbeddingGateway、KnowledgeVectorStore、QdrantKnowledgeVectorStore、KeywordKnowledgeSearch、KnowledgeRetriever、RetrievalPolicy；负责可替换外部接口、词法/向量检索和融合。
- service/ai/rag/：RagAnswerService、RagPromptBuilder、CitationValidator；负责证据上下文、引用映射和错题集成。
- service/ai/run/：AiRunRecorder、AiUsageAccounting；负责运行记录和用量，不承载业务编排。
- service/ai/agent/：ToolChatGateway、T0a 选定的模型适配类、LearningToolRegistry、LearningWorkflowService、LearningAgentService、LearningPlanValidator；负责共享工具、固定工作流、动态循环与结构化校验。
- service/learningplan/：LearningPlanService、PracticeRecommendationService；负责计划生命周期与练习筛选。
- controller/：KnowledgeController、LearningAssistantController、LearningPlanController；既有 AIController 与 AICapabilityController 做兼容扩展。
- dto/knowledge/、dto/agent/、dto/learningplan/：新增记录类型、请求与响应；每个顶层类型独立文件。
- common/config/properties/：KnowledgeProperties、AgentProperties；新增开关与限制不塞入已有大配置类。
- frontend/src/components/：KnowledgeSources.vue、AgentExecutionSteps.vue、LearningPlanCard.vue。
- frontend/src/views/teacher/TeacherKnowledgeView.vue；学生仍从错题页、StudentAIView 进入，避免新增多个相似聊天页面。
- frontend/src/api/knowledge.ts、learning-plan.ts；frontend/src/types/knowledge.ts、learning-plan.ts；扩展已有 api/ai.ts 与 types/ai.ts。
- scripts/verification/：扩展 ai_evaluation.py；新增 evaluate_platform.py、score_ai_evaluation.py、compare_ai_evaluation.py 及对应 unittest。
- docs/ai-evaluation/：rubric.md、experiments.md、corpus-v1/、匿名化实验报告；原始模型输出默认位于 .local/ai-evaluation/ 并忽略提交。

## 5. 接口与流协议

### 5.1 主要 HTTP 接口

| 接口（含完整前缀） | 入参 / 输出 | 权限与行为 |
|---|---|---|
| POST /api/v1/knowledge/documents | multipart file、classId、title → documentId、revisionId、status | 教师且有班级关系；持久化后返回 202，后台索引 |
| GET /api/v1/knowledge/documents?classId=… | 分页列表及索引状态 | 该班教师；每页最多 50 |
| POST /api/v1/knowledge/documents/{id}/reindex | → revisionId、status | 该班教师；同版本处理中返回既有任务，防止重复收费 |
| DELETE /api/v1/knowledge/documents/{id} | → 204 | 该班教师；先撤回可见性，再清索引 |
| GET /api/v1/knowledge/chunks/{id} | → title、revisionId、page、section、text | 班级权限、文档状态和当前访问资格重新验证；不返回本地路径 |
| POST /api/v1/ai/student/wrong-question-analysis | 原 AIAskRequest + 可选 knowledgeMode=OFF/AUTO | 默认 OFF 保持兼容；AUTO 才检索；所属作答和历史快照仍为入口 |
| POST /api/v1/ai/student/learning-plan/preview | eventId、days=1..7、question、requestId → SSE | 学生本人；生成结构化建议，不自动写作业/计划 |
| POST /api/v1/learning-plans | runId、requestId → 已校验的 DRAFT 计划 | 只保存本人成功运行的服务端结果；不接受客户端任意题目列表 |
| POST /api/v1/learning-plans/{id}/activate | expectedVersion → ACTIVE | 本人；重复激活幂等，源作答变化时返回可理解的过期提示 |
| GET /api/v1/learning-plans/{id} | 计划、题目卡、练习进度及证据 | 本人；保护字段继续由 AnswerAccessPolicy 决定 |
| GET /api/v1/ai/runs/{id} | 来源、步骤摘要、用量与阶段耗时 | 本人；不返回原始工具参数里的身份数据和内部推理 |

资料第一版限定每文档属于一个班级；跨班共享以后通过明确授权模型扩展，不隐式向所有学生公开。

### 5.2 SSE 合同

复用原 message/content/progress/complete/error/evidence；新增 sources、tool_step、plan。每个结构化事件带 runId 和单调 seq，前端按 runId 忽略旧请求迟到事件。

- sources：本次可引用的 [{sourceId, chunkId, revisionId, title, page, section}]，不向前端传本地文件路径。
- tool_step：{step, toolName, status, summary}；summary 是“读取了本次作业的 3 道错题”一类结果摘要。
- plan：{planPreview, evidenceSummary}，必须通过 LearningPlanValidator 后才发送。
- complete：保留旧字段，增量携带 runId、usage、usageSource、status；终态最多发送一次。
- error：明确区分资料不可用、证据不足、上游不可用、工具参数错误和超限；不伪装成正常答案。

新增事件不得走现有未知事件的正文兜底。RAG 回答用 [S1] 等标记；CitationValidator 只校验来源 ID 有效性，不能宣称自动证明句子正确。流式正文在结束前标为生成中，终态前必须验证引用；无效来源标记不得变成可点击链接。真正“引用是否支持结论”进入人工评分。

## 6. Agent 工具与执行边界

先用 LearningWorkflowService 固定调用“个人统计→错题→相关资料→候选练习”，建立可回归的业务基线；随后 LearningAgentService 让模型根据请求和工具结果选择下一步。两者使用同一工具注册表、权限检查、候选题、结果校验和评测任务。run 记录 executionMode=WORKFLOW/AGENT，界面和简历也使用真实模式名称。

Agent 判定测试至少包括：只询问知识时不强制查询学情；工具返回无错题时调整建议而非继续执行固定选题序列；资料不足时修改查询或说明缺口；出现错误时在预算内修正或终止。如果只有固定顺序加一次模型总结，应按工作流交付，不标注动态 Agent。即使没有正向收益，也保留对照结果，不因求职展示强行让所有请求走 Agent。

ToolChatGateway 的输入为结构化 ChatTurn、ToolDefinition、模型配置、CancellationToken；输出为正文或 ToolCall 列表及 usage。ToolDefinition 使用 JSON Schema 约束参数；工具结果使用单独的 tool role 回传，不拼成伪造用户输入。

| 工具名 | 模型可填参数 | 服务端事实与输出 |
|---|---|---|
| get_learning_summary | eventId | 从认证用户查询原始作答/补练数量和作答版本；不允许 userId |
| get_wrong_questions | eventId、limit=1..10 | 本人错题及历史题目快照、已标注知识点；不返回其他学生数据 |
| search_knowledge | query、limit=1..5 | 从服务端确定的当前班级/任务范围检索，返回来源 ID |
| recommend_practice | knowledgePointCodes、limit=1..6 | 服务端筛选真实题库，过滤不可练题、去重、应用答案保护 |

get_learning_summary 不能直接使用现有面向任务整体汇总的 summary(eventId) 作为学生个人统计，必须新增按 viewerId/eventId 约束的查询。

默认最多 6 轮模型交互、8 次工具调用、60 秒总时限；单模型请求超时 25 秒、只读工具 5 秒，实际调用不得超出剩余总时限。允许一次参数修正，计入上述总额；无工具循环和重复相同调用要停止。重试只用于有界的临时失败，不对已发送正文偷偷重放。

计划结构：days、sourceEventId、sourceAnswerRevision、items[{day, knowledgePointCode, questionIds, reasonSourceIds}]。限制 1–7 天、总计最多 6 道不同题；只允许工具实际返回的 ID。知识点不完整或候选不足时返回可解释的部分建议，不编造题目，不强行满足数量。

## 7. 评测设计与验收规则

### 7.1 数据集

保留既有 10 条样例。新增 corpus-v1 的 100 条主评测案例：概念/计算 40、错题解释 20、资料不足/缺图 15、指令干扰 10、多轮追问 15；分层固定为开发集 60、留出集 40。另建 20 个工具任务案例，评测任务完成、非法调用与失败恢复。

每个主案例含 id、category、split、question、allowedContext、expectedSourceIds、referenceAnswer、rubric、reviewStatus、reviewer、corpusVersion。评分字段由 runner 剥离；题库正常业务中可见的参考解析与评测标准答案是不同数据来源，分别标记。检索样例指定原文版本及可替代证据集合，不依赖易变数据库自增 ID。

标签与参考答案先人工复核；无法获得有物理基础的复核者时，结果标为“开发者核对”，不宣称教师权威评分。留出集不参与阈值、Prompt 和模型选择；查看失败后继续优化，必须升级语料版本并换新的留出集。

### 7.2 对照组

| 组别 | 内容 | 用途 |
|---|---|---|
| A | 当前相同题目上下文，关闭知识库 | 基线 |
| B | 同模型、同 Prompt 骨架，纯向量检索 | 区分检索收益 |
| C | 同模型，词法 + 向量 RRF | 验证混合检索 |
| D，可选 | C 加重排序 | 验证额外成本是否值得 |
| E，M2 | T11 已实现的固定工作流与 Agent 使用相同工具/校验/任务，各跑 20 案例 | 比较任务完成、额外轮数、延迟和费用；不预设 Agent 更好 |

模型对比先选 2 个确实支持所需接口的候选，名称与价格按实验当日官方配置记录，不能照搬配置中的旧默认型号。开发集上选配置，M1 留出集只评 A 与最终选择版本。实验记录代码 SHA、Prompt/资料/Embedding/索引版本、参数、运行时间、环境和重复次数。

### 7.3 指标

| 指标 | 定义 | 建议目标 / 验收方式 |
|---|---|---|
| Recall@5 | 有相关证据案例中，前五段覆盖的标注相关证据比例；同义证据用等价组计 | 开发目标 ≥ 0.85；报告样本数和分组结果 |
| 回答通过率 | 人工 rubric 满足关键条件且无关键物理错误的案例比例 | 留出目标 ≥ 0.85；报告原始分子/分母 |
| 引用有效率 | 返回来源存在、属于本次证据且访问授权有效的标记比例 | 自动合同测试全部通过；不能替代语义评分 |
| 引用支持率 | 人工核对“引用原文能支持关联结论”的比例 | 目标 ≥ 0.90；逐条保留评分记录 |
| 资料不足处理率 | 不足类案例中明确指出缺口且不捏造资料/图示的比例 | 目标 ≥ 0.90，单列错误案例 |
| 工具任务完成率 | 20 案例中真实拿到所需数据、无越权、输出合法可用计划的比例 | M2 目标 ≥ 0.85；部分完成单列 |
| 性能与成本 | 检索耗时、应用端 TTFT、总时长、输入/输出 Token、估算费用 | 每组至少 50 次才报告 P95，并附样本数；不足只报原始分布 |
| 权限与取消 | 明确的跨班/跨用户/撤回/取消回归场景 | 所有规定的确定性测试通过；不写成普遍安全保证 |

质量目标未达成时如实展示结果和问题分析，先修关键物理错误与虚假引用。对新版本，留出回答通过率至少不低于 A；在此基础上证明引用可追溯。若混合检索不优于纯向量，保留更简单版本。性能目标为可测量、可解释，不承诺不受上游影响的固定秒数。

## 8. 时间、费用与阶段顺序

以下按一名熟悉现有项目的开发者估算，包含实现、测试、资料整理、复核和演示，不包含等待外部复核和服务器故障。修订后中心估算 192 人时（约 190h），合理波动约 ±20%。每周投入 25–30 小时约 7–8 周；每周 15 小时约 13 周。G0 后依据实测重估，不把它解释为固定交期。使用 AI 工具不自动等于这些时间全部缩短。

| 阶段 | 任务 | 中心估算 | 阶段交付 |
|---|---|---:|---|
| 基线、最小验证与数据 | T0、T0a、T1 | 24h | 可重复旧链路、最小 RAG、接入选型、已核对样例 |
| 知识库与检索 | T2–T5 | 40h | 可导入、可撤回、可重建、按班检索 |
| M1 辅导与实验 | T6–T8 | 32h | 带引用的错题辅导、对照报告、可投递版本 |
| 标签、工作流与工具执行 | T9–T11 | 34h | 共享工具、固定工作流及可动态调用的助手 |
| M2 计划与反馈 | T12–T13 | 24h | 保存/激活计划、补练进度、失效检测 |
| 观测、实验与展示 | T14–T16 | 38h | 完整指标、工具评测、演示和简历材料 |

G0 约 8h（另先完成 T0 的基线准备）；可投递闭环是 T0、T0a、T1–T8，约 96h。必须保留权限、引用与评测，压缩时优先减少资料数量、主题范围、重排序实验和界面美化。8h 是验证预算而非保证成功；失败时记录阻塞原因，再决定调整资料、端点或适配方式。

付费实验采用显式预算：每次运行通过 --max-requests 与 --max-estimated-cost-cny 限制；建议单轮上限 20 元、累计初始上限 100 元，均是预算建议而非费用承诺。上游缺失 usage 时标为 estimated / unknown，不记零费用；缺少价格或可靠预估时拒绝开启“费用上限模式”，可改用明确的请求数与输出上限。价格表含来源、币种、计量单位和核对日期。预算上限仅覆盖本 runner，不能约束账户中其他调用，账户侧额度另行设置。

本地增加 Qdrant 前测量内存；试用演示服务器资源不足时，M1 先在本地或录制环境演示，不能直接把重建索引压到在线演示进程。部署新服务必须经过隔离环境实测。

## 9. 按依赖顺序执行的任务

本节路径中的 Java 主代码根为 src/main/java/com/zhixuewuli/，测试根为 src/test/java/com/zhixuewuli/。除明确写“新增”外，先阅读再修改现有文件。每个任务完成后只提交本任务明确列出的变更，避免使用 git add .。开发任务遵循先写有意义的失败测试、实现、运行对应验证、复核差异、独立提交的顺序。

### T0：建立可复跑基线，修正评测入口（6h）

**依赖：** 无。**Files：** 修改 scripts/verification/ai_evaluation.py、test_ai_evaluation.py；新增 docs/ai-evaluation/baseline.md、dependency-decisions.md；按需修改 .gitignore。

**Interfaces：** 保留 prepare(case, model) 的现有输出合同；将默认样例定位封装为 default_cases_path() -> Path。新报告统一带 codeSha、corpusVersion、promptVersion、mode、model、capturedAt、reviewStatus。

- [x] 增加 test_default_cases_path_points_to_existing_repository_fixture，断言默认路径存在且指向既有 10 条样例；保留现有“不泄露 reference/rubric”测试。
- [x] 运行 python3 -m unittest discover -s scripts/verification -p 'test_ai_evaluation.py'，确认新测试在修正前失败；只修路径与元数据，不更改旧样例数量。
- [x] 在隔离数据库/本地 Redis 与 Docker 可用环境运行既有 ./mvnw -B test；在 frontend 运行 npm ci、npm test、npm run build。记录实际结果、失败原因、所需环境，不能将未运行项写成通过。
- [x] 核对并锁定 PDFBox/Qdrant 兼容版本、Embedding 接口能力和工具模型能力；验证聊天、Embedding、tools 是独立能力。dependency-decisions.md 记录来源、版本、维度、价格日期，不保存密钥。
- [x] 用原样例执行不联网采集，输出 .local/ai-evaluation/baseline-prepared.jsonl；复核差异后提交 fix: make AI evaluation baseline reproducible。

### T0a：最小 RAG 验证与 Java 接入选型（8h）

**依赖：** T0。**Files：** 新增 experiments/rag-spike/README.md、pom.xml、src/main/java/RagSpike.java、src/test/java/RagSpikeContractTest.java、小型 Markdown 讲义与 10 个开发问题；更新 docs/ai-evaluation/dependency-decisions.md。实验不修改产品路由、旧模型客户端或生产数据库。

**Interfaces：** RagSpike.run(question, document, adapter) -> {retrievedPassages, answer, sourceIds, usage, timings}；adapter 的 HTTP/框架两种实现只在实验中比较。无凭证时默认模拟上游；真实调用显式启用并使用既定预算。实验输出进入 .local/ai-evaluation/，不会直接成为正式业务能力。

- [x] 建立一份 AI 辅助初核、人工待审的短讲义、10 个带预期来源的开发问题，其中包含无答案问题；写问题检索到正确片段、无结果不造来源、最终上下文包含所选片段、工具往返参数不丢失的合同测试，先用模拟响应验证。
- [x] 跑通 HTTP 版“切分→Embedding→本地临时向量检索→模型回答→来源映射”；首版实验可用内存索引，不先部署 Qdrant 或搭上传管理。真实 Embedding 与回答的用量分别记录。
- [x] 用相同资料、模型和问题验证 LangChain4j 核心模块是否兼容 JDK 21/现有 Spring Boot 依赖树，检查工具调用往返与取消；若兼容检查失败，记录证据而非为此升级主应用。Spring AI 只有在不改变主基线时才作备选。
- [x] 在 experiments/rag-spike 运行 ./../../mvnw -B test；真实调用显式开启后检查 10 案例来源与回答，至少展示一条正确引用和一条资料不足。记录失败，不从这 10 个开发样例推导泛化准确率。
- [x] 在 dependency-decisions.md 选定唯一新模块接入方式、适配类名/路径、版本、取消办法、兼容性和选择理由；README 标记实验局限，复核后提交 chore: validate minimal RAG and select Java model adapter。G0 不通过则停在对应技术问题，不继续 T1/T2 的完整建设。

### T1：建立资料、标注与评测合同（10h）

**依赖：** T0a 的 G0 检查通过。**Files：** 新增 docs/ai-evaluation/rubric.md、experiments.md、corpus-v1/{documents,development.jsonl,holdout.jsonl,agent-tasks.jsonl,manifest.json}；新增 scripts/verification/test_evaluation_corpus.py。

**Interfaces：** manifest.json 定义 corpusVersion、文档逻辑 ID/哈希、caseId→split 映射和复核状态。模型输入只取允许字段，评分字段独立传给 scorer。

- [x] 写 test_splits_are_disjoint、test_references_resolve_to_versioned_documents、test_scoring_fields_are_not_model_context；分别断言 60/40 分层分割无交集、来源可解析、金标准不进入输入。
- [x] 先建立最小 10 案例开发批次验证合同，再整理完整 100+20 案例；运行 python3 -m unittest discover -s scripts/verification -p 'test_evaluation_corpus.py'。
- [ ] 为讲义建立章节与页码/小节锚点；公式人工核对。标注 relevant evidence 的等价集合、可回答性、关键错误与通过标准，保留复核人和日期。
- [x] 冻结留出集哈希；先仅用开发集做日常调试。案例注明自编/公开许可/脱敏来源，不使用真实未授权学生资料。
- [x] 检查完整语料数量、类别、许可与版本，提交 docs: add versioned physics AI evaluation corpus。


### T2：知识库数据模型、上传与授权（10h）

**依赖：** T0a 的 G0 检查通过；上传样例来自 T1。**Files：** 新增 V12 迁移、entity/KnowledgeDocument.java、KnowledgeRevision.java、KnowledgeChunk.java，对应 repository；service/knowledge/{KnowledgeDocumentService,KnowledgeDocumentStore,KnowledgeAccessPolicy}.java；controller/KnowledgeController.java；dto/knowledge/ 下上传/列表/来源合同；KnowledgeProperties.java。

**Interfaces：** upload(viewerId, classId, title, MultipartFile) -> DocumentStatus；withdraw(viewerId, documentId) -> void；requireReadable(viewerId, chunkId) -> KnowledgeChunkView。读取班级关系使用只读查询，不能通过 resolveForStudent 的自动补默认班级行为给学生隐式授权。

- [x] 新增 KnowledgeAuthorizationTest，覆盖非教师上传、外班教师管理、外班学生读 chunk、文档撤回、10 MiB 超限和文件伪装；断言未授权时无原文/标题泄露。
- [x] 运行 ./mvnw -B test -Dtest=KnowledgeAuthorizationTest -Dgroups=unit，先看到新增行为失败，再实现文件白名单、魔数/文本检查、随机存储名与班级授权。
- [x] 原始文件写到专用挂载目录，由 KnowledgeDocumentStore 管理，上传不会使用客户端文件名拼接路径；数据库保存逻辑 key/hash。文档业务删除先撤回，再清文件与索引。
- [x] 新增 KnowledgeSchemaMySqlTest，按 @Tag("mysql") 执行，验证空库/升级库迁移与中文 FULLTEXT ngram 查询；H2 单元测试只验证不依赖 ngram 的业务逻辑。
- [x] 运行该单测及 ./mvnw -B test -Dtest=KnowledgeSchemaMySqlTest -Dgroups=mysql，检查既有迁移测试兼容后提交 feat: add class-scoped knowledge documents。

### T3：资料提取、页码与切分（8h）

**依赖：** T2。**Files：** 新增 service/knowledge/KnowledgeTextExtractor.java、KnowledgeChunker.java；pom.xml 加锁定的 PDFBox 依赖；新增 service/knowledge/KnowledgeExtractionTest.java 和小型本地文本/PDF 测试夹具。

**Interfaces：** extract(path, mediaType) -> List<ExtractedSection>，Section 含 page（PDF 从 1 起）、heading、text；chunk(sections, policy) -> List<ChunkDraft>，含稳定 ordinal、hash 和来源锚点。

- [x] 写 extract_text_pdf_preserves_page_numbers、reject_scanned_or_encrypted_pdf、chunk_keeps_formula_and_heading；验证双页 PDF、无文字 PDF、加密文件、跨行公式与中文段落。
- [x] 运行 ./mvnw -B test -Dtest=KnowledgeExtractionTest -Dgroups=unit，先确认失败，再实现提取和可理解的失败状态；最终 10 项通过。
- [x] 按标题/段落优先切分，目标 600–900 字符，硬上限 1200，重叠最多 100；避免拆开短 LaTeX 块，超长单公式标为需教师修订。字符限制不称作精确 Token 限制。
- [x] PDF 无可用文本时报告 OCR_REQUIRED，不产出空向量；测试 100 页/200000 字符硬上限及配置覆盖限制。
- [x] 开发者逐份检查开发语料讲义公式与适用条件；教师复核仍 pending。重跑提取单测后提交 feat: extract physics knowledge with stable source anchors。

### T4：Embedding、向量索引与失败恢复（12h）

**依赖：** T3。**Files：** 新增 EmbeddingGateway、KnowledgeVectorStore、QdrantKnowledgeVectorStore、KnowledgeIndexService、KnowledgeIndexRecovery；修改 docker-compose.yml（可选 knowledge profile）、配置样例与 .github/workflows/ci.yml；新增 KnowledgeIndexTest、KnowledgeVectorIntegrationTest。

**Interfaces：** embed(texts, embeddingConfig, cancellation) -> EmbeddingBatch；upsert(indexVersion, points) / search(indexVersion, allowedScope, vector, limit) / deleteRevision(revisionId)。indexVersion 同时绑定模型、维度、提取/切分策略与 revision。外部适配按 T0a 决策实现，优先复用所选库；本任务重点是正式索引生命周期与业务隔离，不再重复造已有协议封装。

- [x] 写 duplicate_import_does_not_duplicate_chunks、dimension_mismatch_is_rejected、failed_revision_does_not_replace_active_revision、restart_recovers_expired_indexing_lease；网络用 WireMock，断言最多重试 3 次。
- [x] 单测先失败再实现：DB 持久化任务状态/租约，最多 2 个索引 worker，重复任务复用版本；Qdrant/Embedding 调用在短事务外，重启可恢复过期租约。
- [x] 新建按 revision/config hash 隔离的 collection；点数核对通过后原子发布 active_revision_id 与 index_version。向量查询携带 classId/revisionId 过滤；原文仍从 MySQL 回读授权。
- [x] @Tag("knowledge-integration") 用固定版本 Qdrant 容器验证 upsert/search/delete/重启持久化；新增 CI job。完整后端 1018 项通过；MySQL 迁移/租约 2 项通过；Qdrant+BGE 集成 3 项通过，无真实聊天 API 调用。
- [x] 记录本地 Qdrant 空载实测 18.4 MiB（512 MiB 限制）和重建流程；运行 KnowledgeIndexTest 后提交 feat: add recoverable versioned knowledge indexing。

### T5：词法、向量与混合检索（10h）

**依赖：** T1/T4。**Files：** 新增 KeywordKnowledgeSearch、KnowledgeRetriever、RetrievalPolicy 与 dto/knowledge/RetrievalResult.java；新增 KnowledgeRetrievalTest、KnowledgeKeywordMySqlTest；新增开发集检索实验输出。

**Interfaces：** retrieve(viewerId, eventId, query, RetrievalMode, cancellation) -> RetrievalResult(status, passages, indexVersion, timings)；状态为 MATCHED / INSUFFICIENT / UNAVAILABLE，不能混为一类。

- [x] 写 rrf_deduplicates_and_orders_candidates、scope_is_applied_to_both_branches、withdrawn_document_is_filtered_before_prompt；固定两路排名断言 RRF 常数 60、结果最多 5 段。
- [x] 单测失败后实现两路各最多 10 条、去重融合，保留每段原始排名/分数用于诊断；MySQL 中文检索在 KnowledgeKeywordMySqlTest 验证。
- [x] 查询和索引必须使用同一 Embedding 模型/维度。索引或 Embedding 失效返回 UNAVAILABLE；显式配置允许词法降级时记录 lexical_only，不能冒充 hybrid。
- [ ] 开发集分别跑 keyword/vector/hybrid；只在开发集校准低相关门槛，记录样本、门槛与误拒情况；RRF 分数是排名融合值，不当作“正确概率”。没有相关证据返回 INSUFFICIENT，不硬凑 5 条。
- [ ] 执行完整单测与 MySQL 检索测试，提交 feat: add measurable hybrid physics knowledge retrieval。

### T6：将证据与引用接入现有错题辅导（14h）

**依赖：** T5。**Files：** 新增 V13 迁移、AiRun/AiRunStep/AiMessageSource 实体与仓储；RagAnswerService、RagPromptBuilder、CitationValidator、AiRunRecorder；修改 AIAskRequest、ZhiPuAIAssistantServiceImpl、ConversationHistory 及相关会话读取/上下文构造；新增 RagAnswerContractTest、RagHistoryAccessTest。

**Interfaces：** streamWrongAnswer(request, viewerId, sessionId, emitter)；appendSources(runId, passages)；completeRun(runId, outcome, usage)。knowledgeMode=OFF 走旧路径，AUTO 走检索路径。run 的终态、引用与 history 绑定一起校验保存，失败不会留下成功标记。

- [x] 写 off_mode_preserves_existing_request_contract、rag_uses_owned_answer_snapshot、unknown_citation_is_not_clickable、no_evidence_does_not_invent_sources；输入包含“忽略系统指令”的讲义，验证它始终是待引用数据而非系统消息。
- [x] 单测失败后实现证据 Prompt，先做错题场景；参考答案仍来自该学生有权读的历史快照，成绩统计来自代码。图片不足时不推断图示。
- [x] 落库来源绑定与 Prompt/模型/输入哈希；新增 get history 时可选 sources/runId 字段。班级变更/资料撤回后，受限历史回答显示不可用占位，也不重新送入下一轮 Prompt。
- [x] 取消从 SSE 向下传递到检索、Embedding 和上游请求；索引不可用明确提示，保留用户切回普通问答的选择。AI_ENABLED=false 不依赖 Qdrant 启动。
- [x] 运行全量后端分组回归与隔离 MySQL schema 迁移/检索验证，复核新旧流回归后提交 feat: ground wrong-question tutoring in authorized sources。

### T7：知识库管理和引用交互（10h）

**依赖：** T6。**Files：** 新增 TeacherKnowledgeView.vue、KnowledgeSources.vue、api/knowledge.ts、types/knowledge.ts；修改 router/index.ts、constants/navigation.ts、api/ai.ts、types/ai.ts、WrongAnalysisPanel.vue、utils/aiCapabilities.ts；后端 AICapabilityController 增量返回 knowledgeReady/workflowReady/agentReady（M1 后两项为 false，后续分别按实际能力启用）；新增 frontend/test/aiEventDispatch.test.mjs、frontend/e2e/knowledge-tutoring.spec.ts。

**Interfaces：** StreamOptions 增加 onSources/onToolStep/onPlan；sources 作为结构化状态保存，不拼入正文。能力状态读取配置/最近结果，不为检查额外调用收费模型。

- [ ] 写 sources_event_is_not_rendered_as_answer_text、legacy_message_event_still_works、old_run_events_are_ignored；把事件分派提取成可测试纯函数，保持既有解析行为。
- [ ] 在 frontend 运行 npm test，先确认新测试失败，再实现教师上传/状态/撤回、错题“依据讲义辅导”选项、可点击来源卡片与原文片段弹层。
- [ ] E2E 覆盖上传→READY→学生作答→引用回答→刷新历史→查看原文；另覆盖撤回后的不可用提示、缺图提示、取消、错误重试。沿用隔离账号与 writeGuard，不写公共演示数据。
- [ ] 运行 npm test、npm run build；隔离后端和 mock 模型准备好后运行 npm run test:e2e -- knowledge-tutoring.spec.ts。
- [ ] 检查窄屏、长公式、长文件名和空状态；提交 feat: expose cited tutoring and teacher knowledge management。

### T8：交付 M1 效果报告与投递版（8h）

**依赖：** T1/T7。**Files：** 新增 evaluate_platform.py、score_ai_evaluation.py、compare_ai_evaluation.py 及对应 test_*.py；新增 docs/ai-evaluation/m1-results.md、docs/demo/m1-demo.md；更新 README.md。

**Interfaces：** runner 通过隔离平台端点调用 A/B/C 真正业务路径，不用单独临时 Prompt 替代；scorer 消费 run 输出和独立 rubric，输出 {caseId, pass, citationValid, supportReview, reviewer, reasons}。compare 保留分母、失败、取消和未评分项。

- [ ] 写 test_platform_runner_uses_selected_mode、test_holdout_never_enters_tuning、test_unreviewed_cases_are_not_passes、test_missing_usage_is_not_free；单元测试完全 mock HTTP。
- [ ] 实现 --split development/holdout、--mode baseline/vector/hybrid、--max-requests、--max-estimated-cost-cny、--allow-model-calls；默认只准备输入。单次执行记录价格版本与预算账本，含失败/重试费用保守预留。
- [ ] 在开发集选定版本后跑冻结留出 A 与最终版本；人工复核正确性与引用支持，输出原始计数、分组差异及至少 5 个代表性失败案例。可重复跑但不得只选最好的一次。
- [ ] 运行 python3 -m unittest discover -s scripts/verification -p 'test_*.py'；复跑后端/前端受影响测试，确认 M1 演示从空库可复现。
- [ ] 写 3 分钟演示脚本和 README 实验表；明确真实样本/合成样本、复核方式、未完成项。提交 docs: deliver cited tutoring MVP with reproducible evaluation；仅此时形成 M1 可投递版本。

### T9：知识点标签与可推荐练习（10h）

**依赖：** M1。**Files：** 新增 V14 迁移、KnowledgePoint/QuestionKnowledgePoint 实体与仓储、PracticeRecommendationService；修改 Question 创建/更新 DTO、题库管理 UI、任务题目/作答快照构造；新增 QuestionKnowledgeSnapshotTest、PracticeRecommendationTest。

**Interfaces：** recommend(viewerId, eventId, pointCodes, limit) -> List<PracticeCandidate>，候选含真实 questionId、知识点与可见字段；不含未授权答案。人工维护 code，不自动断言学生真正的认知错误。

- [x] 写 old_snapshot_without_tags_stays_unknown、editing_tags_does_not_rewrite_old_task、recommendation_respects_answer_access；创建未首次作答的在进行作业，断言不能通过推荐拿到答案/不允许的补练。
- [x] 补知识点目录、题目标签与任务/作答快照；目录预置12个人工维护代码，既有题目保持未标注。
- [ ] 为两主题40–60道真实题目人工标注10–15个知识点，保留缺标签状态，不强填；等待教师逐题确认，不能由模型或开发者代替教学判断。
- [x] 推荐按匹配点、难度、练习可访问性筛选，去重且最多6题；由AnswerAccessPolicy和任务可见性决定字段，候选不足返回实际数量。
- [x] 运行 ./mvnw -B test -Dtest=QuestionKnowledgeSnapshotTest,PracticeRecommendationTest -Dgroups=unit，以及题库/作答权限回归；frontend 运行 npm test 与 npm run build。
- [x] 提交 feat: add versioned knowledge tags and safe practice recommendations。

### T10：增加显式工具调用协议（8h）

**依赖：** T9/T0a 的适配决策。**Files：** 新增 ToolChatGateway、T0a 选定的工具模型适配类（直接 HTTP 时使用 OpenAiCompatibleToolChatClient；使用库时按决策记录的类名实现）、dto/agent/{ChatTurn,ToolDefinition,ToolCall,ToolChatResponse}.java；新增 AgentProperties；新增 ToolChatWireContractTest。

**Interfaces：** complete(turns, definitions, modelConfig, cancellation) -> ToolChatResponse；包含 callId、name、JSON arguments、finishReason、usage、usageSource。首版工具轮用非流式 HTTP，循环进度通过 SSE 上报；旧聊天流式客户端保持兼容。

- [x] WireMock 测试 schema、assistant/tool role、tool_call_id、多个 call、缺失 usage、非法 JSON、429、取消；assert 未返回 tools 的普通正文不会被硬解析成工具调用。
- [x] 运行 ./mvnw -B test -Dtest=ToolChatWireContractTest -Dgroups=unit 看见失败后通过选定的库/HTTP 适配实现协议；不依赖模型对“兼容 OpenAI”字样的宣称，保留实际协议夹具。
- [x] 请求带总截止时间，按 T0a 验证过的 SDK 取消机制传递取消；模型 ID、端点和能力独立配置，toolCallingEnabled 默认关闭，agentReady 仍为 false，不影响 tutoring/reports。
- [ ] 用明确启用的独立外部冒烟测试验证一个目标模型；写日期和结果，不把付费冒烟加入普通 CI。
- [x] 重跑旧 AIWireContractTest 与新测试。
- [x] 提交 feat: add typed tool-calling model adapter。

### T11：先建立工作流，再实现有界 Agent（16h）

**依赖：** T10/T5。**Files：** 新增 LearningToolRegistry、LearningWorkflowService、LearningAgentService、LearningPlanValidator、controller/LearningAssistantController.java；为个人汇总新增受 userId/eventId 约束的仓储查询；修改 dto/internal/ConversationType.java 与会话接入；新增 LearningWorkflowContractTest、LearningAgentContractTest、LearningToolAuthorizationTest。

**Interfaces：** preview(viewerId, sessionId, eventId, days, requestId, emitter)；registry.execute(viewerContext, toolCall, deadline) -> ToolResult；validator.validate(planJson, returnedCandidates, sourceRevision) -> ValidatedPlanPreview。viewerContext 只能由后端认证和任务访问结果构造。ai.learning-assistant.mode=WORKFLOW/AGENT 是后端配置，初始 WORKFLOW；run 与预览事件记录真实 executionMode，客户端不能通过模型参数切换权限或预算。

- [x] 写权限覆盖、个人统计隔离、工作流共用工具、知识问答跳过学情、空错题不虚构练题、无效题目 ID 拒绝、Agent 达到预算停止；另覆盖取消和重复调用停止，限制仍是 6 轮/8 次工具/60 秒。
- [x] 注册第 6 节四个只读工具并实现 LearningWorkflowService：固定顺序收集事实，再由代码生成合法候选计划。通过 LearningWorkflowContractTest，核对统计、选题和权限；不标为 Agent。
- [x] 实现 LearningAgentService 的模型→校验→执行→工具结果→模型循环，模型轮次和工具调用均计预算；知识问答跳过学情读取。
- [x] 两模式共享 LearningPlanValidator、取消、权限和返回候选约束；缺标签/无候选返回部分空计划，不编造题目；超限与失败明确终止，不静默切换模式。
- [x] 运行 ./mvnw -B -Dgroups=unit -Dtest=LearningWorkflowContractTest,LearningAgentContractTest,LearningToolAuthorizationTest,LearningPlanValidatorTest。
- [ ] T13/T14 补齐 SSE tool_step 事件和持久化 executionMode/run 记录；当前预览 SSE 只返回 progress、plan、complete/error。
- [ ] 实际模型调用与工作流/Agent 各20案例对照留待 T15 独立预算评测；动态 Agent 默认关闭。

### T12：复习计划保存、激活与补练反馈（12h）

**依赖：** T11。**Files：** 新增 V15 迁移、LearningPlan/LearningPlanItem 实体与仓储、LearningPlanService、LearningPlanController；接入已有补练提交服务；新增 LearningPlanLifecycleTest、LearningPlanMySqlConcurrencyTest。

**Interfaces：** saveDraft(viewerId, runId, requestId) -> LearningPlanView；activate(viewerId, planId, expectedVersion)；progress(viewerId, planId) -> PlanProgress。sourceAnswerRevision 使用本用户/任务的有序作答版本/摘要，不用全班汇总版本。

- [ ] 写 duplicate_save_returns_same_plan、parallel_activation_is_idempotent、changed_answer_revision_requires_refresh、practice_does_not_change_original_score、other_user_cannot_read_plan。
- [ ] 失败后实现服务端读取成功 run 产物保存 DRAFT；事务内唯一键和版本校验；激活前重新校验题目可练性、知识版本与源作答版本。
- [ ] 计划 item 保留选题快照、知识点与来源，学生以现有补练链路作答；进度关联本人计划创建/激活后的合法尝试，统计口径写清楚。
- [ ] “补练后变化”只展示次数、正确情况与掌握状态变化，不归因为 AI 带来提分；用户可取消计划，旧作业成绩仍由既有逻辑决定。
- [ ] 跑生命周期单测和 @Tag("mysql") 并发测试后提交 feat: persist revision-aware learning plans and practice feedback。

### T13：助手交互、计划卡与历史恢复（12h）

**依赖：** T12/T7。**Files：** 新增 AgentExecutionSteps.vue、LearningPlanCard.vue、api/learning-plan.ts、types/learning-plan.ts；修改 StudentAIView.vue、StudentWrongBookView.vue、useAIStreamState.ts、api/ai.ts；新增 frontend/test/learningPlanState.test.mjs、frontend/e2e/learning-assistant.spec.ts。

**Interfaces：** UI 只消费工具摘要、ValidatedPlanPreview 和保存后的 PlanView。用户动作依次是生成建议、检查依据、保存/激活、去练习；按钮状态绑定 runId 与计划 version。界面按 executionMode 展示“复习工作流”或“智能复习助手”，不把固定步骤展示为模型自主决策。

- [ ] 写 cancelled_run_cannot_activate_preview、late_event_cannot_replace_new_plan、double_save_uses_same_request_id、history_restores_sources_and_plan；npm test 先失败后实现。
- [ ] 在原 AI 页面增加“按本次作业安排复习”入口，展示实际步骤、部分完成原因、来源卡和最多 6 道题；不新增通用工作台或展示模型内部推理。
- [ ] E2E 覆盖生成→检查来源→保存→激活→补练→进度更新；另覆盖源作答变化、刷新、重复点击、取消、空题库、工具故障。
- [ ] 运行 npm test、npm run build、npm run test:e2e -- learning-assistant.spec.ts；检查移动端和已有问答/报告入口仍可用。
- [ ] 提交 feat: expose learning assistant plans and practice progress。

### T14：运行链路、用量与费用记录（8h）

**依赖：** T6/T11。**Files：** 扩展 AIMetrics、AiRunRecorder；新增 AiUsageAccounting；新增本人运行详情接口；新增 AiRunAccountingTest、AiRunOwnershipTest；新增 docs/ai-evaluation/cost-model.md。

**Interfaces：** recordStage(runId, stage, duration)；recordUsage(runId, model, reportedUsage, estimatedUsage, pricingVersion)；usageSource=reported/estimated/unknown。Token 会话累计、单次请求输入和真正上下文窗口分别处理。

- [ ] 写 missing_usage_is_unknown、cancelled_run_is_counted_once、retry_cost_is_not_dropped、different_user_cannot_read_run、metrics_have_no_user_or_run_id_labels。
- [ ] 失败后记录请求接收、检索、上游首字、最后内容/终态时间；应用端 TTFT 从本次请求开始计时，保留现有上游 first.content 指标，避免名称混淆。
- [ ] 记录低基数 provider/model/mode/status 标签；runId 只写受控运行记录，不作为 Micrometer label。日志仅含 ID/状态/耗时和必要错误类别。
- [ ] 费用按模型价格快照、币种和计量单位估算；无 usage 不报 0。新路径的输入超限须缩减允许的资料/历史或返回错误，不悄悄截掉关键题目/公式。
- [ ] 运行相关单测和现有 AIMetricsTest，提交 feat: account for AI stages usage and estimated cost。

### T15：完整实验、优化取舍与故障回放（10h）

**依赖：** T13/T14。**Files：** 更新评测 runner/scorer；新增 docs/ai-evaluation/m2-results.md、failure-cases.md、model-selection.md；更新 agent-tasks 的执行夹具。

**Interfaces：** 同一版本 runner 比较固定工作流与 Agent，输出 taskSuccess、toolValidity、steps、duration、cost。每条结果可以追溯 runId/代码 SHA/数据版本与人工评分。

- [ ] 写 test_partial_plan_is_not_full_success、test_failed_calls_remain_in_denominator、test_price_version_is_preserved；重跑评测单测。
- [ ] 在 20 工具案例上比较固定工作流与模型选择工具，增加超时、畸形参数、缺标签、候选不足场景；不把工具调用次数当作智能程度。
- [ ] 依据开发集诊断最多选两项优化，例如 chunk 边界、检索查询、重排序、历史窗口；每项单独对照。不同模型比较时保持任务和预算可比。
- [ ] 完成 M2 留出验证与人工核对，报告原始分子/分母和不确定性；已查看并用于优化的留出案例不再声称全新泛化验证。
- [ ] 至少整理 5 类失败：公式损坏、检索漏召回、引文不支持、工具误用、源数据过期；写触发、定位、修复和仍有限制，提交 docs: evaluate learning agent quality cost and failure modes。

### T16：部署复现、演示与简历材料（20h）

**依赖：** T15；M1 已单独可交付。**Files：** 更新 README.md、docs/项目启动指南.md、docs/生产部署指南.md、docker-compose.yml；新增 docs/demo/{ai-demo.md,interview-notes.md,resume-project.md}；新增或扩展隔离环境验收脚本与 CI。

估算拆分：部署/恢复/回归约 10h，演示录制/实验展示/简历与面试材料约 10h。

**Interfaces：** knowledge profile 可选启用，原配置关闭新功能仍可启动；持久卷包括原始资料和向量服务，MySQL 中保存可重建版本与来源；演示数据通过已有 seed 风格扩展，不改公共学生数据。

- [ ] 在空数据库迁移、旧库升级、功能关闭、功能开启四种模式验证启动；对新版本执行备份→恢复→从 MySQL/原始资料重建索引→原引用访问的完整演练。
- [ ] 验证撤回/重启/模型不可用/取消/费用缺失等故障，保留复现命令、期望和实际输出；新表和文件没有纳入备份则不能宣称可恢复。
- [ ] 跑第 10 节完整必需检查，修复阻塞项；仅检查通过且演示可复现时标注对应功能已完成。
- [ ] 录制 3–5 分钟演示，包含一个正常流程、一个资料不足/失败处理、实验对比页。输出一张架构图、一张对照表、一个失败分析和一份个人职责说明。
- [ ] 简历只写已经实现且有证据的指标；在 README 标明测试环境、样本量、数据来源与没有生产容量证明。提交 docs: package AI application project for reproducible demo and interviews。

## 10. 验证命令与完成定义

命令在仓库根目录执行；前端命令在 frontend 子目录。数据库/容器测试只针对隔离环境，不能指向在线演示库。

| 层次 | 命令 / 环境 | 通过标准 |
|---|---|---|
| G0 实验合同 | experiments/rag-spike 内运行 ../../mvnw -B test；真实模型另行显式开启 | 最小检索链路、来源、工具协议与取消验证可复现；记录最终接入方式 |
| Python 合同 | python3 -m unittest discover -s scripts/verification -p 'test_*.py' | 样例、分割、字段剥离、评分、预算测试通过 |
| 后端既有默认组 | ./mvnw -B test | 当前默认 unit/ai-sync/ai-async/integration-controller/integration-repository 无回归；准备 Redis/Docker |
| MySQL 专属 | ./mvnw -B test -Dgroups=mysql -DexcludedGroups=external-api | 真实 MySQL 8 下迁移、ngram、计划并发通过；按现有 CI 做空库与 V7 升级路径 |
| Qdrant 专属 | ./mvnw -B test -Dgroups=knowledge-integration -DexcludedGroups=external-api | 固定版本容器下索引持久化、删除、过滤和查询通过 |
| 前端单测/构建 | npm test；npm run build | SSE、计划状态、权限界面、TypeScript 与构建通过 |
| 关键 E2E | npm run test:e2e -- knowledge-tutoring.spec.ts learning-assistant.spec.ts | 隔离账号与 mock 上游中完整用户流程通过；M1 只跑已实现前半部分 |
| 真实模型实验 | 显式指定 allow-model-calls、预算与候选模型的 runner | 实际调用完成、评分完成、原始结果可追溯，未评分不算成功 |
| 部署演练 | Compose 隔离环境与现有 delivery/备份恢复链路 | 开关关闭/开启、旧库升级、重启、恢复与索引重建通过 |

每任务完成定义：新增行为有针对性测试；对应检查实际通过；异常语义明确；文档反映真实实现；提交可单独审查。文档/UI 微调只做对应构建和人工检查，不为低影响文字变更增加镜像式测试。

M1 发布条件：资料可导入/撤回、错题引用可追溯、跨班隔离通过、旧聊天行为保持、至少一份真实模型 A/最终版对照且人工评分完成、演示可复现。质量目标未达成须公开标明，不把“功能已完成”写成“准确率达标”。

M2 发布条件：M1 继续通过；四个工具具备权限和限额；固定工作流先验收、动态工具选择场景再验收；计划可保存激活补练、源版本变化可检测；同 20 任务的两模式对照已评；用量、费用来源和失败状态可解释。动态执行未完成时允许发布工作流版，但 README/界面/简历均不得声称已实现 Agent。

## 11. 风险与具体收缩办法

| 实际风险 | 发现方式 | 应对 / 收缩 |
|---|---|---|
| PDF 公式错位导致错误讲解 | 人工检查提取片段与失败案例 | 首版优先人工整理 Markdown，保留 PDF 支持边界；扫描件后置 |
| 题量和知识点不足以支持推荐 | 标签覆盖和候选不足统计 | 先完成两个主题，允许部分计划，不生成虚构题 |
| 混合检索比纯向量更差 | 同开发集 Recall 与回答对比 | 保留纯向量为选用配置，记录失败原因 |
| 工具模型能力与现有视觉模型不一致 | WireMock 合同 + 独立真实冒烟 | 工具模型独立选型，不替换旧图片问答路径 |
| 试用服务器内存不足 | 记录基线和新容器 RSS/峰值 | 本地/隔离环境演示，延后在线全功能部署 |
| 缺物理复核者 | reviewStatus 和 reviewer 字段 | 清楚标注开发者核对，缩小案例并核对，不夸大教育效果 |
| 中途开始投递，时间被压缩 | M1 里程碑后评估剩余时间 | 保留 M1、评测与演示；延后 Agent、重排序、监控可视化 |
| 计划在实现中膨胀 | 新任务无法对应本文件 Goal | 新需求单独记录，不默认纳入本轮 |

## 12. 2026 招聘要求映射与资料来源

本节是定向样本，不代表整个就业市场，也不意味着满足项目指标即可获得录用。日期为官方页面标注的发布或更新时间，核对日期为 2026-10-23。

| 官方岗位 | 日期 / 级别 | 本项目对应展示 |
|---|---|---|
| [百度 Java 工程师实习生 J106251](https://talent.baidu.com/jobs/detail/INTERN/a3431f5a-582d-4829-899b-a70a2882604a) | 2026-09-24，实习 | Java/Spring Boot 业务集成、RAG、Agent、可靠性；对应 T2–T7/T11 |
| [百度 AI 开放平台研发实习生 J103363](https://talent.baidu.com/jobs/detail/INTERN/ef30e579-2d3d-4539-ad90-884521b815c9) | 2026-07-21，实习 | 流式调用、鉴权配额、上下文、Function Calling、日志；对应既有实现/T10–T14 |
| [百度大模型研发工程师 J100679](https://talent.baidu.com/jobs/detail/GRADUATE/66a12645-f0f1-435c-8426-9fb91f1be330) | 2026-07-21，校招 | RAG/Agent、调用链路、数据工程；Python 用于 T1/T8/T15 评测与分析，不能据此声称熟练 Python 后端 |
| [腾讯实时互动后台研发工程师](https://careers.tencent.com/jobdesc.html?postId=2095435115055464448) | 2026-10-09 更新，三年以上经验 | Agent 输出质量、评测指标、工程交付；用于观察深化方向，不当作应届项目最低要求 |

技术依据：MySQL 的 [ngram 中文全文解析](https://dev.mysql.com/doc/refman/8.4/en/fulltext-search-ngram.html)、Qdrant 的 [多租户过滤](https://qdrant.tech/documentation/manage-data/multitenancy/)与[混合检索融合](https://qdrant.tech/documentation/search/hybrid-queries/)、PDFBox 的 [3.x 迁移与加载接口](https://pdfbox.apache.org/3.0/migration.html)。本文选择在应用侧融合 MySQL 与 Qdrant 排名，不要求使用 Qdrant 的全文检索能力。

### 12.1 开源项目参考与具体借鉴

以下参考基于项目官方仓库和文档，不以功能列表代表已复现源码，也不以安装平台代替本项目实现。仓库与文档随时间变化，T0a 应记录实际参考的版本/提交。

| 项目 / 资料 | 已核对的内容 | 本计划对应动作 |
|---|---|---|
| [LangChain4j 官方示例](https://github.com/langchain4j/langchain4j-examples)、[RAG 教程](https://docs.langchain4j.dev/tutorials/rag/) | 提供文档处理、Embedding/检索器、RAG 与 Java 示例，云端模型可作为生成后端 | T0a 验证一个最小示例及兼容性，T4/T6 借鉴模块划分；不把框架默认英文样例配置直接当作中文物理最佳方案 |
| [LangChain4j 工具调用说明](https://docs.langchain4j.dev/tutorials/tools/) | 模型提出工具调用，由应用执行并回传结果；有低层和高层接口 | T10 验证 tool call 往返；T11 权限与参数校验放在自己的业务工具边界 |
| [AnythingLLM](https://github.com/Mintplex-Labs/anything-llm) | 支持资料导入、来源引用，分别配置聊天模型、Embedding 和向量数据库 | T4 配置解耦、T7 引用交互参考；不照搬完整产品或把调用其 API 当作自研检索 |
| [Dify](https://github.com/langgenius/dify) | 将模型管理、资料检索、工作流/Agent 与运行观测组合成应用平台 | T11/T13/T14 借鉴执行记录与错误展示；不为当前两个主题额外部署整套平台 |
| [Anthropic：Building effective agents](https://www.anthropic.com/research/building-effective-agents) | 区分预定义工作流与模型动态选择工具，也讨论直接 API 与框架的取舍 | 第 6 节能力判定与 T11/T15 工作流对照；该文是架构参考，不作为 2026 招聘样本 |

这些参考不改变核心分工：模型供应商提供生成/推理，自己的项目负责数据、检索、业务工具、权限、运行状态和评测。直接调用 API 可构成完整 RAG/Agent；缺少相应链路时，仅更换 SDK 或页面名称不能增加这两种能力。

## 13. 面试与简历交付物

- README 第一屏：业务问题、可运行入口、已实现 AI 能力、真实实验表与架构图。
- 3–5 分钟演示：上传讲义→错题辅导→查看来源→请求复习→工具执行→补练反馈，至少展示一个不足/失败案例。
- 实验报告：模型/Prompt/语料版本、A 与最终版对照、原始计数、评分方式、延迟/费用、失败分析。
- 架构与取舍：为何保留单体、如何做班级隔离、文档版本/撤回、取消、工具次数/时间预算、计划幂等。
- 个人贡献说明：自己设计、实现、验证的内容，以及 AI 编程工具参与方式；能解释关键代码和测试。
- 简历描述模板：“在既有教学平台中实现带来源的错题辅导与工具调用复习助手；基于 N 条固定案例对比 A/B，记录通过率、引用支持率、时延与费用。”N 和结果由真实实验填写，不预置提升百分比。

## 14. 执行前检查与计划自审

执行前只需核对：实际每周投入、两个主题的内容可用性、是否有物理复核者、模型/Embedding 额度、本地 Docker/内存。无额外约束时采用本文建议，不因普通实现细节反复暂停。

自审结果：

- 目标覆盖：最小验证/选型在 T0a，正式 RAG 在 T2–T8，工作流/Agent 在 T9–T13，评测在 T0/T1/T8/T15，成本在 T14，展示在 T8/T16。
- 数据缺口覆盖：知识点标签、题目快照、个人统计查询、原文文件持久化、运行记录、版本过期检测均有明确任务。
- 失败覆盖：Review Focus 五项已分别落入授权、提取、索引、Agent、SSE 和历史测试。
- 接口一致：现有错题端点保留；新工具不接受任意 userId；计划先预览后由用户保存激活；sourceAnswerRevision 来自本人任务作答。
- 预算一致：模型对比与改进实验均在显式 runner 中执行，默认 CI 不收费，缺 usage 不计为零。
- v1.1 一致性：18 个任务（T0、T0a、T1–T16），中心工时 192h；M1 为 96h；框架选择不改变业务端口；固定工作流与 Agent 共享工具、按真实模式记录。
- 计划边界：本次只新增本计划文档；未安装依赖、迁移数据库、调用收费模型、部署服务或修改业务代码。实际测试状态将在执行时记录。

建议执行方式：同一执行者顺序推进，先 T0/T0a 展示最小链路并锁定接入方式，以 M1 为首个业务交付点；每任务局部验证、每里程碑整体验收。实施前先确认本方案的范围与取舍，确认后从 T0 开始。

