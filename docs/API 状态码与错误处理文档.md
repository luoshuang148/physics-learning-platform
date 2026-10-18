# 智学物理 API 状态码与错误处理文档

> 本文档详细说明了智学物理后端 API 的 HTTP 状态码、业务状态码及错误处理机制，帮助前端开发人员快速理解和处理各种响应情况。

## 目录

- [响应格式说明](#响应格式说明)
- [HTTP 状态码概览](#http-状态码概览)
- [业务状态码详解](#业务状态码详解)
- [错误处理机制](#错误处理机制)
- [各模块常见错误示例](#各模块常见错误示例)
- [前端处理建议](#前端处理建议)

---

## 响应格式说明

### 成功响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { /* 实际数据 */ },
  "timestamp": 1710000000000,
  "reason": null
}
```

### 错误响应格式

```json
{
  "code": 4001,
  "message": "业务逻辑错误",
  "data": null,
  "timestamp": 1710000000000,
  "reason": "附加的错误原因信息"
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | **业务状态码**，用于标识具体的错误或成功状态 |
| `message` | string | 错误描述信息，可用于用户提示 |
| `data` | object/null | 响应数据，成功时包含实际数据，失败时为 null |
| `timestamp` | long | 响应时间戳（毫秒） |
| `reason` | string/null | 附加的错误原因，提供更详细的调试信息 |

---

## HTTP 状态码概览

系统遵循 RESTful 规范使用标准 HTTP 状态码：

| HTTP 状态码 | 状态 | 含义 | 使用场景 |
|------------|------|------|----------|
| **200 OK** | 成功 | 请求成功处理 | GET/PUT/PATCH 成功 |
| **201 Created** | 成功 | 资源创建成功 | POST 创建新资源 |
| **400 Bad Request** | 客户端错误 | 请求参数错误 | 参数校验失败、JSON 格式错误 |
| **401 Unauthorized** | 客户端错误 | 未授权访问 | 令牌无效、密码错误 |
| **403 Forbidden** | 客户端错误 | 权限不足 | 用户角色权限不足、账户被禁用 |
| **404 Not Found** | 客户端错误 | 资源不存在 | 请求的资源 ID 不存在 |
| **409 Conflict** | 客户端错误 | 资源冲突 | 用户名重复、唯一约束违反 |
| **429 Too Many Requests** | 客户端错误 | 请求过多 | AI 配额不足、限流触发 |
| **500 Internal Server Error** | 服务器错误 | 系统内部错误 | 代码逻辑错误、未知异常 |
| **503 Service Unavailable** | 服务器错误 | 服务不可用 | AI 服务宕机、第三方服务故障 |

---

## 业务状态码详解

业务状态码采用分层设计，便于精确定位问题来源：

### 状态码分类规则

| 编码范围 | 分类 | 说明 |
|---------|------|------|
| **2xx** | 成功状态码 | HTTP 标准成功响应 |
| **4xx** | 客户端错误 | 请求参数、认证、权限等错误 |
| **5xx** | 服务器错误 | 系统技术层面错误 |
| **4000-4099** | 通用业务错误 | 跨模块的通用业务规则错误 |
| **4100-4199** | 用户模块错误 | 用户相关错误 |
| **4200-4299** | 题目模块错误 | 题目相关错误 |
| **4300-4399** | 视频模块错误 | 视频相关错误 |
| **4400-4499** | 答题记录模块错误 | 答题记录相关错误 |
| **4500-4599** | AI 模块错误 | AI 对话相关错误 |
| **4600-4699** | 实验管理模块错误 | 实验相关错误 |
| **4700-4799** | 教师事件模块错误 | 教师事件相关错误 |
| **4800-4899** | 权限模块错误 | 权限相关错误 |
| **4900-4999** | 参数校验错误 | 参数验证相关错误 |
| **5000-5099** | 通用技术错误 | 系统级技术错误 |
| **5100-5199** | 用户模块技术错误 | 用户服务技术错误 |
| **5200-5299** | 题目模块技术错误 | 题目服务技术错误 |
| **5300-5399** | 视频模块技术错误 | 视频服务技术错误 |
| **5400-5499** | 答题记录模块技术错误 | 答题记录服务技术错误 |
| **5500-5599** | AI 模块技术错误 | AI 服务技术错误 |
| **5600-5699** | 错题分析技术错误 | 错题分析服务技术错误 |
| **5700-5799** | 教师事件技术错误 | 教师事件服务技术错误 |
| **5800-5899** | 实验管理技术错误 | 实验管理服务技术错误 |

---

### 通用业务错误码 (4000-4099)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4001 | BUSINESS_ERROR | 400 | 业务逻辑错误 | 显示错误提示，检查业务逻辑 |
| 4002 | SESSION_STATUS_INVALID | 400 | 会话状态不正确 | 刷新页面或重新登录 |
| 4003 | SESSION_NOT_FOUND | 404 | 会话不存在 | 跳转登录页 |

---

### 用户模块错误码 (4100-4199)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4102 | USER_NOT_FOUND | 404 | 用户不存在 | 提示用户不存在 |
| 4103 | USER_ALREADY_EXISTS | 409 | 用户已存在 | 提示更换用户名 |
| 4104 | USER_ACCOUNT_LOCKED | 403 | 用户账户已锁定 | 联系管理员解锁 |
| 4105 | USER_PASSWORD_INCORRECT | 401 | 密码错误 | 提示重新输入密码 |
| 4106 | USER_DISABLED | 403 | 用户已被禁用 | 提示联系管理员 |
| 4107 | USER_NAME_DUPLICATE | 409 | 用户名称重复 | 提示更换用户名 |
| 4108 | REAL_NAME_DUPLICATE | 409 | 真实姓名重复 | 提示联系管理员核实 |
| 4109 | USER_NAME_INVALID | 400 | 用户名称不合法 | 提示用户名格式要求 |
| 4110 | REAL_NAME_INVALID | 400 | 真实姓名不合法 | 提示真实姓名格式要求 |
| 4111 | INVALID_TOKEN | 401 | 令牌无效或已过期 | **跳转登录页** |

**典型场景：**
- 登录失败 → 401 + 4105（密码错误）
- Token 过期 → 401 + 4111（令牌无效）
- 注册重名 → 409 + 4107（用户名重复）

---

### 题目模块错误码 (4200-4299)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4202 | QUESTION_NOT_FOUND | 404 | 题目不存在 | 提示题目已被删除 |
| 4203 | QUESTION_CONTENT_DUPLICATE | 409 | 题目内容重复 | 提示修改题目内容 |
| 4204 | QUESTION_VIDEO_NOT_FOUND | 404 | 关联视频不存在 | 提示先创建视频或选择其他视频 |

**典型场景：**
- 查看不存在的题目 → 404 + 4202
- 创建重复题目 → 409 + 4203

---

### 视频模块错误码 (4300-4399)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4302 | VIDEO_NOT_FOUND | 404 | 视频不存在 | 提示视频已被删除 |
| 4303 | VIDEO_ALREADY_EXISTS | 409 | 视频已存在 | 提示修改视频标题 |
| 4305 | VIDEO_FORMAT_UNSUPPORTED | 400 | 视频格式不支持 | 提示支持的视频格式（如 mp4） |

**典型场景：**
- 上传不支持格式 → 400 + 4305
- 访问不存在的视频 → 404 + 4302

---

### 答题记录模块错误码 (4400-4499)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4402 | ANSWER_RECORD_NOT_FOUND | 404 | 答题记录不存在 | 提示记录不存在 |

---

### AI 模块错误码 (4500-4599)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4508 | SESSION_EXPIRED | 408 | 会话已失效 | 重新创建会话 |
| 4509 | INVALID_QUESTION_ID | 400 | 题目 ID 为空 | 检查题目 ID 参数 |

**典型场景：**
- AI 会话过期 → 408 + 4508（需重新创建会话）
- 未传题目 ID → 400 + 4509

---

### 实验管理模块错误码 (4600-4699)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4601 | EXPERIMENT_ALREADY_EXISTS | 409 | 实验已存在（标题重复） | 提示修改实验标题 |
| 4602 | EXPERIMENT_NOT_FOUND | 404 | 实验不存在 | 提示实验已被删除或不存在 |
| 4603 | EXPERIMENT_SERIAL_DUPLICATE | 409 | 实验序号重复 | 系统内部错误，需联系管理员 |

**典型场景**:
- 创建重名实验 → 409 + 4601
- 访问不存在的实验 → 404 + 4602

---

### 教师事件模块错误码 (4700-4799)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4702 | TEACHER_EVENT_ALREADY_EXISTS | 409 | 全局事件已存在 | 提示事件已存在，无法重复创建 |
| 4703 | TEACHER_EVENT_NOT_FOUND | 404 | 全局事件不存在 | 提示事件不存在 |

---

### 权限模块错误码 (4800-4899)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4801 | PERMISSION_DENIED | 403 | 权限不足 | 提示无权限访问 |
| 4802 | INSUFFICIENT_TEACHER_PERMISSION | 403 | 教师权限不足 | 提示需要教师角色 |
| 4803 | INSUFFICIENT_STUDENT_PERMISSION | 403 | 学生权限不足 | 提示需要学生角色 |
| 4804 | INSUFFICIENT_ADMIN_PERMISSION | 403 | 管理员权限不足 | 提示需要管理员角色 |

**典型场景：**
- 学生访问教师接口 → 403 + 4802
- 未授权访问管理接口 → 403 + 4804

---

### 参数校验错误码 (4900-4999)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 4900 | VALIDATION_FAILED | 400 | 参数校验失败 | 显示具体验证错误信息 |
| 4901 | MISSING_PARAMETER | 400 | 缺少必需参数 | 补充缺失参数 |
| 4902 | PARAMETER_TYPE_MISMATCH | 400 | 参数类型不匹配 | 检查参数类型 |
| 4903 | PARAMETER_FORMAT_INVALID | 400 | 参数格式错误 | 检查参数格式（如邮箱、日期） |
| 4904 | PARAMETER_OUT_OF_RANGE | 400 | 参数值超出范围 | 调整参数值到允许范围 |
| 4905 | PARAMETER_LENGTH_INVALID | 400 | 参数长度不合法 | 调整参数长度 |
| 4906 | PARAMETER_CONTENT_INVALID | 400 | 参数内容不合法 | 修正参数内容 |
| 4907 | REQUEST_BODY_PARSE_FAILED | 400 | 请求体解析失败 | **检查 JSON 格式** |
| 4908 | BINDING_EXCEPTION | 400 | 数据绑定异常 | 检查请求参数与 DTO 匹配 |
| 4909 | CONSTRAINT_VIOLATION | 400 | 约束条件违反 | 检查约束条件（如@NotNull） |

**典型场景：**
- JSON 格式错误 → 400 + 4907
- 必填字段为空 → 400 + 4900（带详细错误信息）
- 邮箱格式错误 → 400 + 4903

---

### 通用技术错误码 (5000-5099)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 5001 | TECHNICAL_ERROR | 500 | 系统内部错误 | 提示系统繁忙，稍后重试 |
| 5002 | DATABASE_CONNECTION_FAILED | 500 | 数据库连接失败 | 提示系统维护中 |
| 5003 | DATABASE_ACCESS_FAILED | 500 | 数据库访问失败 | 提示系统维护中 |
| 5004 | DATABASE_CONSTRAINT_VIOLATION | 409 | 数据库约束违反 | 显示具体约束冲突信息 |
| 5005 | THIRD_PARTY_SERVICE_ERROR | 500 | 第三方服务调用失败 | 提示服务暂时不可用 |
| 5006 | FILE_SYSTEM_ERROR | 500 | 文件系统错误 | 提示文件操作失败 |
| 5007 | NETWORK_COMMUNICATION_ERROR | 500 | 网络通信错误 | 提示网络连接失败 |
| 5008 | SYSTEM_RESOURCE_INSUFFICIENT | 500 | 系统资源不足 | 提示系统繁忙 |

---

### 用户模块技术错误码 (5100-5199)

| 业务码 | 名称 | HTTP 状态 | 说明 |
|-------|------|----------|------|
| 5101 | USER_STATUS_INVALID | 500 | 用户状态不正确 |

---

### 题目模块技术错误码 (5200-5299)

| 业务码 | 名称 | HTTP 状态 | 说明 |
|-------|------|----------|------|
| 5201 | QUESTION_STATUS_INVALID | 500 | 题目状态不正确 |

---

### 视频模块技术错误码 (5300-5399)

| 业务码 | 名称 | HTTP 状态 | 说明 |
|-------|------|----------|------|
| 5301 | VIDEO_STATUS_INVALID | 500 | 视频状态不正确 |
| 5304 | VIDEO_UPLOAD_FAILED | 500 | 视频上传失败 |

---

### 答题记录模块技术错误码 (5400-5499)

| 业务码 | 名称 | HTTP 状态 | 说明 |
|-------|------|----------|------|
| 5401 | ANSWER_RECORD_STATUS_INVALID | 500 | 答题记录状态不正确 |
| 5403 | ANSWER_RECORD_PROCESSING_FAILED | 500 | 答题记录处理失败 |

---

### AI 模块技术错误码 (5500-5599)

| 业务码 | 名称 | HTTP 状态 | 说明 | 前端处理建议 |
|-------|------|----------|------|-------------|
| 5501 | AI_SERVICE_UNAVAILABLE | 503 | AI 服务不可用 | 提示 AI 服务暂时不可用 |
| 5502 | AI_TIMEOUT | 504 | AI 响应超时 | 提示 AI 响应超时，建议重试 |
| 5503 | AI_PARSE_ERROR | 500 | AI 响应解析失败 | 提示系统解析失败 |
| 5504 | AI_QUOTA_EXCEEDED | 429 | AI 配额不足 | 提示配额不足，稍后重试 |
| 5505 | AI_MODEL_UNSUPPORTED | 500 | AI 模型不支持 | 提示模型配置错误 |
| 5506 | SESSION_OPERATION_FAILED | 500 | 会话操作失败 | 提示会话操作失败 |
| 5507 | TOKEN_CALCULATION_ERROR | 500 | Token 计算错误 | 提示系统计算错误 |
| 5508 | TOKEN_QUOTA_EXCEEDED | 429 | Token 配额不足 | 提示 Token 配额不足，稍后重试 |

**典型场景：**
- AI 服务宕机 → 503 + 5501
- AI 响应超时 → 504 + 5502（请求超时）

---

### 错题分析技术错误码 (5600-5699)

| 业务码 | 名称 | HTTP 状态 | 说明 |
|-------|------|----------|------|
| 5601 | QUESTION_ERROR_ANALYSIS_SERVICE_UNAVAILABLE | 503 | 错题分析服务不可用 |

---

### 教师事件技术错误码 (5700-5799)

| 业务码 | 名称 | HTTP 状态 | 说明 |
|-------|------|----------|------|
| 5701 | TEACHER_EVENT_STATUS_INVALID | 500 | 全局事件不正确 |

---

### 实验管理技术错误码 (5800-5899)

| 业务码 | 名称 | HTTP 状态 | 说明 |
|-------|------|----------|------|
| 5801 | EXPERIMENT_STATUS_INVALID | 500 | 实验状态不正确 |
| 5802 | EXPERIMENT_STEP_INVALID | 500 | 实验步骤格式错误 |
| 5803 | EXPERIMENT_QUESTION_ASSOCIATION_FAILED | 500 | 实验题目关联失败 |

---

## 错误处理机制

### 全局异常处理器

系统使用 `GlobalExceptionHandler` 统一处理所有异常，确保返回格式一致：

1. **参数验证异常** → 最优先处理
2. **各模块业务异常** → 按模块分类处理
3. **技术异常** → 系统级异常处理
4. **未知异常兜底** → 最终异常捕获

### 异常处理优先级

```
参数校验异常 (最高优先级)
    ↓
认证/权限异常
    ↓
各模块业务异常
    ↓
技术异常
    ↓
未知异常 (最低优先级/兜底)
```

---

## 各模块常见错误示例

### 1. 用户认证模块

#### 登录成功
```json
// HTTP 200 OK
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "userId": 1,
    "username": "zhangsan",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

#### 密码错误
```json
// HTTP 401 Unauthorized
{
  "code": 4105,
  "message": "密码错误",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

#### Token 过期
```json
// HTTP 401 Unauthorized
{
  "code": 4111,
  "message": "令牌无效或已过期",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

#### 用户名重复
```json
// HTTP 409 Conflict
{
  "code": 4107,
  "message": "用户名称重复",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 2. 题目管理模块

#### 创建题目成功
```json
// HTTP 201 Created
{
  "code": 201,
  "message": "题目创建成功",
  "data": {
    "id": 1,
    "title": "牛顿第一定律",
    "content": "...",
    "answer": "..."
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

#### 题目不存在
```json
// HTTP 404 Not Found
{
  "code": 4202,
  "message": "题目不存在",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

#### 题目内容重复
```json
// HTTP 409 Conflict
{
  "code": 4203,
  "message": "题目内容重复",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 3. AI 对话模块

#### AI 分析成功
```json
// HTTP 200 OK
{
  "code": 200,
  "message": "AI 分析完成",
  "data": {
    "sessionId": "sess_123",
    "analysis": "本题考查牛顿第二定律...",
    "suggestions": ["建议复习力的合成与分解"]
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

#### 会话过期
```json
// HTTP 408 Request Timeout
{
  "code": 4508,
  "message": "会话已失效",
  "data": null,
  "timestamp": 1710000000000,
  "reason": "会话 ID: sess_123"
}
```

#### AI 服务不可用
```json
// HTTP 503 Service Unavailable
{
  "code": 5501,
  "message": "AI 服务不可用",
  "data": null,
  "timestamp": 1710000000000,
  "reason": "AI 服务暂时不可用，请稍后重试"
}
```

---

### 4. 参数校验错误示例

#### JSON 格式错误
```json
// HTTP 400 Bad Request
{
  "code": 4907,
  "message": "请求体格式错误，请检查 JSON 格式",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

#### 多个字段验证失败
```json
// HTTP 400 Bad Request
{
  "code": 4900,
  "message": "参数校验失败：标题不能为空；难度必须在 1-5 之间",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

#### 缺少必需参数
```json
// HTTP 400 Bad Request
{
  "code": 4901,
  "message": "缺少必需参数：questionId",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 5. 权限相关示例

#### 学生访问教师接口
```json
// HTTP 403 Forbidden
{
  "code": 4802,
  "message": "教师权限不足",
  "data": null,
  "timestamp": 1710000000000,
  "reason": "用户 ID: 123"
}
```

#### 账户被禁用
```json
// HTTP 403 Forbidden
{
  "code": 4106,
  "message": "用户已被禁用",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 6. 数据库约束冲突示例

#### 唯一索引冲突
```json
// HTTP 409 Conflict
{
  "code": 5004,
  "message": "数据库中已存在相同的用户名：zhangsan",
  "data": null,
  "timestamp": 1710000000000,
  "reason": "字段：username, 值：zhangsan, 类型：UNIQUE_KEY"
}
```

---

### 7. 实验管理模块错误示例

#### 实验标题重复
```json
// HTTP 409 Conflict
{
  "code": 4601,
  "message": "实验已存在（标题重复）",
  "data": null,
  "timestamp": 1710756600000,
  "reason": null
}
```

#### 实验不存在
```json
// HTTP 404 Not Found
{
  "code": 4602,
  "message": "实验不存在",
  "data": null,
  "timestamp": 1710756600000,
  "reason": null
}
```

#### 关联的题目 ID 不存在
```json
// HTTP 404 Not Found
{
  "code": 4202,
  "message": "题目不存在",
  "data": null,
  "timestamp": 1710756600000,
  "reason": "题目 ID: 9999"
}
```

---

## 前端处理建议

### 1. 统一的错误拦截器

建议在 Axios 或 Fetch 中设置统一的错误拦截器：

```javascript
// Axios 拦截器示例
axios.interceptors.response.use(
  response => {
    const { code, message, data } = response.data;
    
    // 成功响应
    if (code === 200 || code === 201) {
      return { success: true, data };
    }
    
    // 业务错误
    return handleBusinessError(code, message);
  },
  
  error => {
    // HTTP 错误
    const { status, data } = error.response;
    
    switch (status) {
      case 401:
        // 未授权，跳转登录
        redirectToLogin();
        break;
      case 403:
        // 权限不足，提示用户
        showPermissionDenied(data?.message);
        break;
      case 404:
        // 资源不存在
        showResourceNotFound(data?.message);
        break;
      case 500:
      case 503:
        // 服务器错误，提示系统繁忙
        showSystemError();
        break;
      default:
        // 其他错误
        showGenericError(data?.message);
    }
    
    return Promise.reject(error);
  }
);
```

### 2. 业务码处理策略

```javascript
function handleBusinessError(code, message) {
  // 认证相关错误 - 跳转登录
  if (code === 4111) { // INVALID_TOKEN
    localStorage.removeItem('token');
    window.location.href = '/login';
    return;
  }
  
  // 会话相关错误 - 重新初始化
  if (code === 4508) { // SESSION_EXPIRED
    recreateSession();
    return;
  }
  
  // 参数错误 - 表单验证提示
  if (code >= 4900 && code <= 4999) {
    showFormValidationErrors(message);
    return;
  }
  
  // 权限错误 - 提示无权限
  if (code >= 4800 && code <= 4899) {
    showPermissionDenied(message);
    return;
  }
  
  // 资源不存在 - 导航到上一页或首页
  if (code === 4202 || code === 4302 || code === 4402) {
    showResourceNotFound(message);
    return;
  }
  
  // 重复冲突 - 提示修改内容
  if (code === 4107 || code === 4203 || code === 4303) {
    showDuplicateError(message);
    return;
  }
  
  // AI 服务错误 - 友好提示
  if (code >= 5500 && code <= 5599) {
    showAIServiceError(code, message);
    return;
  }
  
  // 默认错误提示
  showErrorToast(message);
}
```

### 3. 特殊场景处理

#### Token 刷新机制
```javascript
// 检测到 token 即将过期时自动刷新
if (code === 4111) {
  try {
    await refreshToken();
    // 重试原请求
    return retryOriginalRequest();
  } catch (e) {
    // 刷新失败，跳转登录
    redirectToLogin();
  }
}
```

#### AI 服务降级
```javascript
// AI 服务不可用时提供基础功能
if (code === 5501 || code === 5502) {
  showFallbackAnalysis();
  // 记录日志，稍后重试
  logAIErrorForRetry();
}
```

### 4. 用户友好的错误提示

| 错误类型 | 推荐提示文案 |
|---------|------------|
| 401 未授权 | "登录已过期，请重新登录" |
| 403 权限不足 | "您没有权限执行此操作" |
| 404 资源不存在 | "请求的内容不存在" |
| 409 资源冲突 | "该内容已存在，请修改后重试" |
| 4900 参数错误 | "请检查填写的内容是否正确" |
| 500 系统错误 | "系统繁忙，请稍后重试" |
| 503 服务不可用 | "服务暂时不可用，请稍后再试" |
| 5501 AI 不可用 | "AI 分析服务暂时不可用，我们正在努力修复" |

### 5. 错误日志上报

```javascript
// 关键错误上报到监控系统
function reportError(code, message, url, method) {
  if (code >= 5000) {
    // 服务端错误，需要上报
    errorTrackingService.report({
      code,
      message,
      url,
      method,
      timestamp: Date.now(),
      userAgent: navigator.userAgent
    });
  }
}
```

---

## 最佳实践总结

### ✅ 推荐做法

1. **始终检查 HTTP 状态码**：优先处理 4xx/5xx 错误
2. **根据业务码精确提示**：不要只显示 "操作失败"
3. **认证错误立即跳转登录**：401/4111 错误不要犹豫
4. **参数错误定位到字段**：表单验证要具体指出哪个字段有问题
5. **系统错误友好提示**：5xx 错误不要暴露技术细节
6. **重复错误引导修改**：409 错误告诉用户如何修改

### ❌ 避免做法

1. ❌ 所有错误都显示 "网络错误"
2. ❌ 忽略业务码，只看 HTTP 状态码
3. ❌ 将技术错误详情直接展示给用户
4. ❌ Token 过期不处理，让用户手动刷新
5. ❌ 参数验证失败不提示具体哪个字段

---

## 附录：完整状态码速查表

### 快速索引

| 业务场景 | 可能返回的状态码 |
|---------|----------------|
| 登录/注册 | 200, 400, 401, 403, 409, 4105, 4107, 4111 |
| 获取资源 | 200, 404, 403, 4202, 4302, 4402, 4602 |
| 创建资源 | 201, 400, 403, 409, 4203, 4303, 4601 |
| 更新资源 | 200, 400, 403, 404, 409 |
| 删除资源 | 200, 403, 404, 4602 |
| AI 分析 | 200, 400, 408, 500, 503, 5501, 5502 |
| 权限校验 | 200, 403, 4801, 4802, 4803, 4804 |
| 参数验证 | 400, 4900, 4901, 4903, 4907 |
| 系统错误 | 500, 503, 5001, 5002, 5003 |
| 实验管理 | 200, 201, 400, 404, 409, 4202, 4601, 4602, 5801, 5802 |

---

## 更新日志

| 版本 | 更新日期 | 更新内容 |
|------|---------|----------|
| 1.0.0 | 2026-03-16 | 初始版本，包含完整的状态码说明 |
| 1.0.1 | 2026-03-18 | 新增实验管理模块错误码 (4600-4699, 5800-5899)，补充实验相关错误示例 |
| 1.0.2 | 2026-03-26 | 更新教师事件模块为全局事件设计，调整错误码 4702、4703、5701 描述 |
| 1.0.3 | 2026-04-18 | 新增视频上传模块相关错误码，文档同步更新 |
| 1.0.4 | 2026-04-18 | 重构实验管理模块，移除不存在的步骤字段，文档同步更新 |
| 1.0.5 | 2026-04-18 | 修正 AI_QUOTA_EXCEEDED HTTP状态码 (500→429)；新增 TOKEN_QUOTA_EXCEEDED 状态码 (5508, 429) |

---

## 联系方式

如有疑问，请联系开发团队或在项目群内咨询。

**智学物理开发团队** © 2026
