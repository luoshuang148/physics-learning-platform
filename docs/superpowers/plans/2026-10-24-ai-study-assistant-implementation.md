# 物理学习平台 AI 学习助手 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成平台题库 RAG、本人学习记录驱动的补练 Agent、真实补练闭环和可复现评测，支撑 AI 应用开发实习面试。

**Architecture:** 继续使用 Java 单体和 Vue。题库检索采用独立的题目来源模型，复用现有 EmbeddingGateway、本地 BGE 和 Qdrant 技术，不强行复用绑定班级文档的实体。MySQL 提供事实与授权，RAG 提供平台内容依据，Agent 只产生建议，用户确认后由确定性接口保存执行。

**Tech Stack:** Java 21、Spring Boot 3.3.8、JPA/JDBC、MySQL 8、Qdrant、LangChain4j、本地 BGE、Vue 3/TypeScript、Python评测、JUnit、Playwright。

**Spec:** [详细方案](../specs/2026-10-24-ai-study-assistant-design.md)

## Global Constraints

- 保留现有业务与未提交改动；本计划只记录本轮范围，已有工作不算本轮完成。
- Java 21；继续现有 Java 服务，不新增 Python 在线微服务；Python 用于模拟数据、评测和结果分析。
- 核心语料来自平台题目、解析与标签；班级资料保留为辅助能力。
- 用户身份与可见范围由服务端决定；禁止模型指定 userId/classId；任何来源进入模型前完成授权校验。
- 题目修订、禁用和删除后旧索引不得继续作为有效证据；错题讲解保留历史快照事实。
- 题库候选必须显式 ai_practice_enabled=true；沿用现有保守的待作答题保护，不放宽原权限。
- 首版聚焦四个力学主题的120道文字选择题；120是建设规模目标，不代表现有数量。
- 计划1～7天、最多6题；Agent最多6轮模型交互/8次工具调用，总60秒、单模型25秒、单工具5秒。
- 用户确认后才保存和激活；不自动发布教师作业；计划练习独立归属planItemId。
- 60开发/40留出问答检索任务与20计划任务；评测阈值冻结后再跑留出集；所有效果指标须来自实测。
- 每个阶段完成后做针对性验证和小范围提交；不得把其他已有改动混入提交。暂不自动push或合并。

## Review Focus

1. 题目在检索后被修改、删除、禁用或关联待考作业：生成前/引用点击/计划激活/练习提交重新校验（任务2、3、4、6）。
2. 当前作业无错题、无作答或无未做候选：返回事实和部分/空建议，不推断缺陷、不拼凑题号（任务5、8）。
3. 学生改题号、记录ID、计划ID或模型伪造身份：所有入口拒绝越权且不泄露内容（任务3～6）。
4. 模型超时、重复工具调用、断线、重复点击与迟到SSE：不重复扣费调用或写库，不把部分结果标完成（任务4～6、8）。
5. 新题不属于原作业、同题出现在两份计划、原作业后来更新：计划作答准确归属，原作业成绩不变（任务6、8）。

## 任务依赖和里程碑

M0：任务1。M1：任务2→3→4，可交付可引用的题库RAG。M2：任务5→6→7，可交付真实工具调用与补练闭环。M3：任务8。M4：任务9。任务7的语料准备可在任务2后提前进行，导入验收等接口完成后再执行。

| 任务 | 主要成果 | 开发日估算 |
|---|---|---|
| 1 | 配置、基线、复现环境 | 1 |
| 2 | 题库发布和可靠索引 | 1.5～2 |
| 3 | 授权检索及来源接口 | 1～2 |
| 4 | RAG问答与历史恢复 | 1.5～2 |
| 5 | 工具调用与有效计划预览 | 1.5～2 |
| 6 | 跨题补练与准确进度 | 1.5～2 |
| 7 | 样例、教师管理和整体体验 | 1～2 |
| 8 | 数据冻结、效果及故障评测 | 3～5 |
| 9 | 复现部署和面试交付 | 1～2 |

合计约13～20个有效开发日，允许任务复用节省工作量；以验收结果计算完成度，不按代码文件数量计算。

## 文件布局与共用契约

以下路径都相对于仓库根目录。新增名字是规划名称；现有文件无需整体重写。

- 新增后端包 `src/main/java/com/zhixuewuli/service/platformsearch/`：题库索引、授权、检索、来源读取；不接管班级文档。
- 新增 `src/main/java/com/zhixuewuli/dto/platformsearch/PlatformRetrievalTypes.java`：统一嵌套records，所有任务引用同一组类型。
- 新增后端包 `src/main/java/com/zhixuewuli/service/ai/assistant/`：助手会话、RAG问答编排、历史持久化；复用已有模型客户端和流处理，不另造供应商SDK。
- 复用 `service/ai/agent/` 中 Agent、工作流、工具网关、计划验证和运行记录服务。
- 新增 `frontend/src/components/assistant/`：输入、会话、来源、计划区块；`StudentAIView.vue`保留为路由壳层。
- 新增 `scripts/fixtures/platform-rag/` 与 `docs/ai-evaluation/corpus-v2/`：业务样例与评测分别管理；现有corpus-v1保留。

共用类型（在任务2/3定义）：
- `SearchRequest(String query, Long eventId, List<String> knowledgePointCodes, Integer difficulty, boolean excludeAttempted, int limit, RetrievalMode mode)`：mode是服务端策略，公共请求不允许改变权限。
- `QuestionHit(Long questionId, long revision, String content, Integer difficulty, List<String> knowledgePointCodes, List<String> sourceIds)`。
- `Evidence(String sourceId, Long questionId, long revision, String part, int ordinal, String text)`。
- `SearchResult(RetrievalStatus status, String actualMode, List<QuestionHit> questions, List<Evidence> sources, long durationMs)`。
- `EligibleQuestion(Long questionId, long revision)`；`QuestionIndexStatus(Long questionId, long revision, String status, String failureCode)`。

## Task 1：锁定环境与配置基线

**Files:**
- Modify: `src/main/resources/application.yml`、`src/main/resources/application-dev.yml`、`.env.dev.example`。
- Create: `scripts/verification/check_ai_environment.py`、`docs/ai-evaluation/v2-baseline.md`。
- Test: `src/test/java/com/zhixuewuli/common/config/AiEnvironmentBindingTest.java`。

**Interfaces:** 环境检查仅输出DB/Redis/Qdrant连通性、已配置开关、索引状态、最近调用状态和Java版本；不输出密钥，不自动调用付费模型。

- [ ] 保存当前分支和已有改动清单，核对Java21、数据库schema、前端4173/后端8080及外部服务连接来源；不得用演示/空数据库覆盖现有开发库。
- [ ] 增加配置测试：从`.env`形式的properties加载`APP_KNOWLEDGE_ENABLED=true`和Agent相关键时，绑定对象必须得到正确值；关闭开关时能力接口不得声称可用。
- [ ] 运行 `./mvnw -Dtest=AiEnvironmentBindingTest test`，记录未修复映射的失败；显式补YAML占位映射，敏感值只留本地环境。
- [ ] 增加环境检查脚本，记录“已配置”和“实际模型调用成功”是两个状态；向量/Agent未验证时显示未验证。
- [ ] 重跑配置测试和只读环境检查；将启动方式及基线状态写入文档，单独提交本任务改动。

## Task 2：题库发布字段与索引一致性

**Files:**
- Modify: `entity/Question.java`、`dto/request/QuestionCreateRequest.java`、`dto/request/QuestionUpdateRequest.java`、`dto/response/QuestionResponse.java`、`service/question/impl/QuestionServiceImpl.java`（均在`src/main/java/com/zhixuewuli/`下）。
- Create: `src/main/resources/db/migration/V17__platform_question_search.sql`。
- Create: `service/platformsearch/QuestionSourceBuilder.java`、`QuestionSourceIndexService.java`、`QuestionIndexWorker.java`、`QuestionSearchRepository.java`、`QuestionVectorIndex.java`、`QdrantQuestionVectorIndex.java`、`dto/platformsearch/PlatformRetrievalTypes.java`。
- Tests: `src/test/java/com/zhixuewuli/service/platformsearch/QuestionSourceBuilderTest.java`、`QuestionIndexConsistencyTest.java`；`src/test/java/com/zhixuewuli/repository/QuestionIndexMySqlTest.java`。

**Interfaces:**
- `QuestionSourceBuilder.build(Question question)`返回带part/ordinal/hash的检索单元；复用`EmbeddingGateway.embed(List<String>,boolean,BooleanSupplier)`。
- `QuestionSourceIndexService.enqueueCurrent(long questionId)`、`enqueueDelete(long questionId,long revision)`参与调用方MySQL事务。
- `QuestionVectorIndex.upsert(String indexVersion,List<QuestionVectorPoint> points)`、`search(String indexVersion,List<EligibleQuestion> allowed,float[] vector,int limit)`、`deleteQuestion(long questionId,long throughRevision)`；`QuestionVectorPoint`定义为同包record，包含确定性pointId、题ID/修订/part/ordinal/向量，不存学生数据。

- [ ] 先写builder测试：LaTeX/中文/长解析分段、空解析、纯图片题；超模型token上限必须拆分或明确报错，不静默截断。
- [ ] 写MySQL与向量适配测试：修改解析会生成新revision；重试幂等；旧任务晚完成不得发布；事务回滚无索引任务；删除后旧点不能回源。
- [ ] 运行相应测试记录失败；新增字段、`question_search_units`、`question_index_jobs`。有效单元以题目当前revision及READY状态为准；任务表不依赖已删除题目的外键存活，保留删除墓碑。
- [ ] 实现两个worker、租约120秒/30秒续租、最多3次尝试/5秒与30秒退避；当前题已变化则任务标OBSOLETE。Qdrant采用独立题库collection，名字含模型/维度/分块配置版本，禁止伪造classId复用文档集合。
- [ ] 明确单元ordinal稳定规则和pointId生成；写入向量后核对数量再发布READY；失败不发布半成品。
- [ ] 跑单元测试及真实MySQL/Qdrant集成验证，验证已有题默认未发布，现有文档索引不受影响；提交。

## Task 3：题库授权检索和来源查看

**Files:**
- Create: `service/platformsearch/QuestionRetrievalAccessPolicy.java`、`PlatformQuestionRetriever.java`、`PlatformSourceService.java`、`QuestionKeywordSearch.java`。
- Create: `controller/PlatformSearchController.java`、`dto/request/PlatformSearchRequest.java`、`common/config/properties/PlatformSearchProperties.java`。
- Modify: `service/question/AnswerAccessPolicy.java`（只抽出可复用批量判断，不放宽原行为）、`repository/AnswerRecordRepository.java`。
- Tests: `src/test/java/com/zhixuewuli/service/platformsearch/PlatformQuestionRetrievalTest.java`、`PlatformSourceAuthorizationTest.java`；`src/test/java/com/zhixuewuli/controller/PlatformSearchControllerTest.java`。

**Interfaces:**
- `QuestionRetrievalAccessPolicy.eligible(Long viewerId,SearchRequest request): List<EligibleQuestion>`。
- `PlatformQuestionRetriever.retrieve(Long viewerId,SearchRequest request,BooleanSupplier cancelled): SearchResult`。
- `PlatformSourceService.requireReadable(Long viewerId,String sourceId): Evidence`。

- [ ] 编写测试：同义词/中文关键词/公式查询；同题多片段只计一个题；本人成绩筛选“没做过”；跨用户eventId、待考题、禁用题、旧版本来源被拒绝；关键词和向量分支均受相同限制。
- [ ] 运行测试记录失败；实现批量候选授权，给两路检索传入允许ID+revision，禁止以过滤后不足为理由扩大授权范围。
- [ ] 实现各路Top10、RRF(k=60)、最多5个来源和5道题。中文检索先确认MySQL FULLTEXT ngram能力；如果数据库不支持，采用可测试的中文分词关键词适配器并如实记录模式，禁止悄悄变成全表LIKE后声称全文检索。
- [ ] 区分MATCHED/INSUFFICIENT/UNAVAILABLE；向量故障允许显式配置的关键词降级，actualMode必须记录lexical_only。
- [ ] 实现设计中的search/source端点和参数校验；来源回源再次查权限/版本。仅做无模型检索验收，记录候选与耗时。
- [ ] 执行三个测试类及真实中文检索用例，确认未知来源不可访问、返回无答案泄漏；提交。

## Task 4：真正接通RAG问答与会话

**Files:**
- Create: `src/main/resources/db/migration/V18__assistant_conversations.sql`。
- Create: `service/ai/assistant/AssistantConversationService.java`、`PlatformQaService.java`、`AssistantRunRepository.java`；`controller/StudyAssistantController.java`；`dto/request/AssistantMessageRequest.java`。
- Modify: `frontend/src/views/student/StudentAIView.vue`、`frontend/src/api/ai.ts`、`frontend/src/types/ai.ts`、`frontend/src/utils/aiEventDispatch.ts`、`frontend/src/views/student/StudentWrongBookView.vue`。
- Create: `frontend/src/api/study-assistant.ts`、`frontend/src/components/assistant/AssistantChatPanel.vue`、`PlatformSources.vue`。
- Tests: `src/test/java/com/zhixuewuli/service/ai/assistant/PlatformQaServiceTest.java`、`AssistantConversationTest.java`；`frontend/e2e/platform-assistant.spec.ts`。

**Interfaces:**
- `PlatformQaService.stream(Long viewerId,AssistantMessageRequest request,SseEmitter emitter)`；使用任务3的SearchResult。
- `AssistantMessageRequest(question,mode,conversationId,eventId,recordId,requestId)`字段/约束按设计第8节。
- 前端新增题库来源类型，不复用必须含documentId/chunkId的旧AISource；旧文档来源使用原组件，二者通过kind区分。

- [ ] 先测模型收到的上下文：GENERAL不伪造平台来源；PLATFORM只包含授权内容；无匹配/服务故障文案不同；题目中的注入文本不能改变工具权限。
- [ ] 测试recordId属于本人且上下文一致；从快照讲历史题，引用最新版解析时分别标记；索引更新后不把旧来源注入追问。
- [ ] 测试重复requestId返回409原runId且模型调用次数仍为1；取消保存部分公开回答并标CANCELLED；迟到SSE不覆盖新run；切换eventId/recordId新建会话。
- [ ] 跑测试记录失败，建立会话/消息/运行表，复用现有供应商客户端实现SSE与取消，限制模型上下文长度并在装配后验证token预算，优先保留当前问题和相关来源。
- [ ] 将学习助手整理为设计中的两个页签；错题本入口携带recordId；来源卡可点开原题/解析；历史分页恢复。旧“手填题目ID”入口从新主流程移除。
- [ ] 跑后端测试和 `E2E_BASE_URL=http://127.0.0.1:4173/ npm --prefix frontend run test:e2e -- e2e/platform-assistant.spec.ts`；覆盖320/390/1440宽度；提交。

## Task 5：工具调用和计划证据

**Files:**
- Modify: `service/ai/agent/LearningToolRegistry.java`、`LearningAgentService.java`、`LearningWorkflowService.java`、`LearningPlanValidator.java`、`LearningPlanService.java`、`LearningAssistantRunRecorder.java`。
- Modify: `controller/LearningAssistantController.java`、`controller/AICapabilityController.java`。
- Create: `service/ai/agent/PlanCandidateRegistry.java`。
- Tests: 扩展 `src/test/java/com/zhixuewuli/service/ai/agent/LearningAgentContractTest.java`、`LearningToolAuthorizationTest.java`、`LearningPlanValidatorTest.java`、`LearningWorkflowContractTest.java`。

**Interfaces:**
- 工具参数保持当前eventId上下文约束；`search_platform_questions(query,knowledgePointCodes?,difficulty?,excludeAttempted,limit)`与`get_question_evidence(sourceIds)`映射任务3服务；所有身份在LearningToolContext内。
- `PlanCandidateRegistry.add(SearchResult result)`、`contains(Long questionId,long revision)`、`snapshot()`积累本次运行候选与来源。成功预览保存registry快照供后续保存/激活核对。
- 保留现有ValidatedPlanPreview字段；保存的预览增加版本化candidateRegistry JSON，不让调用方重新传入未授权题号。

- [ ] 先测一次Agent根据工具返回调用后续工具，最后生成有效计划；多次搜索候选取并集，不能只留最后一次结果。
- [ ] 测试凭空题号、未知引用、换学生/换作业、重复题、超6题、重复工具调用、8次上限、60秒超时、取消；均无草稿写入。
- [ ] 测试全对/无作答/无可练题：如实返回部分或空建议；“没做过”过滤同时覆盖首次作答与补练。
- [ ] 实现四个受限工具；原recommend_practice继续服务旧接口，新路径不被旧“只取前6条”的重推荐逻辑覆盖。保存计划校验实际返回的registry与当前授权，而非重新检索再取Top6判断。
- [ ] 改造固定工作流使用同一工具服务作对照，明确执行模式；设置并实际验证Tool Calling供应商，配置存在不算成功运行。
- [ ] 跑合同/权限/验证器测试，并在有明确调用上限时做一次真实模型冒烟，保存经过脱敏的工具轨迹及输出；提交。

## Task 6：计划题目真正能练、进度准确归属

**Files:**
- Create: `src/main/resources/db/migration/V19__learning_plan_practice_links.sql`。
- Create: `service/ai/agent/PlanPracticeService.java`、`dto/request/PlanPracticeAnswerRequest.java`、`dto/agent/SavedLearningPlanItem.java`。
- Modify: `controller/LearningPlanController.java`、`service/ai/agent/LearningPlanService.java`、`dto/agent/LearningPlanView.java`、`service/answerRecord/impl/AnswerRecordServiceImpl.java`（抽出复用而非复制计分）。
- Modify: `frontend/src/api/learning-plan.ts`、`frontend/src/types/learning-plan.ts`、`frontend/src/components/LearningPlanCard.vue`、`frontend/src/views/student/StudentQuestionView.vue`。
- Tests: `src/test/java/com/zhixuewuli/service/ai/agent/PlanPracticeServiceTest.java`、`src/test/java/com/zhixuewuli/repository/PlanPracticeMySqlTest.java`；`frontend/e2e/learning-plan-practice.spec.ts`。

**Interfaces:**
- 已保存条目DTO新增`itemId`、`questionRevision`；预览条目不伪造itemId，保留独立类型。
- `PlanPracticeService.question(Long viewerId,Long planId,Long itemId): QuestionResponse`（提交前仅题干/选项）。
- `PlanPracticeService.submit(Long viewerId,Long planId,Long itemId,String userAnswer,String requestId): AnswerRecordResponse`。
- 新`learning_plan_attempt_links(plan_item_id,answer_record_id,user_id,request_id)`，answer_record_id唯一，(user_id,request_id)唯一；写记录与链接同事务。计划条目补充完整snapshot JSON和question_revision，老条目保持null并仅展示历史、提示重新生成，不虚构旧修订。

- [ ] 先写集成测试：推荐题不在原作业也能打开、提交；作答eventId=null且practice=true，原作业成绩不变；同一题在两份计划中不会串计。
- [ ] 测试草稿/已取消/他人计划拒绝作答，题目更新/禁用/受保护后拒绝新提交；激活检查候选revision和作答版本。
- [ ] 测试同requestId同提交重放得到原结果，参数或条目不同则409；并发提交只产生一条记录及链接，网络失败重试不漏记进度。
- [ ] 将激活前题目快照、revision和来源保存，新增GET/POST条目接口。来源作业eventId保留于plan，不作为候选题的作业归属。
- [ ] 前端使用 `/student/practice?planId=...&planItemId=...` 走新接口；旧eventId/questionId练习继续原流程。进度按条目链接汇总次数和正确次数，同时按每个条目的最新一次作答判断ON_TRACK；一题多次答对不能覆盖其他题未做。
- [ ] 旧计划历史只展示旧口径并明确未迁移，不能将所有旧作答猜测关联到新条目；新旧统计不混合。
- [ ] 跑后端MySQL并发测试及E2E从生成/保存/激活到提交/刷新全流程；提交。

## Task 7：可维护题库和可复现模拟数据

**Files:**
- Modify: `frontend/src/views/teacher/TeacherQuestionManageView.vue`、`frontend/src/api/question.ts`、`scripts/seed_test_data.py`。
- Create: `scripts/fixtures/platform-rag/questions.jsonl`、`scripts/fixtures/platform-rag/personas.json`、`scripts/seed_platform_rag.py`、`scripts/test_seed_platform_rag.py`。
- Create: `controller/QuestionIndexController.java`（包根同前）、`frontend/e2e/question-index-status.spec.ts`。

**Interfaces:**
- `GET /questions/teacher/{id}/index-status`返回QuestionIndexStatus；`POST /questions/teacher/{id}/reindex`仅教师、幂等排队，复用题目编辑权限。
- `python3 scripts/seed_platform_rag.py --base-url http://127.0.0.1:8080/api/v1 --manifest scripts/fixtures/platform-rag/questions.jsonl`；凭据走环境变量，输出fixtureKey→questionId映射及新增/复用/失败计数，不输出token。

- [ ] 将题库页面AI启用开关、索引状态、失败原因、重试入口连接真实接口；开启时校验文本与解析完整，正常编辑触发新revision。
- [ ] 准备120道自编文字选择题，标记模拟来源，按4个主题/每主题30题；解析检查数值、单位、唯一正确选项、知识点。抽取至少每主题5题人工复核并记录，其余标明审核范围，不将抽检称全部专业审核。
- [ ] 基于已有60名模拟学生建立确定性画像与题目覆盖，使用模拟题manifest管理自身记录；两次导入数量稳定，拒绝覆盖非模拟用户内容。
- [ ] 测试fixture重复、中文搜索、标签不存在、索引失败和少于目标题量；不允许失败后仍输出完成120题。
- [ ] 执行 `python3 -m unittest scripts.test_seed_platform_rag scripts.test_seed_test_data`；导入后检查READY数量和跨两班可见性，保存导入报告。
- [ ] 浏览器核对教师编辑→索引→学生检索新版本的流程；提交。

## Task 8：真实评测、故障和性能

**Files:**
- Create: `docs/ai-evaluation/corpus-v2/{manifest.json,development.jsonl,holdout.jsonl,agent-tasks.jsonl}`、`docs/ai-evaluation/v2-results.md`。
- Modify: `scripts/verification/evaluation_corpus.py`、`evaluate_platform.py`、`score_ai_evaluation.py`、`compare_ai_evaluation.py`、`score_agent_evaluation.py`及对应`test_*.py`。
- Modify: `service/ai/assistant/AssistantRunRepository.java`、`service/ai/agent/LearningAssistantRunRecorder.java`。

**Interfaces:** 每条评测记录包含caseId、split、corpusVersion、codeSHA、model、promptVersion、indexVersion、mode、实际status、候选/引用IDs、工具轨迹、耗时、token与价格版本；审阅结果另存，不伪造缺失项。

- [ ] 先测评分器：FAILED/CANCELLED/无人工评分不计成功；题目级去重；只统计相关题存在的Hit@5分母，空检索任务另算；重复样例与分组跨集泄漏报错。
- [ ] 将100个问答检索任务及20个Agent任务映射到明确fixture ID；固定60/40划分并检查近重复；替换旧讲义任务，但保留v1历史。
- [ ] 使用mock模型跑权限、停机恢复、迟到事件、错误引用与故障测试；Qdrant停止、模型超时、索引中断必须得到定义中的状态。
- [ ] 在非用户测试库的隔离评测schema运行真实模型任务，沿用现有allow-model-calls/max-requests/max-estimated-cost-cny门控；先每模式5例，再按预算扩大。每一批先算请求数和上限；无用量字段则写null。
- [ ] 比较关键词/向量/混合，比较同模型无RAG/RAG，比较同数据WORKFLOW/AGENT；开发集调优后冻结参数再评40例留出。公开每组实际样本数、无结果和失败比例。
- [ ] 按方案指标人工评分并记录reviewer及理由；没有物理专业复核时明确为开发者核对。可选LLM评分仅作辅助，不能冒充人工。
- [ ] 固定机器/模型/并发/版本记录检索、首字和总耗时p50/p95、费用与失败分类；API/数据库吞吐与模型效果分别报告。
- [ ] 发布v2-results和至少5个真实失败案例，检查“已实现”和“已测量”不混写；提交。即使指标未达标也保留原始记录，不能删除失败样例。

## Task 9：运行复现与面试材料

**Files:**
- Modify: `README.md`、`.env.dev.example`。
- Create: `docs/ai-evaluation/interview-walkthrough.md`、`docs/ai-evaluation/architecture-v2.md`、`docs/ai-evaluation/acceptance-v2.md`。

- [ ] 以完整新环境或明确隔离的验证环境，按文档执行启动→迁移→导入→索引→登录；确认无需开发者隐藏的本机路径或SSH隧道才能理解依赖配置。已有隧道可继续用于开发，复现文档需给出本地服务替代方案。
- [ ] 执行 `./mvnw test`、`npm --prefix frontend test`、`npm --prefix frontend run build` 和本次相关E2E；依赖真实MySQL/Qdrant的测试单列，未执行项不得记为通过。
- [ ] 编写8～10分钟演示脚本：平台题库搜索→来源→错题辅导→Agent工具调用→确认计划→补练进度→异常场景→效果对比。
- [ ] 准备架构图、索引更新时序、SQL与RAG边界、工具权限、失败取舍与5个面试追问答案。
- [ ] 根据真实结果编写3条简历描述；不给尚未测试的准确率、吞吐、节省成本或真实用户规模填数。
- [ ] 逐条填写acceptance-v2验收清单，链接测试输出和运行记录；标记未完成项及原因，交付本轮结果。

## 完成和进度口径

每个任务按“未开始 / 实现中 / 待验证 / 已验收 / 受外部依赖阻塞”记录。提供文件或页面只算实现中；真实模型运行、真实数据授权和端到端验收通过才算完成对应能力。RAG与Agent分别验收，模型失败不能用mock演示替代实际成功记录。

当前本轮进度：详细方案和实施计划已编写，任务1～9均未开始。已有项目能力按基线复用，不据此宣称本轮计划已完成。

## 方案覆盖自查

| 方案要求 | 实施任务 |
|---|---|
| 环境、配置与能力状态 | 1 |
| 题库发布、分块、索引更新与故障恢复 | 2、7 |
| SQL/RAG分工、权限和来源引用 | 3、4 |
| 助手界面、错题跳转、会话与取消 | 4 |
| Agent工具边界、固定工作流对照 | 5 |
| 草稿、确认、跨题补练与统计 | 5、6 |
| 模拟数据、留出评测、费用和失败报告 | 7、8 |
| 复现、文档、面试材料 | 9 |

设计已做独立builder check：未发现必须先向用户澄清的重要缺口。供应商连通性、批量调用费用上限与人工复核安排在对应阶段落实。上述检查属于文档审阅，不代表任务代码或模型效果已验收。
