# 最小 RAG 接入实验（T0a / G0）

这是隔离开发实验，未接入学生页面，不是正式知识库或 Agent。目的：用同一份中文物理讲义、同一个生成模型与相同向量，对比直接 HTTP 和 LangChain4j 接入。

## 组成

- RagSpike：按 Markdown 小节切分，计算余弦相似度、选取最多 3 段，组装模型上下文，校验来源编号。
- SpikeAdapters：直接 Java HTTP / LangChain4j 1.22.0 两种生成适配；请求输出上限相同、无自动重试。
- 本地 bge-small-zh-v1.5 量化 Embedding：512 维，LangChain4j 1.22.0-beta32 模型包；不是哈希/关键词假向量。
- SpikeToolProbe：只针对合成 lookup_fixture 的两轮消息协议验证，不是会自主规划的 Agent。
- RagSpikeExperiment：默认不调用模型；显式开启后最多 20 次生成请求，输出权限 0600，已有文件不覆盖。
- fixtures：自编讲义和 10 个开发问题，参考来源只用于检查、不进入生成 Prompt。物理内容由 AI 辅助初核，尚未由用户或教师人工审核。

## 复跑

使用 JDK21。从本目录运行 ../../mvnw -B test。首次需要下载依赖和约 16MB 的中文模型包，ONNX Runtime 另有本地库依赖。主应用 Spring Boot 版本不变。

如果当前网络无法连接 Maven Central，可自行提供公共镜像 settings 并通过 -s 传入；本次执行使用 .local/maven-settings.xml，未改全局 Maven 配置，该本机文件不提交。

在仓库根运行 ../../ 路径以外的命令时，Maven 用 ./mvnw -f experiments/rag-spike/pom.xml。先执行 dependency:build-classpath -Dmdep.outputFile=target/classpath.txt，再以 target/classes 和 classpath.txt 中的依赖构造 Java classpath，主类为 RagSpikeExperiment。

CLI：

- 无参数：输出 Model calls disabled，不读取密钥或加载向量模型。
- --retrieval-only --output PATH：实际运行本地向量检索，不调用生成 API；generationMode=NOT_RUN。
- --allow-model-calls --max-requests 20 --output PATH：依次执行 HTTP/SDK，各 10 个问题；缺少依据不调用生成 API。
- --allow-model-calls --probe-tools --max-requests 4 --output PATH：两个适配各完成一次两轮工具协议检查，共 4 请求。
- --minimum-score 0.60：本开发语料的初始余弦门槛；只能在开发问题调参，不能推广为通用门槛。

凭证仅从 ZHIPU_AI_API_KEY 环境变量读取，绝不放在命令参数、源码、报告或截图中。可通过 SPIKE_BASE_URL / SPIKE_CHAT_MODEL 指定兼容端点/模型。真实端点要求 HTTPS。

## 当前证据

实验合同测试覆盖，涵盖检索选择、证据不足、来源校验、提前取消、真实 HTTP 延迟取消、两适配的消息与用量、工具 ID/结果往返，以及默认不联网和检索输出不冒充生成。

实际本地向量：10 个开发问题中，8 个有依据的问题检索到了预期来源，2 个返回资料不足。仅是已知开发集检查，不能写成正式准确率。

Prompt v1 真实对照：两个适配各执行 10 个问题；每种均 6 个回答带有效编号、2 个资料不足、2 个数值题未带编号而被拒绝。诊断请求确认是模型遗漏引用，不是传输错误。保留失败，改为证据行优先的 Prompt v2 后再验证；不得自动给无来源回答补上引用。

所有原始回答和诊断位于本地 .local/ai-evaluation/，不提交。来源支持性仍需要用户或教师人工核对；模型输出格式校验并不能证明物理结论正确。

## 边界

没有持久化、班级权限、文档版本切换、用户上传、前端来源卡或可恢复的工具任务。索引在内存，重复计算向量通过本次进程缓存避免；实验耗时包含向量加载/缓存影响，不用于宣布哪种传输更快。

云端 embedding-3 本次返回 429/1113；没有暗中降级或重试，而是明确改用本地中文模型。正式模块应通过 EmbeddingGateway 区分 provider=local/api，并测量资源。

SDK 取消完成与真正取消上游网络/计费不是同一件事；只确认合同测试覆盖的行为。最终适配决策与风险见 docs/ai-evaluation/dependency-decisions.md。
