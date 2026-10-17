# 智学物理 AI 服务前端交互规范

> 本文档详细说明了智学物理 AI 服务的 SSE 流式输出、异步处理等核心机制，帮助前端开发人员理解并正确对接 AI 相关接口。

## 目录

- [核心概念](#核心概念)
- [SSE 流式输出机制](#sse-流式输出机制)
- [AI 接口列表与使用场景](#ai-接口列表与使用场景)
- [请求参数详解](#请求参数详解)
- [响应数据格式](#响应数据格式)
- [错误处理机制](#错误处理机制)
- [会话管理](#会话管理)
- [前端实现指南](#前端实现指南)
- [常见问题与解决方案](#常见问题与解决方案)

---

## 核心概念

### 1. SSE (Server-Sent Events)

**SSE 是什么？**
- SSE 是一种允许服务器向浏览器推送实时数据的 HTTP 技术
- 基于 HTTP 长连接，服务器可以持续向客户端发送数据
- 适用于 AI 流式输出、实时通知等场景

**为什么使用 SSE？**
- ✅ **单向通信**：服务器 → 客户端（适合 AI 内容推送）
- ✅ **自动重连**：浏览器会自动尝试重新连接
- ✅ **简单易用**：基于标准 HTTP，无需额外协议
- ✅ **流量友好**：相比 WebSocket 更轻量

**SSE vs WebSocket：**
| 特性 | SSE | WebSocket |
|------|-----|-----------|
| 通信方向 | 单向（服务器→客户端） | 双向 |
| 协议 | HTTP | WebSocket |
| 数据格式 | 文本/JSON | 二进制/文本 |
| 适用场景 | 流式输出、实时推送 | 实时聊天、游戏 |

### 2. 异步任务处理

**后端异步模型：**
```
用户请求 → Controller → 创建 SSE 连接 → 提交异步任务 → 立即返回
                                    ↓
                            后台执行 AI 调用
                                    ↓
                            通过 SSE 推送结果
```

**关键特点：**
- 请求提交后立即建立 SSE 连接
- AI 调用在独立线程池中异步执行
- 支持最大 32 个并发 SSE 连接
- 连接超时时间：5 分钟（300,000ms）

### 3. 双 AI 服务商

系统整合了两个 AI 服务提供商：

| 服务商 | 功能模块 | 主要用途 |
|--------|---------|---------|
| **智谱 AI** | 实时问答、错题分析 | 学生提问互动 |
| **阿里 AI** | 学生报告、教师分析 | 综合分析报告生成 |

---

## SSE 流式输出机制

### 1. SSE 事件格式

后端发送的每个 SSE 事件遵循以下格式：

```
event: content
data: 实际内容（纯字符串）

event: progress  
data: {"progress": 50, "stage": "analyzing"}

event: reasoning
data: {"reasoning": "深度思考过程..."}

event: complete
data: {"promptTokens": 100, "completionTokens": 200, "totalTokens": 300}

event: error
data: {"code": 5501, "message": "AI 服务不可用"}
```

**事件类型说明：**

| 事件类型 | 说明 | 数据格式 |
|---------|------|---------|
| `content` | AI 生成的内容片段（**纯字符串**，直接追加） | 字符串 |
| `progress` | 处理进度更新 | `{progress, stage}` |
| `reasoning` | AI 深度思考过程 | `{reasoning}` |
| `complete` | 传输完成统计 | `{promptTokens, completionTokens, totalTokens}` |
| `error` | 错误信息 | `{code, message}` |

**重要说明：**
- `content` 事件的数据是**纯字符串**，直接追加到已接收内容后面即可
- 不要再用 `JSON.parse()` 解析 content 事件数据
- 解析示例：
```
// ✅ 正确：content 是纯字符串
emitter.addEventListener('content', (event) => {
  fullContent += event.data;  // 直接追加
});

// ❌ 错误：不要解析 JSON
emitter.addEventListener('content', (event) => {
  const data = JSON.parse(event.data);  // 错误！
});

```

### 2. SSE 连接生命周期

```
// 1. 创建连接
const emitter = new EventSource('/ai/student/realtimeQA', {
  headers: {
    'Authorization': 'Bearer token',
    'X-Session-Id': 'sessionId'
  }
});

// 2. 监听不同类型的事件
emitter.addEventListener('content', (event) => {
  console.log('收到 AI 内容:', event.data);
  // 追加到显示区域
});

emitter.addEventListener('progress', (event) => {
  const data = JSON.parse(event.data);
  console.log('处理进度:', data.progress + '%');
  // 更新进度条
});

emitter.addEventListener('reasoning', (event) => {
  const data = JSON.parse(event.data);
  console.log('思考过程:', data.reasoning);
  // 展示思考过程（可选）
});

emitter.addEventListener('complete', (event) => {
  const data = JSON.parse(event.data);
  console.log('传输完成，Token 统计:', data);
  // 关闭连接，显示统计信息
  emitter.close();
});

emitter.addEventListener('error', (event) => {
  const data = JSON.parse(event.data);
  console.error('发生错误:', data.message);
  // 错误处理
  emitter.close();
});

// 3. 连接错误处理
emitter.onerror = (err) => {
  if (emitter.readyState === EventSource.CLOSED) {
    console.log('连接已正常关闭');
  } else {
    console.error('连接异常断开，将自动重连...');
  }
};
```

### 3. 心跳保活机制

**后端心跳设置：**
- 默认每 30 秒发送一次心跳
- 防止连接因长时间无数据而断开
- 前端无需特殊处理，但需了解此机制

**前端注意事项：**
```javascript
// ❌ 不要这样做：频繁关闭和重建连接
setInterval(() => {
  emitter.close();
  emitter = new EventSource(...);
}, 10000);

// ✅ 正确做法：让连接保持打开状态
const emitter = new EventSource(...);
// 只在收到 complete 或 error 事件时关闭
```

### 4. 停止生成功能

**功能说明：**
- 前端提供"停止生成"按钮，允许用户主动中断 AI 流式输出
- 支持两种触发方式：用户点击按钮或关闭页面/组件时自动中断
- 中断后，已生成的部分内容会被保存，不会丢失

**前端实现示例：**
```
// 1. 创建 AbortController 用于中断请求
const abortController = new AbortController();

// 2. 在发送请求时传入 signal
async function generateReport() {
  try {
    const response = await fetch(`${API_BASE_URL}/ai/student/report`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${getToken()}`,
        'X-Session-Id': getSessionId()
      },
      signal: abortController.signal  // 传入 AbortController 的 signal
    });
    // 处理流式响应...
  } catch (error) {
    if (error.name === 'AbortError') {
      // 用户中断或组件卸载
      console.log('请求已中断');
      // 注意：此时已生成的内容仍会保留在 state 中
    }
  }
}

// 3. 停止按钮点击处理
const stopGeneration = () => {
  if (loading) {
    abortController.abort();
  }
};

// 4. 组件卸载时自动中断（防止内存泄漏）
onUnmounted(() => {
  abortController.abort();
});
```

**UI 示例：**
``vue
<template>
  <div>
    <button @click="generateReport" :disabled="loading">生成报告</button>
    <button v-if="loading" @click="stopGeneration" class="stop-btn">停止生成</button>
    <div v-if="content">{{ content }}</div>
  </div>
</template>
```

**重要特性：**
- 🔄 **实时中断**：点击停止后，后端会立即停止 AI 调用，不再推送新内容
- 💾 **内容保留**：已生成的内容会保留在内存/状态中，用户可以查看部分结果
- 🔌 **自动清理**：组件卸载时自动调用 `abort()`，防止内存泄漏

---

## AI 接口列表与使用场景

### 1. 智谱 AI 模块

#### 1.1 实时问答

**接口地址：** `POST /ai/student/realtimeQA`

**使用场景：**
- 学生在学习过程中提出具体问题
- 需要 AI 即时解答疑问
- 支持多轮对话（基于会话上下文）

**请求参数：**
```json
{
  "question": "什么是牛顿第二定律？",
  "questionId": null,  // 实时问答不传题目 ID
  "imageBase64": "data:image/png;base64,..."  // 可选，题目图片
}
```

**响应示例：**
```
event: content
data: 牛顿第二定律是经典力学的基本定律之一...

event: content
data: 它描述了力与物体运动状态变化之间的关系。

event: reasoning
data: {"reasoning": "这个问题需要从定义、公式、应用三个层面解释..."}

event: complete
data: {"promptTokens": 85, "completionTokens": 156, "totalTokens": 241}
```

**前端调用示例：**
```javascript
async function askQuestion(question, imageBase64 = null) {
  return new Promise((resolve, reject) => {
    const requestBody = {
      question: question,
      questionId: null,
      imageBase64: imageBase64
    };

    // 创建 SSE 连接
    const eventSource = new EventSource(
      `${API_BASE_URL}/ai/student/realtimeQA`,
      {
        headers: {
          'Authorization': `Bearer ${getToken()}`,
          'X-Session-Id': getSessionId(),
          'Content-Type': 'application/json'
        }
      }
    );

    let fullContent = '';

    eventSource.addEventListener('content', (event) => {
      fullContent += event.data;
      // 实时更新 UI：打字机效果
      updateAIResponse(event.data);
    });

    eventSource.addEventListener('reasoning', (event) => {
      const data = JSON.parse(event.data);
      // 显示思考过程（可选）
      showReasoning(data.reasoning);
    });

    eventSource.addEventListener('complete', (event) => {
      const data = JSON.parse(event.data);
      console.log('Token 统计:', data);
      eventSource.close();
      resolve({
        content: fullContent,
        tokens: data
      });
    });

    eventSource.addEventListener('error', (event) => {
      const data = JSON.parse(event.data);
      handleError(data.code, data.message);
      eventSource.close();
      reject(new Error(data.message));
    });

    // 发送 POST 请求（注意：EventSource 默认 GET，需要特殊处理）
    // 实际应该使用 fetch 创建连接后监听
    createSSEConnection(requestBody);
  });
}
```

**重要提示：** ⚠️
由于浏览器安全限制，SSE 通常使用 GET 请求。对于需要 POST 的请求，有两种方案：

**方案 A：使用 fetch + ReadableStream（推荐）**
```
async function createSSEConnection(requestBody) {
  const response = await fetch(`${API_BASE_URL}/ai/student/realtimeQA`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${getToken()}`,
      'X-Session-Id': getSessionId(),
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(requestBody)
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const chunk = decoder.decode(value);
    processSSEChunk(chunk);
  }
}
```

**方案 B：URL 参数传递（备选）**
```
// 将参数编码到 URL 中
const url = `/ai/student/realtimeQA?question=${encodeURIComponent(question)}`;
const emitter = new EventSource(url);
```

#### 1.2 错题分析

**接口地址：** `POST /ai/student/wrong-question-analysis`

**使用场景：**
- 学生对做错的题目请求分析
- 需要题目 ID 作为分析依据
- 提供错误原因解释和学习建议

**请求参数：**
```json
{
  "question": "请分析这道题的错误原因",
  "questionId": 2001,  // 必填，要分析的题目 ID
  "imageBase64": null  // 可选
}
```

**响应示例：**
```
event: content
data: 本题考查的是牛顿运动定律的应用...

event: content
data: 常见错误：混淆了作用力与反作用力的概念

event: content
data: 学习建议：复习牛顿第三定律，区分相互作用力

event: complete
data: {"promptTokens": 120, "completionTokens": 180, "totalTokens": 300}
```

**前端调用示例：**
```javascript
async function analyzeWrongQuestion(questionId, customQuestion = null) {
  const requestBody = {
    question: customQuestion || "请分析这道题的错误原因",
    questionId: questionId,
    imageBase64: null
  };

  try {
    const result = await createSSEConnection(
      `${API_BASE_URL}/ai/student/wrong-question-analysis`,
      requestBody
    );
    
    // 按类型分类展示
    displayAnalysis(result);
  } catch (error) {
    showError('分析失败，请稍后重试');
  }
}
```

---

### 2. 阿里 AI 模块

#### 2.1 学生个人报告

**接口地址：** `GET /ai/student/report`

**使用场景：**
- 生成学生个人学习报告
- 综合分析学生的答题情况
- 提供个性化学习建议

**请求参数：** 无需请求体（GET 请求）

**必需 Header：**
```
Authorization: Bearer <token>
X-Session-Id: <sessionId>
```

**响应示例：**
```
event: content
data: 根据最近的答题记录分析...

event: content
data: 知识点掌握率：78%

event: content
data: 建议重点复习：力学部分

event: complete
data: {"promptTokens": 200, "completionTokens": 350, "totalTokens": 550}
```

**前端调用示例：**
```javascript
function generateStudentReport() {
  const url = `${API_BASE_URL}/ai/student/report`;
  
  const eventSource = new EventSource(url, {
    headers: {
      'Authorization': `Bearer ${getToken()}`,
      'X-Session-Id': getSessionId()
    }
  });

  let reportContent = '';

  eventSource.addEventListener('content', (event) => {
    reportContent += event.data;
    appendToReport(event.data);
  });

  eventSource.addEventListener('complete', (event) => {
    const data = JSON.parse(event.data);
    eventSource.close();
    finalizeReport(reportContent, data);
  });

  eventSource.addEventListener('error', (event) => {
    const data = JSON.parse(event.data);
    handleReportError(data);
    eventSource.close();
  });
}
```

#### 2.2 教师综合分析

**接口地址：** `GET /ai/teacher/report`

**使用场景：**
- 教师查看班级整体情况分析
- 获取教学统计数据
- 了解学生学习状况

**请求参数：** 无需请求体（GET 请求）

**必需 Header：**
```
Authorization: Bearer <token>
```

**响应格式：** 与学生报告类似，但内容侧重教学维度

#### 2.3 获取学生最新报告

**接口地址：** `GET /ai/teacher/report/{studentId}`

**使用场景：**
- 教师查看特定学生的最新报告
- 非流式接口，返回完整报告对象

**请求参数：**
```
路径参数：studentId (学生 ID)
```

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "studentId": 101,
    "studentName": "张三",
    "reportDate": "2026-03-16T10:30:00",
    "masteryRate": 78.5,
    "wrongQuestionCount": 12,
    "recommendations": ["加强力学基础练习", "提高计算准确性"]
  },
  "timestamp": 1710000000000
}
```

---

### 3. 会话管理接口

#### 3.1 获取会话历史

**接口地址：** `GET /ai/session/history`

**使用场景：**
- 查看当前会话的对话历史
- 支持多轮对话上下文追溯

**请求参数：** 无需请求体

**必需 Header：**
```
Authorization: Bearer <token>
X-Session-Id: <sessionId>
```

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "sessionId": "sess_123",
      "role": "user",
      "content": "什么是牛顿第二定律？",
      "timestamp": "2026-03-16T10:00:00"
    },
    {
      "id": 2,
      "sessionId": "sess_123",
      "role": "assistant",
      "content": "牛顿第二定律是...",
      "timestamp": "2026-03-16T10:00:05"
    }
  ],
  "timestamp": 1710000000000
}
```

#### 3.2 清除会话

**接口地址：** `DELETE /ai/session`

**使用场景：**
- 清空当前会话的历史记录
- 开始新的对话主题

**请求参数：** 无需请求体

**必需 Header：**
```
Authorization: Bearer <token>
X-Session-Id: <sessionId>
```

**响应示例：**
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000
}
```

---

## 请求参数详解

### 1. 通用请求头

所有 AI 接口都需要以下请求头：

```javascript
const headers = {
  // 认证令牌（必需）
  'Authorization': `Bearer ${jwtToken}`,
  
  // 会话 ID（必需，用于上下文关联）
  'X-Session-Id': sessionId,
  
  // 内容类型（POST 请求需要）
  'Content-Type': 'application/json'
};
```

### 2. 会话 ID 管理

**会话 ID 格式：**
```
<时间戳>_<随机字符串>
示例：20260316103000_a1b2c3d4e5f6
```

**生成规则：**
```javascript
function generateSessionId() {
  const timestamp = new Date().getTime();
  const randomStr = Math.random().toString(36).substring(2, 16);
  return `${timestamp}_${randomStr}`;
}

// 或者使用时间戳 + UUID
function generateSessionIdV2() {
  const timestamp = new Date().getTime();
  const uuid = crypto.randomUUID();
  return `${timestamp}_${uuid.replace(/-/g, '').substring(0, 16)}`;
}
```

**会话 ID 生命周期：**
- 首次访问 AI 接口时创建
- 存储在 localStorage 或 sessionStorage
- 会话过期时间：由后端配置（通常 30 分钟）
- 过期后需重新创建

**前端管理示例：**
```javascript
class SessionManager {
  constructor() {
    this.sessionKey = 'ai_session_id';
  }

  getSessionId() {
    let sessionId = sessionStorage.getItem(this.sessionKey);
    
    if (!sessionId || this.isSessionExpired(sessionId)) {
      sessionId = this.createSession();
    }
    
    return sessionId;
  }

  createSession() {
    const sessionId = generateSessionId();
    sessionStorage.setItem(this.sessionKey, sessionId);
    return sessionId;
  }

  isSessionExpired(sessionId) {
    // 简单实现：检查时间戳是否超过 30 分钟
    const [timestamp] = sessionId.split('_');
    const sessionTime = parseInt(timestamp);
    const now = new Date().getTime();
    const thirtyMinutes = 30 * 60 * 1000;
    
    return (now - sessionTime) > thirtyMinutes;
  }

  clearSession() {
    sessionStorage.removeItem(this.sessionKey);
  }
}

// 使用示例
const sessionManager = new SessionManager();
const sessionId = sessionManager.getSessionId();
```

### 3. 图片 Base64 编码

**适用接口：**
- `POST /ai/student/realtimeQA`
- `POST /ai/student/wrong-question-analysis`

**格式要求：**
```
Data URI Scheme: data:image/[format];base64,[encoded_data]
示例：data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...
```

**前端转换代码：**
```javascript
// 方法 1：使用 FileReader API
function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// 方法 2：使用 Canvas 压缩并转换
function imageFileToCompressedBase64(file, maxWidth = 800, quality = 0.8) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      let width = img.width;
      let height = img.height;
      
      // 等比例缩放
      if (width > maxWidth) {
        height = (maxWidth / width) * height;
        width = maxWidth;
      }
      
      canvas.width = width;
      canvas.height = height;
      
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, width, height);
      
      // 转换为 Base64
      const base64 = canvas.toDataURL('image/jpeg', quality);
      resolve(base64);
    };
    img.onerror = reject;
    img.src = URL.createObjectURL(file);
  });
}

// 使用示例
async function uploadQuestionImage(fileInput) {
  const file = fileInput.files[0];
  if (!file) return null;
  
  // 压缩并转换
  const base64 = await imageFileToCompressedBase64(file);
  return base64;
}
```

**注意事项：**
1. ⚠️ Base64 会使数据量增加约 33%
2. ✅ 建议前端进行适当压缩
3. ✅ 支持的格式：PNG, JPG/JPEG
4. ❌ 不支持：GIF, SVG, WEBP

---

## 响应数据格式

### 1. SSE 流式响应

**标准事件结构：**
```javascript
{
  event: '<事件类型>',  // content, progress, reasoning, complete, error
  data: '<内容或JSON字符串>'
}
```

### 2. content 事件数据结构

**说明：** `content` 事件的数据是**纯字符串**，直接追加到已接收内容后面即可。

```javascript
// 前端处理示例
function handleContent(content) {
  // 直接追加到显示区域
  appendToDisplay(content);
}
```

### 3. progress 事件数据结构

``typescript
interface ProgressData {
  progress: number;     // 进度百分比 (0-100)
  stage?: string;       // 当前阶段标识
  message?: string;     // 可选的进度描述
}

// stage 的可能取值：
type ProgressStage = 
  | 'initializing'      // 初始化中
  | 'analyzing'         // 分析中
  | 'generating'        // 生成中
  | 'finalizing'        // 收尾中
  | 'completed'         // 已完成
```

**前端进度条实现：**
```javascript
class ProgressTracker {
  constructor(progressElementId) {
    this.progressEl = document.getElementById(progressElementId);
    this.stageEl = document.getElementById(`${progressElementId}-stage`);
  }

  updateProgress(data) {
    // 更新进度条宽度
    this.progressEl.style.width = `${data.progress}%`;
    
    // 更新阶段文字
    if (data.stage) {
      this.stageEl.textContent = this.getStageText(data.stage);
    }
    
    // 添加动画效果
    this.progressEl.classList.add('progress-animated');
  }

  getStageText(stage) {
    const stageMap = {
      'initializing': '正在初始化...',
      'analyzing': '正在分析问题...',
      'generating': '正在生成答案...',
      'finalizing': '正在整理结果...',
      'completed': '完成！'
    };
    return stageMap[stage] || stage;
  }
}
```

### 4. reasoning 事件数据结构

``typescript
interface ReasoningData {
  reasoning: string;    // AI 的思考过程
  confidence?: number;  // 可选的置信度 (0-1)
}
```

**前端展示建议：**
```
// 方案 1：折叠面板展示
function showReasoning(reasoningText) {
  const accordion = document.createElement('details');
  accordion.className = 'reasoning-accordion';
  
  const summary = document.createElement('summary');
  summary.textContent = '🤖 AI 的思考过程';
  
  const content = document.createElement('div');
  content.className = 'reasoning-content';
  content.textContent = reasoningText;
  
  accordion.appendChild(summary);
  accordion.appendChild(content);
  
  document.querySelector('.ai-response').appendChild(accordion);
}

// 方案 2：侧边栏展示
function showReasoningInSidebar(reasoningText) {
  const sidebar = document.querySelector('.reasoning-sidebar');
  sidebar.innerHTML = `
    <div class="reasoning-header">
      <h4>思考过程</h4>
    </div>
    <div class="reasoning-body">
      ${marked.parse(reasoningText)}
    </div>
  `;
  sidebar.classList.add('visible');
}
```

### 5. complete 事件数据结构

``typescript
interface CompleteData {
  promptTokens: number;        // 提示词消耗的 Token 数
  completionTokens: number;    // 生成内容消耗的 Token 数
  totalTokens: number;         // 总消耗 Token 数
  duration?: number;           // 可选的处理时长 (毫秒)
  model?: string;              // 可选的 AI 模型名称
}
```

**Token 统计展示：**
```javascript
function showTokenStatistics(data) {
  const statsHtml = `
    <div class="token-stats">
      <div class="stat-item">
        <span class="label">提示词:</span>
        <span class="value">${data.promptTokens}</span>
      </div>
      <div class="stat-item">
        <span class="label">生成内容:</span>
        <span class="value">${data.completionTokens}</span>
      </div>
      <div class="stat-item total">
        <span class="label">总计:</span>
        <span class="value">${data.totalTokens}</span>
      </div>
    </div>
  `;
  
  document.querySelector('.response-footer').innerHTML = statsHtml;
}
```

### 6. error 事件数据结构

``typescript
interface ErrorData {
  code: number;      // 业务状态码
  message: string;   // 错误描述
  reason?: string;   // 附加的错误原因
}
```

**错误码分类：**

| 错误码范围 | 类型 | 处理策略 |
|-----------|------|---------|
| 4xxx | 客户端错误 | 提示用户修正请求 |
| 45xx | AI 业务错误 | 检查会话、参数 |
| 49xx | 参数校验错误 | 表单验证提示 |
| 5xxx | 服务器错误 | 稍后重试 |
| 55xx | AI 技术错误 | 服务降级处理 |

**常见错误码速查：**

| 错误码 | 含义 | 前端处理 |
|-------|------|---------|
| 4508 | 会话已失效 | 重新创建会话 |
| 4509 | 题目 ID 为空 | 检查参数 |
| 5501 | AI 服务不可用 | 提示服务暂时不可用 |
| 5502 | AI 响应超时 | 建议重试 |
| 5503 | AI 响应解析失败 | 提示系统错误 |
| 5504 | AI 配额不足 | 联系管理员 |

---

## 错误处理机制

### 1. 错误处理层级

```
第一层：HTTP 状态码错误（网络层）
    ↓
第二层：SSE 连接错误（传输层）
    ↓
第三层：业务状态码错误（业务层）
    ↓
第四层：未知异常（兜底层）
```

### 2. HTTP 状态码处理

```javascript
async function handleHTTPRequest(url, options) {
  try {
    const response = await fetch(url, options);
    
    if (!response.ok) {
      // HTTP 错误（4xx, 5xx）
      throw new HTTPError(response.status, response.statusText);
    }
    
    return response;
  } catch (error) {
    if (error instanceof HTTPError) {
      handleHTTPError(error);
    } else {
      handleNetworkError(error);
    }
    throw error;
  }
}

function handleHTTPError(error) {
  switch (error.status) {
    case 401:
      // 未授权，跳转登录
      redirectToLogin();
      break;
    case 403:
      // 权限不足
      showPermissionDenied();
      break;
    case 404:
      // 资源不存在
      showNotFound();
      break;
    case 429:
      // 请求过多（限流）
      showRateLimitWarning();
      break;
    case 500:
    case 503:
      // 服务器错误
      showServerError();
      break;
    default:
      showGenericError();
  }
}
```

### 3. SSE 连接错误处理

```
class SSEConnection {
  constructor(url, options = {}) {
    this.url = url;
    this.options = options;
    this.retryCount = 0;
    this.maxRetries = options.maxRetries || 3;
    this.retryDelay = options.retryDelay || 3000;
  }

  connect() {
    return new Promise((resolve, reject) => {
      const eventSource = new EventSource(this.url, {
        headers: this.options.headers
      });

      const errorHandler = (error) => {
        console.error('SSE 连接错误:', error);
        
        if (this.retryCount < this.maxRetries) {
          this.retryCount++;
          console.log(`第${this.retryCount}次重连...`);
          
          setTimeout(() => {
            this.connect().then(resolve).catch(reject);
          }, this.retryDelay);
        } else {
          reject(new Error('SSE 连接失败，已达到最大重试次数'));
        }
      };

      eventSource.addEventListener('error', (event) => {
        const data = JSON.parse(event.data);
        
        // 业务错误不重试
        if (data.code >= 4000) {
          reject(new Error(data.message));
          eventSource.close();
          return;
        }
        
        // 技术错误可重试
        errorHandler(data);
      });

      eventSource.addEventListener('complete', () => {
        eventSource.close();
        resolve();
      });
    });
  }
}
```

### 4. 业务错误处理

```
class AIErrorHandler {
  static handle(code, message, reason) {
    const errorConfig = this.getErrorConfig(code);
    
    // 用户友好的提示文案
    const userMessage = this.getUserMessage(errorConfig);
    
    // 日志记录（用于调试）
    this.logError(code, message, reason);
    
    // 显示错误提示
    this.showToast(userMessage);
    
    // 特殊处理逻辑
    this.handleSpecialCases(code);
  }

  static getErrorConfig(code) {
    const errorMap = {
      // AI 业务错误
      4508: {
        type: 'SESSION_EXPIRED',
        action: 'RECREATE_SESSION'
      },
      4509: {
        type: 'INVALID_PARAMETER',
        action: 'CHECK_INPUT'
      },
      
      // AI 技术错误
      5501: {
        type: 'SERVICE_UNAVAILABLE',
        action: 'SHOW_FALLBACK'
      },
      5502: {
        type: 'TIMEOUT',
        action: 'RETRY'
      },
      5503: {
        type: 'PARSE_ERROR',
        action: 'REFRESH_PAGE'
      },
      
      // 参数校验错误
      4900: {
        type: 'VALIDATION_ERROR',
        action: 'HIGHLIGHT_FIELDS'
      }
    };
    
    return errorMap[code] || { type: 'UNKNOWN', action: 'SHOW_MESSAGE' };
  }

  static getUserMessage(config) {
    const messageMap = {
      SESSION_EXPIRED: '会话已过期，正在重新创建...',
      INVALID_PARAMETER: '请求参数有误，请检查输入',
      SERVICE_UNAVAILABLE: 'AI 服务暂时不可用，请稍后再试',
      TIMEOUT: 'AI 响应超时，正在重试...',
      PARSE_ERROR: '系统解析失败，请刷新页面重试',
      VALIDATION_ERROR: '请检查填写的内容是否正确'
    };
    
    return messageMap[config.type] || '操作失败，请稍后重试';
  }

  static handleSpecialCases(code) {
    switch (code) {
      case 4508:
        // 会话过期，自动重建
        sessionManager.clearSession();
        break;
      case 5501:
      case 5502:
        // AI 服务不可用，提供降级方案
        this.showFallbackOption();
        break;
    }
  }
}
```

### 5. 完整的错误处理流程示例

```
async function callAIAPI(endpoint, requestBody) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 300000); // 5 分钟超时

  try {
    // 1. 创建 SSE 连接
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getToken()}`,
        'X-Session-Id': getSessionId(),
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody),
      signal: controller.signal
    });

    clearTimeout(timeoutId);

    // 2. 检查 HTTP 状态
    if (!response.ok) {
      const errorData = await response.json();
      throw new APIError(
        response.status,
        errorData.code,
        errorData.message,
        errorData.reason
      );
    }

    // 3. 处理流式响应
    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      const chunk = decoder.decode(value);
      const events = parseSSEEvents(chunk);

      for (const event of events) {
        if (event.type === 'error') {
          const errorData = JSON.parse(event.data);
          // 4. 处理业务错误
          AIErrorHandler.handle(errorData.code, errorData.message, errorData.reason);
          throw new AIError(errorData.code, errorData.message);
        }

        // 处理其他事件...
        handleEvent(event);
      }
    }

  } catch (error) {
    // 5. 统一错误处理
    if (error.name === 'AbortError') {
      showError('请求超时，请重试');
    } else if (error instanceof APIError) {
      AIErrorHandler.handle(error.code, error.message, error.reason);
    } else if (error instanceof AIError) {
      // AI 业务错误已在上游处理
    } else {
      console.error('未知错误:', error);
      showError('网络错误，请检查连接');
    }
    throw error;
  }
}
```

---

## 会话管理

### 1. 会话生命周期

```
创建会话 → 使用会话 → 会话过期 → 重建会话
    ↓          ↓          ↓          ↓
首次访问   多轮对话   超时/清理   新会话
```

### 2. 会话状态管理

```javascript
class SessionContext {
  constructor() {
    this.sessionId = null;
    this.createdAt = null;
    this.lastUsedAt = null;
    this.messageCount = 0;
    this.topic = null;
  }

  // 初始化会话
  initialize() {
    this.sessionId = generateSessionId();
    this.createdAt = Date.now();
    this.lastUsedAt = Date.now();
    this.messageCount = 0;
    this.topic = null;
    
    this.saveToStorage();
    return this;
  }

  // 使用会话（每次调用 API 时更新）
  touch() {
    this.lastUsedAt = Date.now();
    this.messageCount++;
    this.saveToStorage();
  }

  // 检查是否过期
  isExpired(maxAge = 30 * 60 * 1000) {
    const age = Date.now() - this.lastUsedAt;
    return age > maxAge;
  }

  // 清空会话
  clear() {
    this.sessionId = null;
    this.createdAt = null;
    this.lastUsedAt = null;
    this.messageCount = 0;
    this.topic = null;
    
    this.removeFromStorage();
  }

  // 持久化存储
  saveToStorage() {
    sessionStorage.setItem('ai_session', JSON.stringify({
      sessionId: this.sessionId,
      createdAt: this.createdAt,
      lastUsedAt: this.lastUsedAt,
      messageCount: this.messageCount,
      topic: this.topic
    }));
  }

  // 从存储恢复
  static loadFromStorage() {
    const stored = sessionStorage.getItem('ai_session');
    if (!stored) return null;

    const data = JSON.parse(stored);
    const context = new SessionContext();
    context.sessionId = data.sessionId;
    context.createdAt = data.createdAt;
    context.lastUsedAt = data.lastUsedAt;
    context.messageCount = data.messageCount;
    context.topic = data.topic;
    
    return context;
  }
}

// 使用示例
let session = SessionContext.loadFromStorage();
if (!session || session.isExpired()) {
  session = new SessionContext().initialize();
}

// 每次调用 AI API 前
session.touch();
const sessionId = session.sessionId;
```

### 3. 会话所有权验证

**后端验证逻辑：**
- 检查会话是否存在
- 验证会话是否属于当前用户
- 检查会话是否过期

**前端配合：**
```javascript
async function validateSessionOwnership(sessionId) {
  try {
    const response = await fetch(`${API_BASE_URL}/ai/session/history`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${getToken()}`,
        'X-Session-Id': sessionId
      }
    });

    if (response.status === 404) {
      // 会话不存在，需要重建
      sessionManager.clearSession();
      return false;
    }

    if (response.status === 403) {
      // 会话不属于当前用户
      sessionManager.clearSession();
      return false;
    }

    return true;
  } catch (error) {
    console.error('会话验证失败:', error);
    return false;
  }
}
```

---

## 前端实现指南

### 1. React Hooks 实现

``jsx
import { useState, useEffect, useRef } from 'react';

function useAIStream(endpoint, requestBody) {
  const [loading, setLoading] = useState(false);
  const [content, setContent] = useState('');
  const [error, setError] = useState(null);
  const [progress, setProgress] = useState(0);
  const [tokens, setTokens] = useState(null);
  const eventSourceRef = useRef(null);

  const startStream = async () => {
    setLoading(true);
    setError(null);
    setContent('');
    setProgress(0);

    try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`,
          'X-Session-Id': getSessionId(),
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody)
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value);
        const lines = chunk.split('\n');

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = JSON.parse(line.slice(6));
            
            switch (line.split(':')[0]) {
              case 'message':
                setContent(prev => prev + data.content);
                break;
              case 'progress':
                setProgress(data.progress);
                break;
              case 'complete':
                setTokens(data);
                break;
              case 'error':
                throw new Error(data.message);
            }
          }
        }
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);

  return { loading, content, error, progress, tokens, startStream };
}

// 使用示例
function AIChatComponent() {
  const { loading, content, error, progress, startStream } = useAIStream(
    '/ai/student/realtimeQA',
    { question: '什么是牛顿第二定律？' }
  );

  return (
    <div>
      {loading && <ProgressBar value={progress} />}
      {error && <ErrorMessage message={error} />}
      <div className="ai-response">{content}</div>
      <button onClick={startStream} disabled={loading}>
        提问
      </button>
    </div>
  );
}
```

### 2. Vue 3 Composition API 实现

```
<script setup>
import { ref, reactive, onUnmounted } from 'vue';

export function useAIStream(endpoint, initialRequest = null) {
  const loading = ref(false);
  const content = ref('');
  const error = ref(null);
  const progress = ref(0);
  const tokens = ref(null);
  let eventSource = null;

  const startStream = async (requestBody = initialRequest) => {
    loading.value = true;
    error.value = null;
    content.value = '';
    progress.value = 0;

    try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${getToken()}`,
          'X-Session-Id': getSessionId(),
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody)
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value);
        processSSEChunk(chunk);
      }
    } catch (err) {
      error.value = err.message;
    } finally {
      loading.value = false;
    }
  };

  const processSSEChunk = (chunk) => {
    const lines = chunk.split('\n');
    
    for (const line of lines) {
      if (line.startsWith('event: ')) {
        const eventType = line.slice(7);
        const dataLine = lines.find(l => l.startsWith('data: '));
        
        if (dataLine) {
          const data = JSON.parse(dataLine.slice(6));
          
          switch (eventType) {
            case 'message':
              content.value += data.content;
              break;
            case 'progress':
              progress.value = data.progress;
              break;
            case 'complete':
              tokens.value = data;
              break;
            case 'error':
              error.value = data.message;
              break;
          }
        }
      }
    }
  };

  onUnmounted(() => {
    if (eventSource) {
      eventSource.close();
    }
  });

  return {
    loading,
    content,
    error,
    progress,
    tokens,
    startStream
  };
}
</script>

<template>
  <div>
    <div v-if="loading">
      <progress :value="progress" max="100"></progress>
    </div>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="content" class="response" v-html="renderMarkdown(content)"></div>
  </div>
</template>
```

### 3. 原生 JavaScript 实现

```
class AIServiceClient {
  constructor(baseURL) {
    this.baseURL = baseURL;
    this.eventSources = new Map();
  }

  async streamRequest(endpoint, requestBody, callbacks = {}) {
    const {
      onMessage,
      onProgress,
      onReasoning,
      onComplete,
      onError
    } = callbacks;

    const url = `${this.baseURL}${endpoint}`;
    const headers = {
      'Authorization': `Bearer ${this.getToken()}`,
      'X-Session-Id': this.getSessionId(),
      'Content-Type': 'application/json'
    };

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(requestBody)
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const events = this.parseSSEBuffer(buffer);
        
        for (const event of events) {
          this.handleSSEEvent(event, {
            onMessage,
            onProgress,
            onReasoning,
            onComplete,
            onError
          });
        }
      }
    } catch (error) {
      if (onError) onError(error);
      throw error;
    }
  }

  parseSSEBuffer(buffer) {
    const lines = buffer.split('\n');
    const events = [];
    let currentEvent = {};

    for (const line of lines) {
      if (line.startsWith('event: ')) {
        currentEvent.type = line.slice(7);
      } else if (line.startsWith('data: ')) {
        currentEvent.data = JSON.parse(line.slice(6));
        events.push(currentEvent);
        currentEvent = {};
      }
    }

    return events;
  }

  handleSSEEvent(event, callbacks) {
    switch (event.type) {
      case 'message':
        callbacks.onMessage?.(event.data);
        break;
      case 'progress':
        callbacks.onProgress?.(event.data);
        break;
      case 'reasoning':
        callbacks.onReasoning?.(event.data);
        break;
      case 'complete':
        callbacks.onComplete?.(event.data);
        break;
      case 'error':
        callbacks.onError?.(event.data);
        break;
    }
  }

  getToken() {
    return localStorage.getItem('jwt_token');
  }

  getSessionId() {
    let sessionId = sessionStorage.getItem('ai_session_id');
    if (!sessionId) {
      sessionId = this.generateSessionId();
      sessionStorage.setItem('ai_session_id', sessionId);
    }
    return sessionId;
  }

  generateSessionId() {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(2, 16);
    return `${timestamp}_${random}`;
  }
}

// 使用示例
const aiClient = new AIServiceClient('http://localhost:8080');

aiClient.streamRequest(
  '/ai/student/realtimeQA',
  { question: '什么是牛顿第二定律？' },
  {
    onMessage: (data) => {
      console.log('收到消息:', data.content);
      appendToChat(data.content);
    },
    onProgress: (data) => {
      updateProgressBar(data.progress);
    },
    onReasoning: (data) => {
      showThinkingProcess(data.reasoning);
    },
    onComplete: (data) => {
      console.log('完成，Token 统计:', data);
      showTokenStats(data);
    },
    onError: (data) => {
      showError(data.message);
    }
  }
);
```

---

## 常见问题与解决方案

### Q1: SSE 连接频繁断开怎么办？

**可能原因：**
1. 网络不稳定
2. 服务器超时配置过短
3. 代理服务器拦截
4. 浏览器兼容性

**解决方案：**
```javascript
class RobustSSEConnection {
  constructor(url, options = {}) {
    this.url = url;
    this.options = {
      maxRetries: 5,
      retryDelay: 3000,
      heartbeatInterval: 30000,
      ...options
    };
    this.retryCount = 0;
    this.lastMessageTime = null;
  }

  connect() {
    return new Promise((resolve, reject) => {
      const connectInternal = () => {
        const eventSource = new EventSource(this.url);
        
        const heartbeatTimer = setInterval(() => {
          if (this.lastMessageTime && 
              Date.now() - this.lastMessageTime > this.options.heartbeatInterval * 2) {
            // 检测到心跳超时
            eventSource.close();
            this.reconnect(connectInternal, reject);
          }
        }, this.options.heartbeatInterval);

        eventSource.onopen = () => {
          console.log('SSE 连接已建立');
          this.retryCount = 0;
          resolve(eventSource);
        };

        eventSource.onmessage = (event) => {
          this.lastMessageTime = Date.now();
          // 处理消息...
        };

        eventSource.onerror = (error) => {
          clearInterval(heartbeatTimer);
          eventSource.close();
          
          if (eventSource.readyState === EventSource.CLOSED) {
            // 正常关闭
            resolve(null);
          } else {
            // 异常断开
            this.reconnect(connectInternal, reject);
          }
        };
      };

      connectInternal();
    });
  }

  reconnect(connectFn, rejectFn) {
    if (this.retryCount < this.options.maxRetries) {
      this.retryCount++;
      console.log(`重连计数：${this.retryCount}`);
      
      setTimeout(() => {
        connectFn().catch(rejectFn);
      }, this.options.retryDelay * this.retryCount); // 指数退避
    } else {
      rejectFn(new Error('达到最大重试次数'));
    }
  }
}
```

### Q2: 如何处理大文件图片上传？

**问题：** 大图 Base64 编码会导致请求体过大

**解决方案：**
```javascript
// 前端压缩策略
async function compressAndConvertImage(file, options = {}) {
  const {
    maxWidth = 1200,
    maxHeight = 1200,
    quality = 0.7,
    maxSizeKB = 500  // 最大 500KB
  } = options;

  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      let width = img.width;
      let height = img.height;

      // 等比例缩放
      if (width > maxWidth || height > maxHeight) {
        const ratio = Math.min(maxWidth / width, maxHeight / height);
        width = Math.floor(width * ratio);
        height = Math.floor(height * ratio);
      }

      canvas.width = width;
      canvas.height = height;

      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, width, height);

      // 迭代压缩直到满足大小要求
      let currentQuality = quality;
      let base64 = canvas.toDataURL('image/jpeg', currentQuality);
      
      while (base64.length / 1024 > maxSizeKB && currentQuality > 0.3) {
        currentQuality -= 0.1;
        base64 = canvas.toDataURL('image/jpeg', currentQuality);
      }

      resolve(base64);
    };
    img.onerror = reject;
    img.src = URL.createObjectURL(file);
  });
}

// 使用示例
const compressedBase64 = await compressAndConvertImage(fileInput.files[0], {
  maxWidth: 800,
  quality: 0.8,
  maxSizeKB: 300
});
```

### Q3: 如何实现多轮对话？

**方案：** 利用会话上下文
```javascript
class ConversationManager {
  constructor() {
    this.sessionId = getSessionId();
    this.history = [];
  }

  async sendMessage(question, context = {}) {
    // 构建包含上下文的请求
    const requestBody = {
      question,
      questionId: null,
      imageBase64: context.imageBase64 || null,
      context: this.buildContext(context)
    };

    // 发送请求
    const response = await this.sendAIRequest(requestBody);
    
    // 更新历史记录
    this.addToHistory('user', question);
    this.addToHistory('assistant', response.content);
    
    return response;
  }

  buildContext(options = {}) {
    // 最近 N 轮对话作为上下文
    const recentHistory = this.history.slice(-6); // 最近 3 轮
    
    return {
      history: recentHistory,
      topic: options.topic || this.detectTopic(),
      ...options
    };
  }

  addToHistory(role, content) {
    this.history.push({
      role,
      content,
      timestamp: Date.now()
    });

    // 限制历史记录长度
    if (this.history.length > 20) {
      this.history = this.history.slice(-20);
    }
  }

  async sendAIRequest(requestBody) {
    // 实际的 API 调用
    return await callAIAPI('/ai/student/realtimeQA', requestBody);
  }
}

// 使用示例
const conversation = new ConversationManager();

// 第一轮
await conversation.sendMessage('什么是牛顿第一定律？');

// 第二轮（带上下文）
await conversation.sendMessage('那第二定律呢？', {
  topic: '牛顿运动定律'
});
```

### Q4: Token 统计如何用于成本核算？

**前端统计展示：**
```javascript
class TokenTracker {
  constructor() {
    this.dailyUsage = this.loadFromStorage();
  }

  recordUsage(tokens) {
    const today = new Date().toDateString();
    
    if (!this.dailyUsage[today]) {
      this.dailyUsage[today] = {
        date: today,
        promptTokens: 0,
        completionTokens: 0,
        totalTokens: 0,
        requestCount: 0
      };
    }

    this.dailyUsage[today].promptTokens += tokens.promptTokens;
    this.dailyUsage[today].completionTokens += tokens.completionTokens;
    this.dailyUsage[today].totalTokens += tokens.totalTokens;
    this.dailyUsage[today].requestCount++;

    this.saveToStorage();
    this.updateUI();
  }

  getCostEstimate() {
    // 假设价格：$0.002 / 1K tokens
    const pricePerThousand = 0.002;
    const totalTokens = Object.values(this.dailyUsage)
      .reduce((sum, day) => sum + day.totalTokens, 0);
    
    return (totalTokens / 1000 * pricePerThousand).toFixed(4);
  }

  showUsageStats() {
    const today = this.dailyUsage[new Date().toDateString()] || {};
    
    return `
      今日使用统计:
      - 请求次数：${today.requestCount || 0}
      - 提示词 Token: ${today.promptTokens || 0}
      - 生成 Token: ${today.completionTokens || 0}
      - 总计：${today.totalTokens || 0}
      - 预估成本：$${this.getCostEstimate()}
    `;
  }
}
```

---

## 附录

### A. 快速参考卡片

```
// 1. SSE 连接模板
const emitter = new EventSource(url, { headers });
emitter.addEventListener('content', handler);
emitter.addEventListener('complete', () => emitter.close());
emitter.addEventListener('error', (err) => {
  handleError(err);
  emitter.close();
});

// 2. 会话 ID 生成
const sessionId = `${Date.now()}_${Math.random().toString(36).substr(2, 16)}`;

// 3. 图片转 Base64
const base64 = await new Promise(resolve => {
  const reader = new FileReader();
  reader.onload = () => resolve(reader.result);
  reader.readAsDataURL(file);
});

// 4. 错误处理模板
try {
  await callAIAPI(endpoint, data);
} catch (error) {
  if (error.code >= 5500 && error.code <= 5599) {
    // AI 服务错误
    showAIServiceError(error);
  } else {
    // 其他错误
    showGenericError(error);
  }
}
```

### B. 测试工具

**Postman 集合示例：**
```json
{
  "info": {
    "name": "智学物理 AI API 测试"
  },
  "item": [
    {
      "name": "实时问答",
      "request": {
        "method": "POST",
        "url": "{{baseUrl}}/ai/student/realtimeQA",
        "header": [
          {"key": "Authorization", "value": "Bearer {{token}}"},
          {"key": "X-Session-Id", "value": "{{sessionId}}"},
          {"key": "Content-Type", "value": "application/json"}
        ],
        "body": {
          "mode": "raw",
          "raw": "{\"question\": \"什么是牛顿第二定律？\"}"
        }
      }
    }
  ]
}
```

### C. 性能优化建议

1. **减少不必要的请求**
   - 使用防抖（debounce）避免频繁触发
   - 缓存常用问题的答案

2. **优化图片上传**
   - 前端压缩到合适尺寸
   - 使用 WebP 格式（如果浏览器支持）

3. **合理管理会话**
   - 避免频繁创建新会话
   - 定期清理过期会话

4. **监控 Token 使用**
   - 设置每日使用上限
   - 对异常使用进行告警

### 5. 客户端中断与内容保存机制

#### 后端中断检测

后端通过 `AbortController.Signal` 检测客户端中断：

```java
// 在后端 AI 服务实现中
zhipuAIClient.streamChat(prompt, model, new AIStreamCallback() {
    @Override
    public boolean isCancelled() {
        // 检测客户端是否已断开连接
        return sessionContext != null && sessionContext.isCancelled();
    }

    @Override
    public void onContent(String content, boolean isLast) {
        if (isCancelled()) {
            return;  // 客户端已断开，不再发送
        }
        // 继续发送内容...
    }

    @Override
    public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
        boolean cancelled = isCancelled();

        // 【关键】有正文就保存，即使内容不完整
        if (fullResponse.length() > 0) {
            sessionManager.addInteraction(sessionContext, question, 
                fullResponse.toString(), totalTokens);
        }

        if (cancelled) {
            // 客户端已断开，不发送 complete 事件
            log.info("任务已取消，已保存部分响应内容: {}", 
                fullResponse.length());
            return;
        }

        // 正常发送完成事件
        emitter.send(SseEmitter.event()
            .name("complete")
            .data(completeData));
    }
});
```

#### 内容保存规则

**核心原则：有正文就保存，即使内容不完整**

| 场景 | 是否保存 | 说明 |
|------|---------|------|
| 正常完成 | ✅ 完整保存 | 保存完整内容和 token 统计 |
| 用户主动停止 | ✅ 部分保存 | 保存已生成的 partial 内容 |
| 网络中断 | ✅ 部分保存 | 保存已接收的内容 |
| AI 服务异常 | ⚠️ 按需保存 | 有内容则保存，记录错误日志 |
| 无任何内容 | ❌ 不保存 | 空内容不写入数据库 |

#### 前端中断处理

```javascript
// 中断时的前端处理
async function generateWithAbort() {
    const abortController = new AbortController();
    
    try {
        await streamAI({
            signal: abortController.signal,
            onMessage: (content) => {
                fullContent += content;
            },
            onComplete: () => {
                // 正常完成
                saveContent(fullContent);
            }
        });
    } catch (error) {
        if (error.name === 'AbortError') {
            // 【关键】中断时，已生成的内容仍然保留在 fullContent 中
            console.log('请求已中断，已保留内容长度:', fullContent.length);
            // 用户可以查看部分生成结果
            showPartialContent(fullContent);
            // 可选：保存部分内容
            if (fullContent.length > 0) {
                savePartialContent(fullContent);
            }
        }
    }
}
```

#### 防御性处理

后端对 SSE 发送异常进行防御性处理：

```java
try {
    emitter.send(SseEmitter.event()
        .name("content")
        .data(content));
} catch (IllegalStateException e) {
    // 防御性处理：客户端可能已断开连接
    log.warn("SSE 发送失败，emitter已关闭: {}", e.getMessage());
    // 不再尝试发送，直接停止后续操作
} catch (IOException e) {
    log.error("SSE 数据发送失败", e);
    // 发送错误事件
    streamUtils.sendError(emitter, ResultCode.NETWORK_COMMUNICATION_ERROR, 
        "SSE 发送失败");
}
```

---

**文档版本：** 1.0.2  
**最后更新：** 2026-04-18  
**维护团队：** 智学物理开发团队
