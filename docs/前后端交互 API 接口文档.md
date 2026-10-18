# 智学物理前后端交互 API 接口文档

> 本文档详细描述了智学物理项目前后端交互的 API 接口规范，包括请求路径、请求格式、响应结构等内容，帮助前端开发人员快速理解和对接后端接口。

## 目录

- [基础规范](#基础规范)
- [认证模块](#认证模块)
- [用户管理模块](#用户管理模块)
- [题目管理模块](#题目管理模块)
- [答题记录模块](#答题记录模块)
- [AI 助手模块](#ai 助手模块)
- [教师事件模块](#教师事件模块)
- [错题分析模块](#错题分析模块)
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

**接口描述**：通过唯一用户名与密码进行身份认证，获取访问令牌。

**登录标识说明**：
- 登录只接受用户名，真实姓名不能作为登录凭证。
- 用户名唯一性只由用户名字段和数据库唯一约束保障。


**请求**：
```http
POST /auth/login
Content-Type: application/json
```


**请求体**：
```json
{
  "username": "student001", // 只能是用户名
  "password": "password123"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 验证规则 | 说明 |
|------|------|------|----------|------|
| username | string | 是 | `^[a-zA-Z0-9_]{3,50}$` | 唯一用户名，3-50 位 |
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
| username | string | 是 | `^[a-zA-Z0-9_]{3,50}$` | 唯一用户名；不与其他用户的真实姓名做跨字段校验 |
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
| 409 | 4107 | 用户名重复（含并发注册时的数据库唯一约束冲突） |
| 409 | 4108 | 真实姓名重复（保留现有姓名唯一约束，与登录无关） |
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
  "username": "new_username", // 新用户名（只校验用户名唯一）
  "password": "new_password",
  "realName": "新名字",
  "className": "新班级"
}
```

**请求字段说明**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 否 | 新用户名；如有变更只校验用户名唯一 |
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
| imageBase64 | string | 否 | 题目图片 Base64 编码 |

**重要说明**：

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

**接口描述**：根据 ID 获取题目信息。教师始终获得完整管理字段；学生若尚未完成活动任务中该题的首次作答，响应不包含 `correctOption` 和 `explanation`。任务外自由练习仍可读取这些字段。

**请求**：
```http
GET /questions/teacher/get/{id}
Authorization: Bearer <token>
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

**接口描述**：批量获取数据库中所有题目。教师获得完整字段；学生请求中属于当前活动任务且尚未首次作答的题目按上一条规则隐藏受保护字段。分页历史列表不会返回完整解析或图片正文。

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

## 教学任务模块

单班结构下允许教师并行发布多个作业。任务状态：

| 状态 | 说明 |
|------|------|
| `DRAFT` | 草稿，仅教师可见，可编辑题目和时间 |
| `PUBLISHED` | 已发布，开始时间后、截止时间前允许学生作答 |
| `CLOSED` | 已截止，学生不能再提交，保留历史数据 |
| `ARCHIVED` | 已归档，进入历史任务列表 |

常见接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/teacher/events` | 创建任务；`status` 默认 `PUBLISHED`，可传 `DRAFT` |
| GET | `/teacher/events/all` | 教师未归档任务列表 |
| GET | `/teacher/events/available` | 学生可见的已发布任务列表，含未开始/已截止状态 |
| PUT | `/teacher/events/{id}/draft` | 编辑草稿 |
| POST | `/teacher/events/{id}/publish` | 发布草稿，至少需要一道题 |
| POST | `/teacher/events/{id}/close` | 截止任务 |
| DELETE | `/teacher/events/{id}` | 归档任务 |
| GET | `/teacher/events/{id}/questions` | 获取任务题目；未开始的任务不对学生开放 |
| GET | `/teacher/events/{id}/stats` | 作业详情统计：参与/完成/未交、学生汇总、每题正确率 |

任务创建/编辑请求新增 `questionIds`、`status`、`startAt`、`dueAt`。时间使用无时区 ISO-8601 字符串，服务端按 UTC 存储。

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

### 1.1 分页历史与详情（当前前端使用）👨‍🎓

**接口描述**：数据库分页返回轻量历史行，不携带图片 Base64、完整解析和选项；单页上限 100。排序为 `answeredAt DESC, attemptOrder DESC`。总数来自独立 count 查询，不代表当前页条数。

```http
GET /answer-records/student/history?page=1&size=20&wrongOnly=false&keyword=
GET /answer-records/student/practice-review/page?page=1&size=20&keyword=
GET /answer-records/user/{userId}/history?page=1&size=20&wrongOnly=false
GET /answer-records/{recordId}
Authorization: Bearer <token>
```

- `student/history`：当前学生全部尝试；`wrongOnly=true` 只返回错误尝试。
- `practice-review/page`：当前学生每道题的最后一次尝试，且该题存在过错误；`keyword` 在服务端按题目快照/题库内容筛选，结果仍作用于全量。
- `user/{userId}/history`：教师可查任意学生，学生只能查自己。
- `{recordId}`：按权限加载完整历史快照（含历史图片与解析）。旧格式完整 JSON 快照和新的 `image_ref` 图片引用都兼容。

**轻量行字段**：`recordId,eventId,practice,attemptOrder,userId,questionId,sessionId,userAnswer,correct,questionPreview,answeredAt`。分页响应为 `{total,pages,currentPage,pageSize,records}`。

**学生统计**：`GET /answer-records/student/summary` 返回全量 `{totalAttempts,correctAttempts,wrongQuestions}`，页面不能用当前页自行推算全量正确率。

### 1.3 答案开放规则

- 教师读取完整题目管理信息。
- 学生在活动任务中完成该题首次作答前，任务快照接口和题库接口都不返回正确答案和解析。
- 首次提交成功后，仅该学生解锁自己的答案和解析。
- 活动任务中尚未完成首次作答时，带 `eventId` 的补练和省略 `eventId` 的自由练习都不能绕过；已归档任务保留历史补练能力。
- 这是接口字段保护，不宣称提供完整考试防作弊。

### 1.4 任务进度口径

`GET /answer-records/teacher/summary?eventId={eventId}` 返回任务聚合。进度指标统一以“当前启用学生”为范围：`userType=STUDENT AND active=true`。

- `eligibleStudents`：当前启用学生数，作为完成率分母；为 0 时完成率为 0，不发生除零。
- `completedStudents`：当前启用学生中，首次作答（`practice=false`）覆盖任务全部题目的学生数。
- `participatingStudents`：当前启用学生中有首次作答记录的学生数。
- `assignmentAnswers`、`assignmentCorrect`、`practiceAttempts`、`masteredQuestions`：保留完整历史口径，与进度人数范围不同。
- 补练和重复提交不会增加任务完成题数或完成人数。
- 停用学生保留历史明细和学情列表可见性，但不进入分母或分子。
- 本轮没有实现“发布时参与名单快照”；归档任务的历史进度字段同样按查询时的当前启用学生范围计算。

### 2. 获取我的答题记录 👨‍🎓（旧全量接口）

**接口描述**：查询当前登录用户的答题历史记录。该兼容入口最多返回 100 条；超过 100 条时返回 400，提示改用分页接口，不会静默截断。新前端使用下方分页接口。

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

**接口描述**：查询当前登录用户的错误答题记录（旧全量兼容入口，超过 100 条明确拒绝）。

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

**接口描述**：教师查询指定学生的最近一次学习会话中的所有答题记录。最近会话按该学生最后一条作答的 `answeredAt` 选取；时间相同用唯一的 `attemptOrder` 稳定排序，不再按 sessionId 字符串倒序。无作答历史返回正常空结果。

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
