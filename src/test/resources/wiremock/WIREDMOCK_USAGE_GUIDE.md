# WireMock 外部服务模拟使用指南

## 概述

本项目使用 WireMock 来模拟外部API 服务（如阿里云 AI、智谱 AI 等），实现测试的隔离性和可重复性。

## 快速开始

### 1. 继承基类

```java
import com.zhixuewuli.common.base.WireMockTestBase;

public class AIServiceIntegrationTest extends WireMockTestBase {
    // 自动获得 WireMock 支持
}
```

### 2. 使用静态存根文件

在 `src/test/resources/wiremock/__files/` 目录下创建响应文件：

**示例：aliyun-ai-response.json**
```json
{
  "id": "mock-chatcmpl-test123",
  "object": "chat.completion",
  "created": 1709564800,
  "model": "qwen-turbo",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "这是模拟的 AI 回答"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 50,
    "completion_tokens": 100,
    "total_tokens": 150
  }
}
```

### 3. 配置映射关系

在 `src/test/resources/wiremock/mappings/` 目录创建映射文件：

**示例：aliyun-ai-mapping.json**
```json
{
  "request": {
    "method": "POST",
    "urlPathPattern": "/api/v1/services/aigc/text-generation/generation",
    "headers": {
      "Content-Type": {
        "equalTo": "application/json"
      }
    }
  },
  "response": {
    "status": 200,
    "bodyFileName": "aliyun-ai-response.json",
    "headers": {
      "Content-Type": "application/json"
    }
  }
}
```

### 4. 编程方式添加 Stubbing

```java
import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Test
void testAIResponse() {
    stubFor(post("/v1/chat/completions")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"choices\":[{\"message\":{\"content\":\"AI 回答\"}}]}")));
    
    // 执行测试逻辑
}
```

## 核心特性

### 自动生命周期管理

- **启动**：`@BeforeAll` 自动启动 WireMock 服务器（端口 8089）
- **重置**：`@BeforeEach` 自动清理所有 stubs，确保测试隔离
- **停止**：`@AfterAll` 自动停止服务器

### 端口配置

在 `application-test.yml` 中配置：
```yaml
test:
  wiremock:
    port: 8089
    enabled: true
```

### 文件存根位置

```
src/test/resources/wiremock/
├── __files/           # 响应体文件
│   ├── aliyun-ai-chat-response.json
│   └── aliyun-ai-report-response.json
└── mappings/          # 请求映射
    ├── aliyun-ai-chat-mapping.json
    └── aliyun-ai-report-mapping.json
```

## 使用场景

### 场景 1：AI 对话服务测试

```java
public class AIChatIntegrationTest extends WireMockTestBase {
    
    @Test
    void testRealTimeQA() {
        // 设置 AI 响应存根
        stubFor(post(urlPathMatching("/api/v1/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("""
                {
                  "choices": [{
                    "message": {
                      "content": "牛顿第二定律公式为 F=ma"
                    }
                  }]
                }
                """)));
        
        // 调用 AI 服务
        String response = aiService.ask("什么是牛顿第二定律？");
        
        // 验证结果
        assertThat(response).contains("F=ma");
    }
}
```

### 场景 2：学习报告生成测试

```java
public class ReportGenerationTest extends WireMockTestBase {
    
    @Test
    void testGenerateReport() {
        // 使用预定义的报告响应
        // 文件位于：__files/aliyun-ai-report-response.json
        
        StudentReport report = reportService.generateReport(studentId);
        
        assertThat(report.getContent())
            .contains("答题情况分析")
            .contains("正确率");
    }
}
```

### 场景 3：错误处理测试

```java
@Test
void testAIServiceTimeout() {
    // 模拟超时
    stubFor(post("/api/ai/chat")
        .willReturn(aResponse()
            .withStatus(504)
            .withBody("Gateway Timeout")));
    
    // 验证异常处理
    assertThatThrownBy(() -> aiService.ask("问题"))
        .isInstanceOf(AITimeoutException.class);
}
```

### 场景 4：多次调用测试

```java
@Test
void testMultipleAICalls() {
    // 设置多个不同的响应
    stubFor(post("/api/ai/chat")
        .inScenario("MultiTurnConversation")
        .whenScenarioStateIs("STARTED")
        .willReturn(aResponse()
            .withBody("{\"content\":\"第一次回答\"}"))
        .willSetStateTo("SECOND_CALL"));
    
    stubFor(post("/api/ai/chat")
        .inScenario("MultiTurnConversation")
        .whenScenarioStateIs("SECOND_CALL")
        .willReturn(aResponse()
            .withBody("{\"content\":\"第二次回答\"}"))
        .willSetStateTo("COMPLETED"));
    
    // 测试多轮对话
    String first = aiService.ask("第一个问题");
    String second = aiService.ask("第二个问题");
    
    assertThat(first).contains("第一次");
    assertThat(second).contains("第二次");
}
```

## 高级用法

### 请求验证

```java
@Test
void testRequestParameters() {
    // 执行调用
    aiService.askWithImage("问题", "base64-image-data");
    
    // 验证请求参数
    verify(postRequestedFor(urlEqualTo("/api/ai/chat"))
        .withRequestBody(matchingJsonPath("$.prompt", equalTo("问题")))
        .withRequestBody(matchingJsonPath("$.image"))
        .withHeader("Authorization", matching("Bearer .*")));
}
```

### 延迟模拟

```java
@Test
void testSlowAIResponse() {
    stubFor(post("/api/ai/chat")
        .willReturn(aResponse()
            .withFixedDelay(2000)  // 延迟 2 秒
            .withBody("{\"content\":\"慢速回答\"}")));
    
    // 测试超时处理逻辑
    long startTime = System.currentTimeMillis();
    aiService.ask("问题");
    long duration = System.currentTimeMillis() - startTime;
    
    assertThat(duration).isGreaterThan(2000);
}
```

### 动态响应

```java
@Test
void testDynamicResponse() {
    stubFor(post("/api/ai/chat")
        .willReturn(aResponse()
            .withTransformerParameter("echoRequest", true)
            .withBodyFile("dynamic-response-template.hbs")));
}
```

## 现有存根文件说明

### 1. aliyun-ai-chat-response.json

**用途**：模拟阿里云 AI 对话服务的标准响应

**字段说明**：
- `id`: 模拟的会话 ID
- `model`: 使用的模型（qwen-turbo）
- `choices[0].message.content`: AI 回答内容
- `usage`: Token 使用统计

**适用场景**：实时问答、错题分析等对话场景

### 2. aliyun-ai-report-response.json

**用途**：模拟学生学习报告生成

**内容结构**：
- 基本信息（姓名、班级）
- 答题情况统计
- 知识点掌握分析
- 常见错误类型
- 学习建议和教师评语

**适用场景**：学生报告生成、学习分析等功能

## 故障排查

### 问题 1：WireMock 未启动

**现象**：测试时连接被拒绝

**解决**：
```java
// 确保继承 WireMockTestBase
public class MyTest extends WireMockTestBase {
    // 或在测试前手动启动
    @BeforeAll
    static void setup() {
        WireMockTestBase.startWireMockServer();
    }
}
```

### 问题 2：Stubbing 不匹配

**现象**：返回 404 或默认响应

**解决**：
1. 检查请求方法（GET/POST/PUT）
2. 验证 URL 路径是否匹配
3. 确认请求头格式
4. 使用 `verify()` 查看实际请求

```java
// 查看所有未匹配的请求
List<ServeEvent> unmatched = wireMockServer.getAllServeEvents().stream()
    .filter(e -> e.getRequest().wasNotMatched())
    .collect(Collectors.toList());
```

### 问题 3：响应文件格式错误

**现象**：JSON 解析失败

**解决**：
- 使用 JSON 验证工具检查语法
- 确保转义字符正确
- 避免注释和尾随逗号

## 最佳实践

1. **优先使用静态文件**：便于维护和复用
2. **保持响应真实**：模拟真实的 API响应格式
3. **测试隔离**：每个测试独立设置 stubs
4. **命名规范**：文件名包含服务名和用途
5. **文档化**：在响应文件中添加注释说明

## 与 Mockito 的区别

| 特性 | WireMock | Mockito |
|------|----------|---------|
| 层级 | HTTP 级别 | Java 方法级别 |
| 适用场景 | 外部 HTTP 服务 | Java 依赖对象 |
| 配置方式 | JSON/编程 | Java 代码 |
| 真实性 | 更接近生产环境 | 完全模拟 |

## 相关资源

- [WireMock 官方文档](https://wiremock.org/docs/)
- [WireMockTestBase 源码](../java/com/zhixuewuli/common/base/WireMockTestBase.java)
- [现有存根文件](../resources/wiremock/)
