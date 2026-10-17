package com.zhixuewuli.common.base;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;

/**
 * 基础集成测试类
 * 
 * 提供 Spring 集成测试的基础配置和常用工具方法，支持 H2 数据库、事务管理和数据持久化。
 * 所有集成测试应继承此类以获得统一的测试环境和便捷的工具方法。
 * 
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>Spring Boot 测试上下文支持</li>
 * <li>H2 数据库自动配置（MySQL 兼容模式）</li>
 * <li>事务自动回滚（保证测试隔离性）</li>
 * <li>实体管理器访问</li>
 * <li>数据库清理工具方法</li>
 * <li>SQL 执行工具</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     public class UserServiceIntegrationTest extends BaseIntegrationTest {
 * 
 *         &#64;Autowired
 *         private UserRepository userRepository;
 * 
 *         @Test
 *         void testSaveUser() {
 *             // 使用工具方法清理数据
 *             cleanTable("users");
 * 
 *             // 创建并保存用户
 *             User user = new User();
 *             user.setUsername("test");
 *             userRepository.save(user);
 * 
 *             // 验证结果
 *             assertThat(user.getId()).isNotNull();
 *         }
 *     }
 * }
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@Transactional
@Tag("integration")
public abstract class BaseIntegrationTest {

    @Autowired(required = false)
    protected TestEntityManager entityManager;

    @PersistenceContext
    protected EntityManager jpaEntityManager;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 清理指定表的所有数据
     * 
     * 用于测试间的数据隔离，确保每个测试从干净的状态开始。
     * 该方法会禁用外键检查，清空表后重新启用。
     * 
     * @param tableName 表名（区分大小写，通常为大写）
     */
    protected void cleanTable(String tableName) {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE " + tableName);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    /**
     * 清理所有测试表的数据
     * 
     * 按依赖关系倒序清理，避免外键约束冲突。
     * 适用于需要完全重置数据库状态的测试场景。
     */
    protected void cleanAllTables() {
        List<String> tables = List.of(
                "student_reports",
                "teacher_events",
                "conversation_history",
                "answer_records",
                "questions",
                "videos",
                "users");

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        tables.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + table));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    /**
     * 保存实体并立即刷新到数据库
     * 
     * 封装了 persist 和 flush 操作，确保实体立即写入数据库。
     * 适用于需要立即可见的测试场景。
     * 
     * @param entity 待保存的实体
     * @param <T>    实体类型
     * @return 已保存的实体（包含生成的 ID）
     */
    protected <T> T saveAndFlush(T entity) {
        jpaEntityManager.persist(entity);
        jpaEntityManager.flush();
        return entity;
    }

    /**
     * 执行原生 SQL 语句
     * 
     * 支持 INSERT、UPDATE、DELETE 等 DML 操作，返回受影响的行数。
     * 注意：不能用于查询操作（SELECT）。
     * 
     * @param sql SQL 语句
     * @return 受影响的行数
     */
    protected int executeSql(String sql) {
        return jdbcTemplate.update(sql);
    }

    /**
     * 执行 SQL 查询并返回单个结果
     * 
     * @param sql          SQL 查询语句
     * @param requiredType 期望的返回类型
     * @param <T>          返回类型
     * @return 查询结果
     */
    protected <T> T queryForObject(String sql, Class<T> requiredType) {
        return jdbcTemplate.queryForObject(sql, requiredType);
    }

    /**
     * 验证数据库中存在满足条件的记录
     * 
     * @param tableName     表名
     * @param conditions    WHERE 子句条件（不含 WHERE 关键字）
     * @param expectedCount 期望的记录数量
     * @throws AssertionError 当实际记录数与期望不符时抛出
     */
    protected void assertRecordCount(String tableName, String conditions, int expectedCount) {
        String sql = "SELECT COUNT(*) FROM " + tableName +
                (conditions != null && !conditions.isEmpty() ? " WHERE " + conditions : "");
        Long count = jdbcTemplate.queryForObject(sql, Long.class);

        if (count == null || count != expectedCount) {
            throw new AssertionError(String.format(
                    "Expected %d records in %s, but found %d. Conditions: %s",
                    expectedCount, tableName, count, conditions));
        }
    }

    /**
     * 验证数据库中是否存在满足条件的记录
     * 
     * @param tableName  表名
     * @param conditions WHERE 子句条件（不含 WHERE 关键字）
     * @return true 如果存在至少一条记录
     */
    protected boolean existsInTable(String tableName, String conditions) {
        String sql = "SELECT COUNT(*) FROM " + tableName +
                (conditions != null && !conditions.isEmpty() ? " WHERE " + conditions : "");
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null && count > 0;
    }

    /**
     * 重置表的自增序列
     * 
     * 用于确保每次测试的 ID 生成可预测。
     * 
     * @param tableName  表名
     * @param startValue 起始值
     */
    protected void resetSequence(String tableName, long startValue) {
        String sql = String.format(
                "ALTER TABLE %s ALTER COLUMN id RESTART WITH %d",
                tableName, startValue);
        jdbcTemplate.execute(sql);
    }

    /**
     * 验证测试环境是否正确配置
     * 
     * 检查是否在 test profile 下运行，H2 数据库是否可用。
     * 在测试开始前调用此方法可快速发现配置问题。
     * 
     * @throws IllegalStateException 当环境配置不正确时抛出
     */
    protected void verifyTestEnvironment() {
        // 验证 Profile
        String activeProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfiles != null && !activeProfiles.contains("test")) {
            throw new IllegalStateException("Not running with 'test' profile. Current profiles: " + activeProfiles);
        }

        // 验证数据库连接
        try {
            String dbUrl = jdbcTemplate.queryForObject(
                    "SELECT DATABASE()",
                    String.class);
            System.out.println("✓ Connected to database: " + dbUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect to database", e);
        }
    }
}