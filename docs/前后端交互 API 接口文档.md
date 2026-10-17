# 智学物理前后端交互 API 接口文档

> 本文档详细描述了智学物理项目前后端交互的 API 接口规范，包括请求路径、请求格式、响应结构等内容，帮助前端开发人员快速理解和对接后端接口。

## 目录

- [基础规范](#基础规范)
- [认证模块](#认证模块)
- [用户管理模块](#用户管理模块)
- [题目管理模块](#题目管理模块)
- [视频管理模块](#视频管理模块)
- [答题记录模块](#答题记录模块)
- [AI 助手模块](#ai 助手模块)
- [教师事件模块](#教师事件模块)
- [错题分析模块](#错题分析模块)
- [实验管理模块](#实验管理模块)
- [通用响应格式](#通用响应格式)

---

## 基础规范

### 1. 基础 URL

```
开发环境：http://localhost:8080
生产环境：根据实际情况配置
```

### 2. 请求头要求

所有 API 请求（除登录/注册外）都需要在 Header 中携带 JWT Token：

```http
Authorization: Bearer <your_jwt_token>
Content-Type: application/json
```

### 3. 数据格式

- **请求格式**：`application/json; charset=utf-8`
- **响应格式**：`application/json; charset=utf-8`
- **字符编码**：`UTF-8`

### 4. 时间格式

所有时间字段采用 ISO 8601 格式：
```
yyyy-MM-dd'T'HH:mm:ss
示例：2026-03-16T10:30:00
```

### 5. 权限说明

接口权限标记说明：
- 🔓 公开接口（无需认证）
- 👤 需要认证（学生/教师均可）
- 👨‍🎓 仅学生权限
- 👨‍🏫 仅教师权限

### 6. 文件上传规范

#### 图片上传要求

**适用场景**：
- 题目图片上传（`POST /questions/teacher/create`、`PUT /questions/teacher/update`）
- AI 问答请求（`POST /ai/student/realtimeQA`、`POST /ai/student/wrong-question-analysis`）

**编码格式**：
- 前端需将图片转换为 **Base64 编码**后传输
- 使用 Data URI Scheme 格式：`data:image/[格式];base64,[编码数据]`
- 示例：`data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...`

**支持的图片格式**：
- PNG (`.png`)
- JPG/JPEG (`.jpg`, `.jpeg`)

**编码说明**：
- 后端直接接收 Base64 编码字符串，不需要单独的文件上传接口
- Base64 编码会自动包含图片格式信息（通过 Data URI Scheme 前缀）
- 前端可根据需要压缩图片以减小传输体积（不影响功能，仅优化性能）

**请求字段示例**：
```json
{
  "videoId": 2001,
  "content": "质量为$2\\,\\mathrm{kg}$的物体...",
  "imageBase64": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."
}
```

**注意事项**：
1. Base64 编码会使数据量增加约 33%，前端可适当压缩图片优化传输性能
2. 确保图片清晰可读，避免过度压缩导致题目内容模糊
3. 不支持 GIF、SVG 等动态或矢量格式
4. 建议使用 Canvas 或 FileReader API 进行 Base64 转换

**前端转换示例代码**：
```
// 使用 FileReader 转换图片为 Base64
function imageToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file); // 自动添加 data:image/png;base64, 前缀
  });
}

// 使用示例
const fileInput = document.querySelector('input[type="file"]');
const base64String = await imageToBase64(fileInput.files[0]);
```

---

## 认证模块

**基础路径**: `/auth`


### 1. 用户登录 🔓

**接口描述**：通过“用户名 / 真实姓名”与密码进行身份认证，获取访问令牌。

**登录标识说明**：
- 支持输入“用户名”或“真实姓名”作为登录标识。
- 若同一标识既为某用户真实姓名、又为另一用户用户名，则优先匹配真实姓名。


**请求**：
```http
POST /auth/login
Content-Type: application/json
```


**请求体**：
```json
{
  "username": "student001", // 可为用户名或真实姓名
  "password": "password123"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 验证规则 | 说明 |
|------|------|------|----------|------|
| username | string | 是 | `^[a-zA-Z0-9_\u4e00-\u9fa5_]{2,50}$` | 用户名或真实姓名，2-50 位 |
| password | string | 是 | - | 密码 |

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": 1001,
    "username": "student001",
    "userType": "STUDENT",
    "realName": "张三"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| token | string | JWT 认证令牌，后续请求需在 Header 中携带 |
| userId | long | 用户 ID |
| username | string | 用户名 |
| userType | string | 用户类型：STUDENT（学生）/TEACHER（教师） |
| realName | string | 真实姓名 |

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 401 | 4105 | 密码错误 |
| 404 | 4102 | 用户不存在 |
| 403 | 4106 | 用户已被禁用 |

---

### 2. 用户注册 🔓

**接口描述**：创建新的用户账户

**请求**：
```http

POST /auth/register
Content-Type: application/json
```

**请求体**：
```json
{
  "username": "newstudent001",
  "password": "password123",
  "realName": "李四",
  "className": "高三 (1) 班",
  "userType": "STUDENT"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 验证规则 | 说明 |
|------|------|------|----------|------|
| username | string | 是 | `^[a-zA-Z0-9_]{3,50}$` | 用户名。**不能与任何真实姓名重复**（单向唯一性约束） |
| password | string | 是 | - | 密码 |
| realName | string | 是 | - | 真实姓名 |
| className | string | 否 | - | 班级名称 |
| userType | string | 是 | STUDENT/TEACHER | 用户类型 |

**成功响应** (HTTP 201)：
```json
{
  "code": 201,
  "message": "资源创建成功",
  "data": {
    "username": "newstudent001",
    "userType": "STUDENT"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 409 | 4107 | 用户名重复或用户名与某真实姓名冲突 |
| 409 | 4108 | 真实姓名重复 |
| 400 | 4109 | 用户名不合法 |
| 400 | 4110 | 真实姓名不合法 |

---

### 3. 用户登出 👤

**接口描述**：退出当前登录会话

**请求**：
```http
POST /auth/logout
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "登出成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

## 用户管理模块

**基础路径**: `/user`

### 1. 获取当前用户信息 👤

**接口描述**：获取当前登录用户的完整信息

**请求**：
```http
GET /user/me
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1001,
    "username": "student001",
    "realName": "张三",
    "className": "高三 (1) 班",
    "userType": "STUDENT",
    "active": true,
    "createTime": "2026-01-01T10:00:00",
    "lastLogin": "2026-03-16T14:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 用户 ID |
| username | string | 用户名 |
| realName | string | 真实姓名 |
| className | string | 班级名称 |
| userType | string | 用户类型：STUDENT/TEACHER |
| active | boolean | 账户是否激活 |
| createTime | datetime | 创建时间 |
| lastLogin | datetime | 最后登录时间 |

---

### 2. 更新当前用户信息 👤

**接口描述**：用户更新自己的基本信息（支持增量更新）

**请求**：
```http
PUT /user/me
Authorization: Bearer <token>
Content-Type: application/json
```


**请求体**：
```json
{
  "username": "new_username", // 新用户名（如有变更，需校验不能与任何真实姓名重复）
  "password": "new_password",
  "realName": "新名字",
  "className": "新班级"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 否 | 新用户名。**如有变更，不能与任何真实姓名重复**（单向唯一性约束） |
| password | string | 否 | 新密码 |
| realName | string | 否 | 新真实姓名 |
| className | string | 否 | 新班级 |

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1001,
    "username": "new_username",
    "realName": "新名字",
    "className": "新班级",
    "userType": "STUDENT",
    "active": true,
    "createTime": "2026-01-01T10:00:00",
    "lastLogin": "2026-03-16T14:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 3. 根据用户 ID 获取用户信息 👨‍🏫

**接口描述**：教师根据用户 ID 查询用户详细信息

**请求**：
```http
GET /user/teacher/{id}
Authorization: Bearer <token>
```

**路径参数**：
- `id`: 用户 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": { /* UserResponse 对象 */ },
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4102 | 用户不存在 |
| 403 | 4801 | 权限不足 |

---

### 4. 根据真实姓名获取用户 👨‍🏫

**接口描述**：教师根据学生真实姓名查询用户信息

**请求**：
```http
GET /user/teacher/realname/{realName}
Authorization: Bearer <token>
```

**路径参数**：
- `realName`: 学生真实姓名

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": { /* UserResponse 对象 */ },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 5. 获取所有用户列表 👨‍🏫

**接口描述**：教师获取系统中所有用户的列表信息

**请求**：
```http
GET /user/teacher/all
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1001,
      "username": "student001",
      "realName": "张三",
      "className": "高三 (1) 班",
      "userType": "STUDENT",
      "active": true,
      "createTime": "2026-01-01T10:00:00",
      "lastLogin": "2026-03-16T14:30:00"
    },
    {
      "id": 1002,
      "username": "teacher001",
      "realName": "王老师",
      "className": null,
      "userType": "TEACHER",
      "active": true,
      "createTime": "2026-01-01T09:00:00",
      "lastLogin": "2026-03-16T15:00:00"
    }
  ],
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 6. 更新指定用户信息 👨‍🏫

**接口描述**：教师更新指定用户的信息（支持增量更新）

**请求**：
```http
PUT /user/teacher/update
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "userId": 1001,
  "realName": "新名字",
  "className": "新班级",
  "active": true,
  "userType": "STUDENT"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | long | 是 | 目标用户 ID |
| realName | string | 否 | 新真实姓名 |
| className | string | 否 | 新班级 |
| active | boolean | 否 | 账户状态 |
| userType | string | 否 | 用户类型 |

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": { /* UserResponse 对象 */ },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 7. 删除指定用户 👨‍🏫

**接口描述**：教师删除指定用户账户

**请求**：
```http
DELETE /user/teacher/{id}
Authorization: Bearer <token>
```

**路径参数**：
- `id`: 用户 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

## 题目管理模块

**基础路径**: `/questions`

### 1. 创建题目 👨‍🏫

**接口描述**：教师创建新的练习题目

**请求**：
```http
POST /questions/teacher/create
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "content": "质量为$2\\,\\mathrm{kg}$的物体，受到$10\\,\\mathrm{N}$的水平力作用，求加速度$a$。",
  "options": {
    "A": "$2\\,\\mathrm{m/s^2}$",
    "B": "$5\\,\\mathrm{m/s^2}$",
    "C": "$10\\,\\mathrm{m/s^2}$",
    "D": "$20\\,\\mathrm{m/s^2}$"
  },
  "correctOption": "B",
  "explanation": "根据牛顿第二定律$F=ma$，可得$a=\\frac{F}{m}=\\frac{10}{2}=5\\,\\mathrm{m/s^2}$",
  "difficulty": 3,
  "explanationVideoMapping": {
    "A": "123",
    "B": "456",
    "C": "789",
    "D": "101"
  },
  "imageBase64": "data:image/png;base64,iVBORw0KGgo..."
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 是 | 题目内容（LaTeX 格式） |
| options | object | 是 | 选项映射，key 为 A/B/C/D，value 为选项内容 |
| correctOption | string | 是 | 正确答案选项标识（A/B/C/D） |
| explanation | string | 否 | 题目解析（LaTeX 格式） |
| difficulty | integer | 否 | 难度等级（1-5） |
| explanationVideoMapping | object | 否 | 讲解视频映射 |
| imageBase64 | string | 否 | 题目图片 Base64 编码 |

**重要说明**：
- 题目实体完全独立，不包含 videoId 字段
- 视频通过 `questionId` 字段单向关联题目

**成功响应** (HTTP 201)：
```json
{
  "code": 201,
  "message": "题目创建成功",
  "data": {
    "id": 1001,
    "content": "质量为$2\\,\\mathrm{kg}$的物体...",
    "options": {
      "A": "$2\\,\\mathrm{m/s^2}$",
      "B": "$5\\,\\mathrm{m/s^2}$",
      "C": "$10\\,\\mathrm{m/s^2}$",
      "D": "$20\\,\\mathrm{m/s^2}$"
    },
    "correctOption": "B",
    "explanation": "根据牛顿第二定律...",
    "difficulty": 3,
    "explanationVideoMapping": {
      "A": "123",
      "B": "456",
      "C": "789",
      "D": "101"
    },
    "imageBase64": "data:image/png;base64,...",
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 409 | 4203 | 题目内容重复 |

---

### 2. 删除题目 👨‍🏫

**接口描述**：根据 ID 删除指定题目

**请求**：
```http
DELETE /questions/teacher/delete/{id}
Authorization: Bearer <token>
```

**路径参数**：
- `id`: 题目 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4202 | 题目不存在 |

---

### 3. 更新题目 👨‍🏫

**接口描述**：全量覆盖更新题目信息

**请求**：
```http
PUT /questions/teacher/update
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "id": 1001,
  "content": "质量为$2\\,\\mathrm{kg}$的物体...",
  "options": {
    "A": "$2\\,\\mathrm{m/s^2}$",
    "B": "$5\\,\\mathrm{m/s^2}$",
    "C": "$10\\,\\mathrm{m/s^2}$",
    "D": "$20\\,\\mathrm{m/s^2}$"
  },
  "correctOption": "B",
  "explanation": "根据牛顿第二定律...",
  "difficulty": 3,
  "explanationVideoMapping": {
    "A": "123",
    "B": "456"
  },
  "imageBase64": "data:image/png;base64,..."
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 题目 ID |
| content | string | 是 | 题目内容 |
| options | object | 是 | 选项映射 |
| correctOption | string | 是 | 正确选项 |
| explanation | string | 否 | 解析说明 |
| difficulty | integer | 否 | 难度等级 |
| explanationVideoMapping | object | 否 | 视频映射 |
| imageBase64 | string | 否 | 图片 Base64 |

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "题目更新成功",
  "data": { /* QuestionResponse 对象 */ },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 4. 获取题目详情 👤

**接口描述**：根据 ID 获取题目详细信息（无需身份认证）

**请求**：
```http
GET /questions/teacher/get/{id}
```

**路径参数**：
- `id`: 题目 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1001,
    "content": "质量为$2\\,\\mathrm{kg}$的物体...",
    "options": {
      "A": "$2\\,\\mathrm{m/s^2}$",
      "B": "$5\\,\\mathrm{m/s^2}$"
    },
    "correctOption": "B",
    "explanation": "根据牛顿第二定律...",
    "difficulty": 3,
    "explanationVideoMapping": {
      "A": "123",
      "B": "456"
    },
    "imageBase64": "data:image/png;base64,...",
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 5. 获取所有题目 👨‍🏫

**接口描述**：批量获取数据库中所有题目

**请求**：
```http
GET /questions/teacher/get/all
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { /* QuestionResponse 对象 */ },
    { /* QuestionResponse 对象 */ }
  ],
  "timestamp": 1710000000000,
  "reason": null
}
```

---

## 视频管理模块

**基础路径**: `/video/teacher`

### 1. 创建视频 👨‍🏫

**接口描述**：教师创建新的教学视频

**请求**：
```http
POST /video/teacher
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "title": "牛顿第一定律讲解",
  "url": "https://example.com/video.mp4",
  "knowledgePoint": "力学基础",
  "coverUrl": "https://example.com/cover.jpg",
  "duration": 1200,
  "description": "详细介绍牛顿第一定律的概念和应用",
  "questionId": 1001
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | 是 | 视频标题 |
| url | string | 是 | 视频播放链接 |
| knowledgePoint | string | 否 | 关联的知识点名称 |
| coverUrl | string | 否 | 视频封面图片链接 |
| duration | integer | 否 | 视频时长（秒） |
| description | string | 否 | 视频描述信息 |
| questionId | long | 否 | 关联的题目 ID |

**成功响应** (HTTP 201)：
```json
{
  "code": 201,
  "message": "资源创建成功",
  "data": {
    "id": 2001,
    "title": "牛顿第一定律讲解",
    "url": "https://example.com/video.mp4",
    "knowledgePoint": "力学基础",
    "coverUrl": "https://example.com/cover.jpg",
    "duration": 1200,
    "description": "详细介绍牛顿第一定律的概念和应用",
    "questionId": 1001,
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 409 | 4303 | 视频已存在 |
| 400 | 4305 | 视频格式不支持 |

---

### 2. 删除视频（按 ID） 👨‍🏫

**接口描述**：根据 ID 删除指定视频

**请求**：
```http
DELETE /video/teacher/{id}
Authorization: Bearer <token>
```

**路径参数**：
- `id`: 视频 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4302 | 视频不存在 |

---

### 3. 删除视频（按标题） 👨‍🏫

**接口描述**：根据标题删除视频

**请求**：
```http
DELETE /video/teacher/title/{title}
Authorization: Bearer <token>
```

**路径参数**：
- `title`: 视频标题

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 4. 更新视频 👨‍🏫

**接口描述**：更新视频信息

**请求**：
```http
PUT /video/teacher/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

**路径参数**：
- `id`: 视频 ID

**请求体**：
```json
{
  "title": "新标题",
  "url": "https://example.com/new-video.mp4",
  "knowledgePoint": "更新的知识点",
  "coverUrl": "https://example.com/new-cover.jpg",
  "duration": 1500,
  "description": "新的描述信息",
  "questionId": 1002
}
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 2001,
    "title": "新标题",
    "url": "https://example.com/new-video.mp4",
    "knowledgePoint": "更新的知识点",
    "coverUrl": "https://example.com/new-cover.jpg",
    "duration": 1500,
    "description": "新的描述信息",
    "questionId": 1002,
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 5. 获取视频（按 ID） 👤

**接口描述**：根据 ID 获取视频详细信息

**请求**：
```http
GET /video/teacher/{id}
Authorization: Bearer <token>
```

**路径参数**：
- `id`: 视频 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 2001,
    "title": "牛顿第一定律讲解",
    "url": "https://example.com/video.mp4",
    "knowledgePoint": "力学基础",
    "coverUrl": "https://example.com/cover.jpg",
    "duration": 1200,
    "description": "详细介绍牛顿第一定律的概念和应用",
    "questionId": 1001,
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 6. 获取视频（按标题） 👤

**接口描述**：根据标题获取视频详细信息

**请求**：
```http
GET /video/teacher/title/{title}
Authorization: Bearer <token>
```

**路径参数**：
- `title`: 视频标题

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 2001,
    "title": "牛顿第一定律讲解",
    "url": "https://example.com/video.mp4",
    "knowledgePoint": "力学基础",
    "coverUrl": "https://example.com/cover.jpg",
    "duration": 1200,
    "description": "详细介绍牛顿第一定律的概念和应用",
    "questionId": 1001,
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 7. 获取所有视频 👤

**接口描述**：获取所有视频列表

**请求**：
```http
GET /video/teacher
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 2001,
      "title": "牛顿第一定律讲解",
      "url": "https://example.com/video.mp4",
      "knowledgePoint": "力学基础",
      "coverUrl": "https://example.com/cover.jpg",
      "duration": 1200,
      "description": "详细介绍牛顿第一定律的概念和应用",
      "questionId": 1001,
      "createdAt": "2026-03-16T10:30:00",
      "updatedAt": "2026-03-16T10:30:00"
    }
  ],
  "timestamp": 1710000000000,
  "reason": null
}
```

---

## 视频上传模块

**基础路径**: `/video/upload`

### 1. 上传视频文件 👨‍🏫

**接口描述**：教师上传视频文件到本地存储，返回访问 URL

**请求**：
```http
POST /video/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求参数**：
- `file`: 视频文件（multipart/form-data 格式）

**成功响应** (HTTP 201)：
```json
{
  "code": 201,
  "message": "视频上传成功",
  "data": {
    "url": "/videos/abc123.mp4",
    "originalFilename": "lesson1.mp4",
    "fileSize": 104857600,
    "duration": 180
  },
  "timestamp": 1710756600000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| url | string | 视频访问 URL，可直接访问 |
| originalFilename | string | 原始文件名 |
| fileSize | long | 文件大小（字节） |
| duration | integer | 视频时长（秒） |

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 400 | 4900 | 文件为空或格式不支持 |
| 500 | 5304 | 视频上传失败 |

---

### 2. 删除视频文件 👨‍🏫

**接口描述**：根据 URL 删除本地服务器上的视频物理文件

**请求**：
```http
DELETE /video/upload?url=/videos/abc123.mp4
Authorization: Bearer <token>
```

**请求参数**：
- `url`: 视频访问 URL（查询参数）

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710756600000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4302 | 视频文件不存在 |

---

## 答题记录模块

**基础路径**: `/answer-records`

### 1. 提交答题记录 👨‍🎓

**接口描述**：学生提交答题结果，系统自动判断正误并保存记录

**请求**：
```http
POST /answer-records/student/submit
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "questionId": 1001,
  "selectedOption": "B",
  "sessionId": "sess_123456"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| questionId | long | 是 | 题目 ID |
| selectedOption | string | 是 | 选择的选项（A/B/C/D） |
| sessionId | string | 是 | 会话 ID |

**成功响应** (HTTP 201)：
```json
{
  "code": 201,
  "message": "答题记录提交成功",
  "data": {
    "recordId": "rec_123456",
    "questionId": 1001,
    "userId": 1001,
    "selectedOption": "B",
    "isCorrect": true,
    "sessionId": "sess_123456",
    "answeredAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| recordId | string | 答题记录 ID |
| questionId | long | 题目 ID |
| userId | long | 用户 ID |
| selectedOption | string | 选择的选项 |
| isCorrect | boolean | 是否正确 |
| sessionId | string | 会话 ID |
| answeredAt | datetime | 答题时间 |

---

### 2. 获取我的答题记录 👨‍🎓

**接口描述**：查询当前登录用户的所有答题历史记录

**请求**：
```http
GET /answer-records/student/my
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "recordId": "rec_123456",
        "questionId": 1001,
        "userId": 1001,
        "selectedOption": "B",
        "isCorrect": true,
        "sessionId": "sess_123456",
        "answeredAt": "2026-03-16T10:30:00"
      }
    ],
    "totalCount": 1
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 3. 获取我的错题记录 👨‍🎓

**接口描述**：查询当前登录用户的所有错误答题记录

**请求**：
```http
GET /answer-records/student/my/wrong
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "recordId": "rec_123456",
        "questionId": 1001,
        "userId": 1001,
        "selectedOption": "A",
        "isCorrect": false,
        "sessionId": "sess_123456",
        "answeredAt": "2026-03-16T10:30:00"
      }
    ],
    "totalCount": 1
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 4. 获取用户答题记录 👤

**接口描述**：根据权限查询指定用户的答题记录

**请求**：
```http
GET /answer-records/user/{userId}
Authorization: Bearer <token>
```

**路径参数**：
- `userId`: 用户 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [/* AnswerRecordResponse 数组 */],
    "totalCount": 1
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 403 | 4801 | 权限不足（学生只能查自己） |

---

### 5. 获取用户错题记录 👤

**接口描述**：根据权限查询指定用户的错题记录

**请求**：
```http
GET /answer-records/user/wrong/{userId}
Authorization: Bearer <token>
```

**路径参数**：
- `userId`: 用户 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [/* AnswerRecordResponse 数组 */],
    "totalCount": 1
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 6. 删除答题记录 👨‍🏫

**接口描述**：教师删除指定的答题记录

**请求**：
```http
DELETE /answer-records/teacher/{recordId}
Authorization: Bearer <token>
```

**路径参数**：
- `recordId`: 答题记录 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4402 | 答题记录不存在 |

---

### 7. 批量删除答题记录 👨‍🏫

**接口描述**：教师批量删除指定时间范围内的答题记录

**请求**：
```http
DELETE /answer-records/teacher/batch
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "startTime": "2026-03-01T00:00:00",
  "endTime": "2026-03-15T23:59:59"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| startTime | datetime | 是 | 开始时间 |
| endTime | datetime | 是 | 结束时间 |

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 8. 统计总答题记录数 👨‍🏫

**接口描述**：教师查询整个系统的答题记录总数

**请求**：
```http
GET /answer-records/teacher/count
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": 1000,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 9. 获取所有用户答题记录 👨‍🏫

**接口描述**：教师查询系统内所有用户的答题记录

**请求**：
```http
GET /answer-records/teacher/all
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [/* AnswerRecordResponse 数组 */],
    "totalCount": 100
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 10. 获取所有用户错题记录 👨‍🏫

**接口描述**：教师查询系统内所有用户的错题记录

**请求**：
```http
GET /answer-records/teacher/wrong/all
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [/* AnswerRecordResponse 数组 */],
    "totalCount": 50
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 11. 获取会话答题记录 👤

**接口描述**：根据会话 ID 获取该会话内的所有答题记录

**请求**：
```http
GET /answer-records/session/{sessionId}
Authorization: Bearer <token>
```

**路径参数**：
- `sessionId`: 会话 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "会话答题记录查询成功",
  "data": {
    "records": [/* AnswerRecordResponse 数组 */],
    "totalCount": 10
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 12. 获取学生最近会话记录 👨‍🏫

**接口描述**：教师查询指定学生的最近一次学习会话中的所有答题记录

**请求**：
```http
GET /answer-records/teacher/{studentId}/latest-session
Authorization: Bearer <token>
```

**路径参数**：
- `studentId`: 学生 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "学生最近会话记录查询成功",
  "data": {
    "records": [/* AnswerRecordResponse 数组 */],
    "totalCount": 10
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 13. 根据姓名获取学生最近会话记录 👨‍🏫

**接口描述**：教师通过学生真实姓名查询其最近一次学习会话中的所有答题记录

**请求**：
```http
GET /answer-records/teacher/realname/{studentName}/latest-session
Authorization: Bearer <token>
```

**路径参数**：
- `studentName`: 学生真实姓名

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "学生最近会话记录查询成功",
  "data": {
    "records": [/* AnswerRecordResponse 数组 */],
    "totalCount": 10
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

---

## AI 助手模块

**基础路径**: `/ai`

### 1. 实时问答（流式） 👨‍🎓

**接口描述**：学生向 AI 提问，以 SSE 流式方式返回答案

**请求**：
```http
POST /ai/student/realtimeQA
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "questionId": 1001,
  "question": "这道题的解题思路是什么？"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| questionId | long | 是 | 题目 ID |
| question | string | 是 | 具体问题 |

**响应类型**：SSE (Server-Sent Events)

**流式响应示例**：
```
event: content
data: 本题考查的是牛顿第二定律的应用。\n\n

event: content
data: 首先，我们需要明确已知条件：\n

event: content
data: - 物体质量 m = 2 kg\n


event: content
data: - 作用力 F = 10 N\n\n


event: content
data: 根据牛顿第二定律 F = ma，可得：\n


event: content
data: a = F/m = 10/2 = 5 m/s²\n\n


event: content
data: 因此，正确答案是 B。\n


event: complete
data: {"promptTokens": 85, "completionTokens": 156, "totalTokens": 241}


```

**SSE 事件类型**：

| 事件名称 | 说明 | 数据格式 |


|---------|------|---------|


| `content` | AI 生成的内容片段（**纯字符串**，直接追加） | 字符串 |


| `reasoning` | AI 深度思考过程（可选） | `{reasoning}` |


| `progress` | 处理进度（可选） | `{progress}` |


| `complete` | 传输完成统计 | `{promptTokens, completionTokens, totalTokens}` |


| `error` | 错误信息 | `{code, message}` |



**重要说明**：
- `content` 事件的数据是**纯字符串**，直接追加到已接收内容后面即可


- 前端不应使用 `JSON.parse()` 解析 content 事件数据


- 用户可随时点击"停止生成"按钮中断请求，已生成的内容会被保留



**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 400 | 4509 | 题目 ID 为空 |
| 408 | 4508 | 会话已失效 |
| 503 | 5501 | AI 服务不可用 |
| 504 | 5502 | AI 响应超时 |

---

### 2. 错题分析（流式） 👨‍🎓

**接口描述**：学生对错题进行分析，以 SSE 流式方式返回分析结果

**请求**：
```http
POST /ai/student/wrong-question-analysis
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "questionId": 1001,
  "question": "我为什么做错了这道题？"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| questionId | long | 是 | 题目 ID |
| question | string | 是 | 具体问题 |

**响应类型**：SSE (Server-Sent Events)

**流式响应示例**：
```
event: content
data: 错误原因分析：\n\n

event: content
data: 1. 概念理解不清：对牛顿第二定律的理解不够深入\n



event: content
data: 2. 公式应用错误：混淆了 F=ma 和 a=F/m 的关系\n\n



event: content
data: 正确解法：\n



event: content
data: 根据 F=ma，可得 a=F/m\n



event: content
data: 代入数据：a=10/2=5 m/s²\n\n



event: content
data: 建议复习知识点：力的合成与分解\n



event: complete
data: {"promptTokens": 120, "completionTokens": 180, "totalTokens": 300}
```

**SSE 事件类型**：与实时问答相同

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4202 | 题目不存在 |
| 408 | 4508 | 会话已失效 |
| 503 | 5601 | 错题分析服务不可用 |
| 500 | 5602 | 词云分析失败 |

---

### 3. 生成学生报告（流式） 👨‍🎓

**接口描述**：生成学生个人学习报告的流式输出

**请求**：
```http
GET /ai/student/report
Authorization: Bearer <token>
```

**响应类型**：SSE (Server-Sent Events)

**流式响应示例**：
```
data: {"type": "start", "content": "正在生成学习报告..."}

data: {"type": "content", "content": "# 学习报告\n\n"}


data: {"type": "content", "content": "## 基本信息\n"}


data: {"type": "content", "content": "学生：张三\n"}


data: {"type": "content", "content": "班级：高三 (1) 班\n\n"}


data: {"type": "content", "content": "## 学习情况\n"}


data: {"type": "content", "content": "总答题数：100 题\n"}


data: {"type": "content", "content": "正确率：75%\n\n"}


data: {"type": "content", "content": "## 薄弱知识点\n"}


data: {"type": "content", "content": "1. 牛顿第三定律\n"}


data: {"type": "content", "content": "2. 能量守恒定律\n\n"}


data: {"type": "content", "content": "## 学习建议\n"}


data: {"type": "content", "content": "建议加强对力学部分的学习...\n"}


data: {"type": "end", "content": "报告生成完成"}
```

**SSE 事件类型**：与实时问答相同

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 408 | 4508 | 会话已失效 |
| 503 | 5501 | AI 服务不可用 |

---

### 4. 生成教师综合分析（流式） 👨‍🏫

**接口描述**：生成教师对全班学生的综合分析报告

**请求**：
```http
GET /ai/teacher/report
Authorization: Bearer <token>
```

**响应类型**：SSE (Server-Sent Events)

**流式响应示例**：
```
data: {"type": "start", "content": "正在生成综合分析报告..."}

data: {"type": "content", "content": "# 班级学习分析报告\n\n"}


data: {"type": "content", "content": "## 整体情况\n"}


data: {"type": "content", "content": "班级总人数：50 人\n"}


data: {"type": "content", "content": "平均正确率：78%\n\n"}


data: {"type": "content", "content": "## 共性问题分析\n"}


data: {"type": "content", "content": "1. 牛顿第三定律理解不清（错误率 45%）\n"}


data: {"type": "content", "content": "2. 电路分析能力较弱（错误率 38%）\n\n"}


data: {"type": "content", "content": "## 教学建议\n"}


data: {"type": "content", "content": "1. 加强牛顿第三定律的讲解\n"}


data: {"type": "content", "content": "2. 增加电路分析练习题\n"}


event: complete
data: {"promptTokens": 180, "completionTokens": 280, "totalTokens": 460}
```

**SSE 事件类型**：与实时问答相同

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 403 | 4802 | 教师权限不足 |
| 503 | 5501 | AI 服务不可用 |

---

### 5. 获取会话历史记录 👤

**接口描述**：获取当前会话的历史对话记录

**请求**：
```http
GET /ai/session/history
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "sessionId": "sess_123456",
      "userId": 1001,
      "question": "这道题怎么做？",
      "answer": "首先...",
      "createdAt": "2026-03-16T10:30:00"
    }
  ],
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 历史记录 ID |
| sessionId | string | 会话 ID |
| userId | long | 用户 ID |
| question | string | 用户问题 |
| answer | string | AI 回答 |
| createdAt | datetime | 创建时间 |

---

### 6. 清除会话 👤

**接口描述**：清除当前会话的所有历史记录

**请求**：
```http
DELETE /ai/session
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

---

### 7. 获取学生最新报告 👨‍🏫

**接口描述**：教师获取指定学生的最新学习报告

**请求**：
```http
GET /ai/teacher/report/{studentId}
Authorization: Bearer <token>
```

**路径参数**：
- `studentId`: 学生 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "reportId": "report_123",
    "studentId": 1001,
    "studentName": "张三",
    "className": "高三 (1) 班",
    "totalQuestions": 100,
    "correctRate": 0.75,
    "weakPoints": ["牛顿第三定律", "能量守恒"],
    "suggestions": ["建议加强力学部分学习"],
    "generatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| reportId | string | 报告 ID |
| studentId | long | 学生 ID |
| studentName | string | 学生姓名 |
| className | string | 班级名称 |
| totalQuestions | integer | 总答题数 |
| correctRate | number | 正确率（0-1） |
| weakPoints | array | 薄弱知识点列表 |
| suggestions | array | 学习建议列表 |
| generatedAt | datetime | 生成时间 |

---

## 教师事件模块

**基础路径**: `/teacher/events`

### 1. 发布教师事件 👨‍🏫

**接口描述**：教师发布全局学习事件作为时间基准点。全局事件在系统中唯一，所有用户共享。

**请求**：
```http
POST /teacher/events
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "eventTitle": "新学期开始",
  "eventDescription": "2026 年春季学期正式开始",
  "eventTimestamp": "2026-03-01T08:00:00"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| eventTitle | string | 是 | 事件标题 |
| eventDescription | string | 否 | 事件描述 |
| eventTimestamp | datetime | 是 | 事件时间 |

**成功响应** (HTTP 201)：
```json
{
  "code": 201,
  "message": "资源创建成功",
  "data": {
    "id": 1,
    "teacherId": 1002,
    "isGlobal": true,
    "eventTitle": "新学期开始",
    "eventDescription": "2026 年春季学期正式开始",
    "eventTimestamp": "2026-03-01T08:00:00",
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 事件 ID |
| teacherId | long | 创建事件的教师 ID |
| isGlobal | boolean | 是否为全局事件（固定为 true） |
| eventTitle | string | 事件标题 |
| eventDescription | string | 事件描述 |
| eventTimestamp | datetime | 事件时间 |
| createdAt | datetime | 创建时间 |
| updatedAt | datetime | 更新时间 |

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 409 | 4702 | 全局事件已存在 |

---

### 2. 获取教师事件 👤

**接口描述**：查询全局事件信息。所有认证用户可访问。

**请求**：
```http
GET /teacher/events
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "teacherId": 1002,
    "isGlobal": true,
    "eventTitle": "新学期开始",
    "eventDescription": "2026 年春季学期正式开始",
    "eventTimestamp": "2026-03-01T08:00:00",
    "createdAt": "2026-03-16T10:30:00",
    "updatedAt": "2026-03-16T10:30:00"
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**业务规则**：
- 如果全局事件不存在，返回 `data: null`
- 所有认证用户（学生/教师）均可访问

---

### 3. 删除教师事件 👨‍🏫

**接口描述**：删除全局事件并清理关联数据（答案记录、对话历史、学生报告）。删除操作幂等。

**请求**：
```http
DELETE /teacher/events
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710000000000,
  "reason": null
}
```

**业务规则**：
- 仅教师权限可删除
- 删除后自动清理所有关联数据
- 事件不存在时也返回成功（幂等性）

---

## 错题分析模块

**基础路径**: `/question-error-analysis`

### 1. 获取题目错误统计 👨‍🏫

**接口描述**：教师获取所有有错误记录的题目统计信息，按错误人次降序排列

**请求**：
```http
GET /question-error-analysis/statistics
Authorization: Bearer <token>
```

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "questionId": 1,
      "questionContent": "质量为2kg的物体...",
      "questionOptions": ["A选项", "B选项", "C选项", "D选项"],
      "correctAnswer": "B",
      "questionAnalysis": "根据牛顿第二定律...",
      "difficulty": "中等",
      "imageBase64": "data:image/png;base64,...",
      "errorCount": 15,
      "errorRate": 75.0
    }
  ],
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| questionId | long | 题目 ID |
| questionContent | string | 题目内容 |
| questionOptions | array | 题目选项 |
| correctAnswer | string | 正确答案 |
| questionAnalysis | string | 题目解析 |
| difficulty | string | 难度等级 |
| imageBase64 | string | 题目图片（Base64） |
| errorCount | integer | 错误人次 |
| errorRate | number | 错误率（百分比） |

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 401 | 4111 | 未认证 |
| 403 | 4802 | 权限不足（非教师身份） |

---

### 2. 获取题目错误详情 👨‍🏫

**接口描述**：教师获取指定题目的学生错误列表和词云数据

**请求**：
```http
GET /question-error-analysis/{questionId}/details
Authorization: Bearer <token>
```

**路径参数**：
- `questionId`: 题目 ID

**成功响应** (HTTP 200)：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "questionId": 1,
    "questionContent": "质量为2kg的物体...",
    "correctAnswer": "B",
    "errorCount": 15,
    "errorRate": 75.0,
    "wordCloudData": {
      "力学基础": 8,
      "公式记忆": 5,
      "概念混淆": 3
    },
    "studentList": [
      {
        "recordId": "rec_123",
        "userId": 1001,
        "username": "student001",
        "realName": "张三",
        "selectedOption": "A",
        "wrongReason": "未正确理解牛顿第二定律",
        "answeredAt": "2026-03-16T10:30:00"
      }
    ]
  },
  "timestamp": 1710000000000,
  "reason": null
}
```

**响应字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| questionId | long | 题目 ID |
| questionContent | string | 题目内容 |
| correctAnswer | string | 正确答案 |
| errorCount | integer | 错误人次 |
| errorRate | number | 错误率（百分比） |
| wordCloudData | object | 词云数据（错误原因频次统计） |
| studentList | array | 学生错误详情列表 |

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4202 | 题目不存在 |
| 403 | 4802 | 权限不足（非教师身份） |

---

### 3. 教师题目分析流式生成 👨‍🏫

**接口描述**：教师对指定题目进行流式 AI 分析，纯 AI 分析不包含初始化数据

**请求**：
```http
POST /question-error-analysis/stream
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**：
```json
{
  "questionId": 1,
  "question": "请分析这道题的典型错误原因"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| questionId | long | 是 | 题目 ID |
| question | string | 否 | 自定义分析问题（可选） |

**响应格式**：SSE 流式响应
```
event: content
data: 本题的错误主要集中在以下几个方面...

event: content
data: 1. 力学基础概念理解不清晰...

event: reasoning
data: {"reasoning": "分析思路：首先查看学生的答题记录..."}

event: complete
data: {"promptTokens": 150, "completionTokens": 300, "totalTokens": 450}
```

**SSE 事件说明**：

| 事件类型 | 说明 | 数据格式 |
|---------|------|--------|
| content | AI 生成的内容片段 | 纯字符串，直接追加 |
| reasoning | AI 深度思考过程 | `{reasoning: string}` |
| complete | 传输完成统计 | `{promptTokens, completionTokens, totalTokens}` |
| error | 错误信息 | `{code, message}` |

**常见错误**：

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 400 | 4900 | 参数校验失败 |
| 404 | 4202 | 题目不存在 |
| 408 | 4508 | 会话已失效 |
| 503 | 5501 | AI 服务不可用 |

---

## 实验管理模块

**基础路径**: `/experiments`

### 1. 创建实验 👨‍🏫

**接口描述**: 教师创建新的实验项目

**请求**:
``http
POST /experiments/teacher/create
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
```json
{
  "name": "牛顿第二定律验证实验",
  "questionIds": "[1001, 1002, 1003]",
  "description": "通过实验验证牛顿第二定律 F=ma 的关系",
  "orderNumber": 1
}
```

**请求字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 实验名称（全局唯一，≤255字符） |
| questionIds | string | 是 | 题目ID列表的 JSON 字符串，如 `"[1,2,3]"` |
| description | string | 是 | 实验描述信息 |
| orderNumber | integer | 是 | 排序序号（正整数，全局唯一） |

**成功响应** (HTTP 201):
``json
{
  "code": 201,
  "message": "实验创建成功",
  "data": {
    "id": 1,
    "name": "牛顿第二定律验证实验",
    "questionIds": [1001, 1002, 1003],
    "questionIdsJson": "[1001,1002,1003]",
    "description": "通过实验验证牛顿第二定律 F=ma 的关系",
    "orderNumber": 1,
    "createdAt": "2026-03-18T10:30:00",
    "updatedAt": "2026-03-18T10:30:00"
  },
  "timestamp": 1710756600000,
  "reason": null
}
```

**响应字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 实验 ID |
| name | string | 实验名称 |
| questionIds | array | 题目 ID 列表（数组格式，便于前端使用） |
| questionIdsJson | string | 题目 ID 列表（JSON 字符串格式） |
| description | string | 实验描述 |
| orderNumber | integer | 排序序号 |
| createdAt | datetime | 创建时间 |
| updatedAt | datetime | 更新时间 |

**常见错误**:

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 409 | 4601 | 实验名称已存在 |
| 409 | 4603 | 排序序号已存在 |
| 400 | 4900 | 参数校验失败 |

---

### 2. 删除实验 👨‍🏫

**接口描述**: 根据 ID 删除指定实验

**请求**:
``http
DELETE /experiments/teacher/delete/{id}
Authorization: Bearer <token>
```

**路径参数**:
- `id`: 实验 ID

**成功响应** (HTTP 200):
``json
{
  "code": 200,
  "message": "操作成功",
  "data": null,
  "timestamp": 1710756600000,
  "reason": null
}
```

**常见错误**:

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4602 | 实验不存在 |

---

### 3. 更新实验 👨‍🏫

**接口描述**: 更新实验信息

**请求**:
``http
PUT /experiments/teacher/update
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
``json
{
  "id": 1,
  "name": "牛顿第二定律验证实验",
  "questionIds": "[1001, 1003]",
  "description": "更新后的描述",
  "orderNumber": 2
}
```

**请求字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | long | 是 | 实验 ID |
| name | string | 是 | 实验名称 |
| questionIds | string | 是 | 题目ID列表的 JSON 字符串 |
| description | string | 是 | 实验描述 |
| orderNumber | integer | 是 | 排序序号（正整数，全局唯一） |

**成功响应** (HTTP 200):
``json
{
  "code": 200,
  "message": "实验更新成功",
  "data": {
    "id": 1,
    "name": "牛顿第二定律验证实验",
    "questionIds": [1001, 1003],
    "questionIdsJson": "[1001,1003]",
    "description": "更新后的描述",
    "orderNumber": 2,
    "createdAt": "2026-03-18T10:30:00",
    "updatedAt": "2026-03-18T11:00:00"
  },
  "timestamp": 1710756600000,
  "reason": null
}
```

**常见错误**:

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4602 | 实验不存在 |
| 409 | 4601 | 实验名称已存在 |
| 409 | 4603 | 排序序号已存在 |

---

### 4. 获取实验详情 👤

**接口描述**: 根据 ID 获取实验详细信息

**请求**:
``http
GET /experiments/{id}
```

**路径参数**:
- `id`: 实验 ID

**成功响应** (HTTP 200):
``json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "牛顿第二定律验证实验",
    "questionIds": [1001, 1002, 1003],
    "questionIdsJson": "[1001,1002,1003]",
    "description": "通过实验验证牛顿第二定律 F=ma 的关系",
    "orderNumber": 1,
    "createdAt": "2026-03-18T10:30:00",
    "updatedAt": "2026-03-18T10:30:00"
  },
  "timestamp": 1710756600000,
  "reason": null
}
```

**常见错误**:

| HTTP 状态码 | 业务码 | 说明 |
|------------|--------|------|
| 404 | 4602 | 实验不存在 |

---

### 5. 获取所有实验 👤

**接口描述**: 获取所有实验列表

**请求**:
``http
GET /experiments/all
```

**成功响应** (HTTP 200):
``json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "name": "牛顿第二定律验证实验",
      "questionIds": [1001, 1002, 1003],
      "questionIdsJson": "[1001,1002,1003]",
      "description": "通过实验验证牛顿第二定律 F=ma 的关系",
      "orderNumber": 1,
      "createdAt": "2026-03-18T10:30:00",
      "updatedAt": "2026-03-18T10:30:00"
    }
  ],
  "timestamp": 1710756600000,
  "reason": null
}
```

---

## 通用响应格式

### 成功响应格式

``json
{
  "code": 200,          // 业务状态码
  "message": "操作成功", // 响应消息
  "data": {             // 实际数据（任意类型）
    // ...
  },
  "timestamp": 1710000000000, // 响应时间戳
  "reason": null        // 错误原因（成功时为 null）
}
```

### 错误响应格式

``json
{
  "code": 4105,         // 业务状态码
  "message": "密码错误", // 错误描述
  "data": null,         // 错误时为 null
  "timestamp": 1710000000000, // 响应时间戳
  "reason": null        // 附加错误原因
}
```

### HTTP 状态码说明

| HTTP 状态码 | 使用场景 |
|------------|---------|
| 200 OK | GET/PUT/PATCH请求成功 |
| 201 Created | POST 创建资源成功 |
| 400 Bad Request | 请求参数错误 |
| 401 Unauthorized | 未授权（Token 无效/过期） |
| 403 Forbidden | 权限不足 |
| 404 Not Found | 资源不存在 |
| 409 Conflict | 资源冲突（重复） |
| 500 Internal Server Error | 服务器内部错误 |
| 503 Service Unavailable | 服务不可用 |

### 业务状态码分类

| 编码范围 | 分类 |
|---------|------|
| 2xx | 成功 |
| 4xxx | 客户端业务错误 |
| 5xxx | 服务器技术错误 |

详细的业务状态码请参考《API 状态码与错误处理文档.md》

---

## 附录

### A. LaTeX 数学公式规范

所有题目内容、选项、解析均使用 LaTeX 格式：

**行内公式**：使用 `$` 包裹
```
质量为$2\,\mathrm{kg}$的物体
```

**独立公式**：使用 `$$` 包裹
```
$$F = ma$$
```

**常用符号**：
- 单位：`\mathrm{m/s^2}`
- 希腊字母：`\alpha`, `\beta`, `\gamma`
- 分数：`\frac{a}{b}`
- 上下标：`x^2`, `x_1`

### B. SSE 流式响应前端处理示例

```
// 使用 EventSource 接收 SSE 流
const eventSource = new EventSource('/api/ai/student/realtimeQA', {
  headers: {
    'Authorization': 'Bearer ' + token
  }
});

eventSource.onmessage = function(event) {
  const data = JSON.parse(event.data);
  
  switch(data.type) {
    case 'start':
      console.log('开始分析:', data.content);
      break;
    case 'content':
      // 追加显示内容
      displayContent += data.content;
      updateUI(displayContent);
      break;
    case 'end':
      console.log('分析完成');
      eventSource.close();
      break;
  }
};

eventSource.onerror = function(error) {
  console.error('SSE 错误:', error);
  eventSource.close();
};
```

### C. JWT Token 刷新机制

```
// Axios 拦截器示例
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response.status === 401) {
      // Token 过期，尝试刷新
      return refreshToken()
        .then(newToken => {
          localStorage.setItem('token', newToken);
          // 重试原请求
          return axios.request(originalRequest);
        })
        .catch(() => {
          // 刷新失败，跳转登录
          window.location.href = '/login';
        });
    }
    return Promise.reject(error);
  }
);
```

---

## 更新日志

| 版本 | 更新日期 | 更新内容 |
|------|---------|----------|
| 1.0.0 | 2026-03-16 | 初始版本，包含所有核心 API 接口文档 |
| 1.0.1 | 2026-03-17 | 更新视频管理模块 API，添加 questionId 字段；移除题目管理模块 videoId 字段，明确单向关联设计 |
| 1.0.2 | 2026-03-18 | 新增实验管理模块 API 接口文档 |
| 1.0.3 | 2026-03-26 | 重构教师事件模块为全局事件设计，更新事件发布、查询和删除接口说明 |
| 1.0.4 | 2026-04-18 | 重构实验管理模块 API，移除 steps、maxScore、tags 等不存在的字段，更新为 name、questionIds(JSON)、orderNumber 等实际字段 |
| 1.0.5 | 2026-04-18 | 更新 SSE 事件格式说明，content 事件改为纯字符串格式；新增停止生成按钮功能说明和前端中断请求方式 |
| 1.0.6 | 2026-04-18 | 新增视频上传模块 API 接口文档，包括上传视频和删除视频文件两个接口 |
| 1.0.7 | 2026-04-18 | 重构错题分析模块 API 文档，更新路径为 /question-error-analysis，更新接口为统计、详情、流式分析三个接口 |

---

## 联系方式

如有疑问，请联系开发团队或在项目群内咨询。

**智学物理开发团队** © 2026
