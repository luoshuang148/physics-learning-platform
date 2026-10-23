# AI 升级依赖与能力决策

状态：T0 初步验证，最终适配方式由 T0a 同题实验决定。日期：2026-10-23。

| 组件 | 锁定候选 | 验证 / 限制 |
|---|---|---|
| Java / Spring Boot | Java 21 / Spring Boot 3.3.8 | 原应用不升级框架；编译使用本地 Homebrew JDK21 |
| PDFBox | 3.0.8 | 官方下载及 Maven 元数据已核对，T3 编译与提取测试前不声称兼容验证完成 |
| Qdrant | v1.19.2 | 官方 release 已核对；T4 在容器中验证 API/持久化，不使用 latest |
| Java 模型适配 | LangChain4j 1.22.0 与直接 HTTP | Maven 元数据存在；T0a 对比依赖、调用、来源和取消；不加入 Spring Boot 自动配置模块 |
| 云端聊天/工具 | glm-4-flash-250414 | 一次真实工具调用成功，usage 120 tokens；后续效果与性能另测 |
| 云端向量 | embedding-3，探测请求 dimensions=256 | HTTP 429/code 1113，未返回向量；不作为已可用配置 |
| 本地中文向量候选 | bge-small-zh-v1.5 quantized，512 维 | 官方 Java 模型文档支持进程内推理；版本、依赖、实际向量和性能由 T0a 验证 |

## 决策原则

模型生成 API、向量计算、工具执行相互独立。云端向量失败时，可以由同一个 EmbeddingGateway 接入本地模型；比较 HTTP/框架时使用相同向量，避免混淆检索质量与接入代码差异。云服务不可用不得记成通过。

只在本地实验 pom 增加候选依赖；应用 pom 暂不改。Maven Central 从本机 TLS 连接超时，候选包元数据通过公共 Aliyun Maven 镜像核对。需要下载时使用本计划私有临时 Maven settings，不改用户全局配置。

没有获得可核对的该账户价格快照，因此当前只执行严格限定请求数和输出长度的合成能力探测，不声称已实现硬费用上限。真实评测 runner 的预算合同在 T8 实现。

## 来源

- [PDFBox 官方下载](https://pdfbox.apache.org/download.cgi)
- [Qdrant 官方 releases](https://github.com/qdrant/qdrant/releases)
- [LangChain4j 入门](https://docs.langchain4j.dev/get-started/)
- [本地中文 BGE 向量模型](https://docs.langchain4j.dev/apidocs/dev/langchain4j/model/embedding/onnx/bgesmallzhv15q/BgeSmallZhV15QuantizedEmbeddingModel.html)
- [智谱 Embedding-3 文档入口](https://bigmodel.cn/dev/api/vector/embedding-3)
