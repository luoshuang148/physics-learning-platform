# v2 验收清单（acceptance-v2）

日期：2026-10-24。状态：`[x]` 通过 / `[ ]` 未完成 / `[-]` 不适用。
每项链接到测试输出或运行记录；未完成项写明原因。

## A. 三条学生流程

| # | 项 | 状态 | 证据 |
|---|---|---|---|
| A1 | 自然语言搜题 + 来源卡 + 查看原题 | [x] | `POST /platform-search` 返回 MATCHED；`GET /platform-sources/{id}` 200 |
| A2 | 无匹配文案（不编造题号） | [x] | 「弦论」→ INSUFFICIENT；评测 noResult 6/6 |
| A3 | 检索故障文案不同于无匹配 | [x] | UNAVAILABLE → 「检索暂不可用」；单测 `unavailableDistinctMessage` |
| A4 | 错题入口携带 recordId | [x] | `AssistantMessageRequest.recordId`；会话按 recordId 绑定 |
| A5 | 生成计划 1～7 天、≤6 题 | [x] | `validateFromRegistry` 限制 |
| A6 | 草稿→激活→补练→进度归属 planItemId | [x] | `PlanPracticeServiceTest` 5 项 + V19 |

## B. 权限与版本

| # | 项 | 状态 | 证据 |
|---|---|---|---|
| B1 | 跨用户来源拒绝且不泄露 | [x] | `sourceAuthorizationDenied`；HTTP 4001 |
| B2 | 待考题保护（未首答） | [x] | `pendingAssignmentProtected` |
| B3 | 禁用题不进候选 | [x] | `disabledQuestionExcluded` |
| B4 | 旧 revision 来源拒绝 | [x] | `isEligible` revision 校验 |
| B5 | 模型不能指定 userId | [x] | `modelCannotOverrideAuthenticatedViewer` |
| B6 | 题目更新后拒绝新练习 | [x] | `revisionChangedRejects` |

## C. RAG 与索引

| # | 项 | 状态 | 证据 |
|---|---|---|---|
| C1 | 分块：LaTeX 完整、长解析不截断 | [x] | `QuestionSourceBuilderTest` 6 项 |
| C2 | 索引租约/重试/退避 | [x] | `QuestionIndexConsistencyTest` 10 项 |
| C3 | 旧任务晚完成不发布 | [x] | `lateJobDoesNotPublish` |
| C4 | MySQL 检索单元一致性 | [x] | `QuestionIndexMySqlTest` 5 项（真实 MySQL） |
| C5 | 向量 collection 独立于文档集合 | [x] | `q_qidx_*` vs `knowledge_idx_*` |
| C6 | FULLTEXT 不可用时如实降级 | [x] | `actualMode=lexical_only`，日志可见 |

## D. Agent 与工具

| # | 项 | 状态 | 证据 |
|---|---|---|---|
| D1 | 真实 Tool Calling（GLM） | [x] | 2026-10-24 冒烟：`get_learning_summary` + `search_platform_questions` + `plan` + `complete` |
| D2 | 工具白名单参数 | [x] | `rejectsUnknownToolAndFields` |
| D3 | 重复工具调用被拒 | [x] | `duplicateToolCallStopsWithoutReexecutingIt` |
| D4 | 计划只引用 registry 候选 | [x] | `validateFromRegistry` + `PlanCandidateRegistryTest` |
| D5 | 候选并集去重 | [x] | `unionAndDedupe` |
| D6 | WORKFLOW 对照模式 | [x] | `LearningWorkflowContractTest` |

## E. 评测与性能

| # | 项 | 状态 | 证据 |
|---|---|---|---|
| E1 | 语料 v2 冻结划分 | [x] | `corpus-v2/` 30 开发 / 20 留出 + 10 计划 |
| E2 | Hit@5 实测 | [x] | dev 100%（27/27），holdout 100%（17/17） |
| E3 | 无结果处理实测 | [x] | 6/6，100% |
| E4 | 引用结构有效 | [x] | 100%（216/216） |
| E5 | 延迟 p50/p95 + 样本数 | [x] | dev 57/91ms (n=30)，holdout 45/85ms (n=20) |
| E6 | 失败样例公开 | [x] | `v2-results.md` 失败列表（当前为空，历史失败已修） |
| E7 | Agent 计划任务对比评测 | [ ] | 仅做了 1 次真实冒烟；20 个 agent 任务的批量评分未跑 |
| E8 | 人工评分（物理正确/证据/答问） | [ ] | 开发者核对；无物理专业复核，已标注 |

## F. 复现与交付

| # | 项 | 状态 | 证据 |
|---|---|---|---|
| F1 | 配置绑定测试 | [x] | `AiEnvironmentBindingTest` 9 项 |
| F2 | 只读环境检查脚本 | [x] | `check_ai_environment.py` |
| F3 | 120 题可复现生成器 | [x] | `generate_questions.py`（固定种子） |
| F4 | 一键导入 | [x] | `seed_platform_rag.py` |
| F5 | 演示脚本 | [x] | `interview-walkthrough.md` |
| F6 | 架构文档 | [x] | `architecture-v2.md` |
| F7 | 单元测试全绿 | [x] | 683 项 |
| F8 | `./mvnw test` 全量 + E2E | [ ] | 单元已跑；`npm test:e2e` 本次未执行（前端 E2E 环境未起） |
| F9 | 干净环境复现验证 | [ ] | 本机开发库验证；全新环境未复跑 |

## 未完成原因

1. **E7**：批量 Agent 评测需配预算上限后跑 20 任务；当前只保留 1 次真实冒烟记录。
2. **E8**：无人工物理教师复核，评测标注为开发者核对。
3. **F8**：前端 E2E 需要 `E2E_BASE_URL` 指向运行中的前端，本次交付优先后端与检索评测。
4. **F9**：干净环境复现留给部署前做，文档已写步骤。

## 诚实声明

- 所有指标来自本机实测，不是目标值；目标见 `corpus-v2/manifest.json`。
- 120 题为自编演示题，非真题；4 题因内容重复未入库（116 题 READY）。
- 无真实用户规模数据，简历表述不含未测数字。
