# 集成测试 SQL 初始化脚本使用指南

## 概述

本目录包含用于集成测试的分层 SQL 初始化脚本，支持灵活的数据初始化策略。

## 脚本清单

### 1. test-schema.sql - 仅表结构初始化

**用途**：创建所有数据库表结构，不包含任何测试数据

**适用场景**：
- 需要完全自定义测试数据的集成测试
- 验证表结构和约束的测试
- 数据迁移测试

**使用方法**：
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.sql.init.schema-locations=classpath:sql/test-schema.sql"
})
public class MyIntegrationTest extends BaseIntegrationTest {
    // 测试代码
}
```

### 2. core-data.sql - 核心基础数据

**用途**：初始化最必需的测试数据（管理员和教师账户）

**包含数据**：
- 3 个教师用户（admin, teacher001, teacher002）
- 清空其他所有表

**适用场景**：
- 只需要教师权限的基础功能测试
- 用户管理和权限测试
- 最小数据集测试

**依赖**：自动执行清理和序列重置

**使用方法**：
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.sql.init.schema-locations=classpath:sql/test-schema.sql",
    "spring.sql.init.data-locations=classpath:sql/core-data.sql"
})
public class TeacherAuthIntegrationTest extends BaseIntegrationTest {
    // 测试代码
}
```

### 3. scenario-teaching.sql - 教学场景数据

**用途**：构建完整的教学场景测试数据

**包含数据**：
- 5 个教学视频（抛体运动相关）
- 5 道典型物理题目（含详细解析）
- 5 个学生用户
- 15 条答题记录（每个学生 3 条）

**适用场景**：
- 教学管理功能测试
- 答题分析功能测试
- 学生学习报告测试
- 需要完整业务场景的端到端测试

**依赖**：假设已执行 core-data.sql（会追加数据而非覆盖）

**使用方法**：
```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.sql.init.schema-locations=classpath:sql/test-schema.sql",
    "spring.sql.init.data-locations=classpath:sql/core-data.sql,classpath:sql/scenario-teaching.sql"
})
public class TeachingScenarioIntegrationTest extends BaseIntegrationTest {
    // 测试代码
}
```

### 4. cleanup-all.sql - 完全清理脚本

**用途**：清理所有测试数据并重置自增序列

**适用场景**：
- 测试间的数据隔离
- 手动清理测试环境
- 重置测试数据库状态

**使用方法**：
```java
@Autowired
private DataSource dataSource;

@AfterEach
void cleanup() throws SQLException {
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
        stmt.execute(new String(Files.readAllBytes(
            Paths.get("src/test/resources/sql/cleanup-all.sql")), 
            StandardCharsets.UTF_8));
    }
}
```

## 数据说明

### 核心用户数据（core-data.sql）

| ID | 用户名 | 角色 | 真实姓名 | 用途 |
|----|--------|------|----------|------|
| 100 | admin | TEACHER | 系统管理员 | 系统管理功能测试 |
| 101 | teacher001 | TEACHER | 李老师 | 教师功能测试 |
| 102 | teacher002 | TEACHER | 王老师 | 多用户场景测试 |

### 教学场景数据（scenario-teaching.sql）

#### 视频数据
- ID 1-5：抛体运动系列教学视频
- 知识点覆盖：抛体运动、斜面约束、分解方法
- 时长：280-340 秒

#### 题目数据
- ID 1-5：典型物理计算题
- 难度等级：2-4（中等到困难）
- 每题含完整选项、正确答案、详细解析

#### 学生用户
- ID 110-114：5 个学生用户
- 班级分布：高三 (1) 班、(2) 班、(3) 班

#### 答题记录
- 共 15 条记录，每个学生 3 条
- 正确率覆盖：0%-100%
- 包含典型错误原因记录

## 组合使用示例

### 示例 1：轻量级教师功能测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.sql.init.data-locations=classpath:sql/core-data.sql"
})
public class TeacherFunctionTest extends BaseIntegrationTest {
    
    @Autowired
    private TeacherService teacherService;
    
    @Test
    @DisplayName("教师可以查看学生列表")
    void teacherCanViewStudents() {
        // 只有教师用户，没有学生数据
        // 适合测试空状态或创建学生的场景
    }
}
```

### 示例 2：完整教学场景测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.sql.init.data-locations=classpath:sql/core-data.sql,classpath:sql/scenario-teaching.sql"
})
public class FullTeachingScenarioTest extends BaseIntegrationTest {
    
    @Test
    @DisplayName("生成学生学习报告")
    void generateStudentReport() {
        // 使用预设的学生和答题数据
        // 可以直接测试报告生成逻辑
    }
}
```

### 示例 3：自定义数据测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.sql.init.schema-locations=classpath:sql/test-schema.sql"
})
public class CustomDataTest extends BaseIntegrationTest {
    
    @Autowired
    private TestDataFactory dataFactory;
    
    @BeforeEach
    void setUp() {
        // 使用工厂类创建自定义测试数据
        dataFactory.createUserAndSave(...);
    }
}
```

## 注意事项

1. **执行顺序**：如果使用多个数据脚本，注意它们的执行顺序
   - 推荐：`test-schema.sql` → `core-data.sql` → `scenario-teaching.sql`

2. **数据隔离**：每个测试方法运行后，Spring 会自动回滚事务
   - 如需显式清理，可在 `@AfterEach` 中执行 `cleanup-all.sql`

3. **H2 兼容性**：所有脚本已在 H2 数据库（MySQL 模式）下测试通过
   - 使用 `SET MODE MySQL;` 确保语法兼容
   - 避免使用 MySQL 特有函数

4. **序列管理**：每个脚本都会重置序列，确保 ID 可预测
   - 教师用户从 ID 100 开始
   - 学生用户从 ID 110 开始（在 scenario-teaching.sql 中）

## 扩展指南

### 添加新的场景脚本

1. 创建文件：`src/test/resources/sql/scenario-xxx.sql`
2. 遵循命名规范：`scenario-{场景名}.sql`
3. 在文件开头注释说明用途和包含的数据
4. 假设基础数据（core-data）已存在，只追加新数据
5. 在文档中更新说明

### 模板示例

```sql
-- src/test/resources/sql/scenario-{场景名}.sql
-- {场景描述}
-- 用于{适用测试类型}

-- 追加数据（不要清理现有数据）

-- 1. {数据类型 1}
INSERT INTO ...

-- 2. {数据类型 2}
INSERT INTO ...

-- 显示统计信息
SELECT '{场景名}初始化完成' as message;
SELECT ...
```

## 故障排查

### 问题：外键约束失败

**解决**：确保按正确顺序加载脚本
```properties
# 错误：直接加载场景数据
spring.sql.init.data-locations=classpath:sql/scenario-teaching.sql

# 正确：先加载核心数据
spring.sql.init.data-locations=classpath:sql/core-data.sql,classpath:sql/scenario-teaching.sql
```

### 问题：ID 冲突

**解决**：检查是否正确重置序列
```sql
-- 在脚本中包含序列重置
ALTER TABLE users ALTER COLUMN id RESTART WITH 100;
```

### 问题：数据重复

**解决**：在脚本开头添加清理语句（但不要清理序列）
```sql
DELETE FROM answer_records;
-- 注意：不要删除 users 表，因为其他表可能依赖它
```

## 相关文件

- `application-test.yml` - 测试环境配置
- `BaseIntegrationTest.java` - 集成测试基类
- `IntegrationTestDataFactory.java` - 集成测试数据工厂（任务 2）
