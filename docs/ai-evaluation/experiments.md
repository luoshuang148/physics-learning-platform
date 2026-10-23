# 实验执行登记

## 已有实验

G0：详见 g0-results.md。它使用已知10个开发问题，不使用 corpus-v1 留出集。v1引文遗漏、v2格式改善与摩擦反例全部保留，不能只展示最好结果。

## 版本化主评测

- corpus-v1：开发60、留出40，数据与资料内容哈希位于 manifest.json。
- 当前状态：数据结构、来源锚点、分割、金标准隔离已验证；人工复核与正式模型评测未完成。
- 生成来源：AI辅助生成、自编参数化案例；没有真实学生资料或教师成绩，不能表述为课堂实验数据。
- 开发运行：A 旧链路无检索，B 同模型纯向量，C 同模型混合检索；冻结配置后只比较A和最终方案的留出表现。
- T8将提供正式平台runner，目前不把G0的独立Prompt结果当作平台效果。
- 每次执行登记代码SHA/工作树状态、语料/资料/索引/Embedding版本、Prompt版本、模型、参数、时间、请求数和用量来源。
- 价格缺失不记为免费，未知usage不记零；保留失败、取消、重试和未评分项。

## T8 平台评测工具准备（2026-10-23）

- 已新增隔离平台 runner、人工 review scorer 与保留全分母的 compare 工具；model call 默认关闭，并强制 loopback、请求预算、费用估算与价格版本。
- runner 支持 baseline / vector / hybrid 业务模式及 development / holdout 分割；`--tuning` 明确拒绝 holdout。缺少隔离业务作答记录的案例会标为 unmapped，不调用模型。
- 无网络准备结果：development 三种模式各 60 条，均为 prepared / 0 reviewed。输出仅存于忽略的 `.local/`，不代表生成效果。
- 仍未做任何真实平台模型调用或教师评分。执行前需给语料案例建立隔离 `questionId`/`recordId` 映射、启用知识服务和索引；留出集待开发配置冻结后单独运行。

## 初步内容复核清单

优先复核6份短讲义的适用条件与公式，其次核对计算题单位、摩擦反例、缺图/缺条件处理。没有人工评分前，所有accuracy/引用支持率位置显示pending；可以单独报告确定性的接口测试、检索来源命中与格式校验结果。

## T5 检索实现验证（2026-10-23）

- 实现口径：MySQL FULLTEXT ngram 词法检索与按活动 revision 隔离的 Qdrant 向量检索；RRF 常数 60，结果最多 5 段，每个分支最多 10 个候选。结果附原始排名/分数与融合值，融合值仅用于排序诊断，不解释为概率。
- 安全边界：两路均按事件班级和未撤回活动 revision 检索；进入结果前再次从 MySQL 读取并校验当前活动版本、READY 状态和读权限。资料不足为 INSUFFICIENT，词法/索引/Embedding 服务故障为 UNAVAILABLE；只有显式打开配置后，混合检索才允许标记 lexical_only。
- 确定性检查：RetrievalPolicyTest + KnowledgeRetrievalTest 为 4/4；真实 MySQL 8.0.46 中文 ngram 查询/班级隔离/活动版本/撤回过滤为 1/1；完整后端测试 1022 项通过。
- 未执行：corpus-v1 development.jsonl 的 keyword/vector/hybrid Recall@5 对照、门槛误拒分析。当前资料与问题仍待教师审核，0.60 仅沿用 G0 的窄范围初始阈值，不声称经过 60 例校准。后续评测只读取 development.jsonl，严禁读取 holdout.jsonl 来调阈值。
