package com.zhixuewuli.common.factory;

import com.zhixuewuli.entity.*;
import com.zhixuewuli.dto.internal.ConversationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 集成测试专用数据工厂
 * 
 * 专门用于集成测试场景，提供带数据库持久化的实体创建方法。
 * 与单元测试的 TestDataFactory 不同，本类的所有方法都会将实体保存到数据库。
 * 
 * <p>
 * 主要特点：
 * </p>
 * <ul>
 * <li>自动处理实体的持久化和 ID 生成</li>
 * <li>提供事务边界管理</li>
 * <li>支持批量创建和场景构建器模式</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Autowired
 * private IntegrationTestDataFactory factory;
 * 
 * @Test
 * void testWithTeachingScenario() {
 *     // 创建完整教学场景
 *     TeachingScenario scenario = factory.createTeachingScenario(101L, 5);
 *     
 *     // 或使用链式调用
 *     User student = factory.createUserAndSave()
 *         .withUserType(User.UserType.STUDENT)
 *         .withClassName("高三 (1) 班")
 *         .build();
 * }
 * }
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TestDataFactory
 */
@Component
@Transactional
public class IntegrationTestDataFactory {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 保存实体并返回带 ID 的实例
     * 
     * @param entity 待保存的实体
     * @param <T>    实体类型
     * @return 已保存的实体（包含生成的 ID）
     */
    public <T> T save(T entity) {
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }

    /**
     * 创建并保存用户
     * 
     * @return 已保存的用户实体
     */
    public User createUserAndSave() {
        return new UserBuilder().buildAndSave();
    }

    /**
     * 创建并保存题目（不关联视频）
     * 
     * @return 已保存的题目实体
     */
    public Question createQuestionAndSave() {
        Question question = TestDataFactory.createQuestion();
        return save(question);
    }

    /**
     * 创建并保存答题记录
     * 
     * @param userId     用户 ID
     * @param questionId 题目 ID
     * @return 已保存的答题记录实体
     */
    public AnswerRecord createAnswerRecordAndSave(Long userId, Long questionId) {
        AnswerRecord record = TestDataFactory.createAnswerRecord(userId, questionId);
        return save(record);
    }

    /**
     * 创建并保存 AI 对话历史
     * 
     * @param userId 用户 ID
     * @return 已保存的对话历史实体
     */
    public ConversationHistory createConversationHistoryAndSave(Long userId) {
        ConversationHistory history = TestDataFactory.createConversationHistory();
        history.setUserId(userId);
        return save(history);
    }

    /**
     * 创建并保存学生报告
     * 
     * @param userId 用户 ID
     * @return 已保存的学生报告实体
     */
    public StudentReport createStudentReportAndSave(Long userId) {
        StudentReport report = TestDataFactory.createStudentReport(userId);
        return save(report);
    }

    /**
     * 创建并保存教师事件
     * 
     * @param teacherId 教师 ID
     * @return 已保存的教师事件实体
     */
    public TeacherEvent createTeacherEventAndSave(Long teacherId) {
        TeacherEvent event = TestDataFactory.createTeacherEvent(teacherId);
        return save(event);
    }

    /**
     * 创建完整的教学场景
     * 
     * 包含：1 个视频 + 3 道题目 + 5 个学生 + 每个学生的答题记录
     * 
     * @param teacherId    教师 ID（用于创建教师事件）
     * @param studentCount 学生数量（最多 5 个）
     * @return 教学场景构建器
     */
    public TeachingScenarioBuilder createTeachingScenario(Long teacherId, int studentCount) {
        return new TeachingScenarioBuilder(teacherId, studentCount);
    }

    /**
     * 用户构建器（链式调用）
     */
    public class UserBuilder {
        private String username;
        private String realName;
        private User.UserType userType = User.UserType.STUDENT;
        private String className;

        public UserBuilder withUsername(String username) {
            this.username = username;
            return this;
        }

        public UserBuilder withRealName(String realName) {
            this.realName = realName;
            return this;
        }

        public UserBuilder withUserType(User.UserType userType) {
            this.userType = userType;
            return this;
        }

        public UserBuilder withClassName(String className) {
            this.className = className;
            return this;
        }

        public User buildAndSave() {
            User user = new User();
            user.setUsername(username != null ? username : "user_" + System.currentTimeMillis());
            user.setPasswordHash("$2a$10$example_hash");
            user.setRealName(realName != null ? realName : "测试用户");
            user.setUserType(userType);
            user.setClassName(className);
            user.setActive(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return save(user);
        }
    }

    /**
     * 教学场景构建器
     */
    public class TeachingScenarioBuilder {
        private final Long teacherId;
        private final int studentCount;
        private List<Question> questions = new ArrayList<>();
        private List<User> students = new ArrayList<>();
        private List<AnswerRecord> answerRecords = new ArrayList<>();

        public TeachingScenarioBuilder(Long teacherId, int studentCount) {
            this.teacherId = teacherId;
            this.studentCount = Math.min(studentCount, 5);
        }

        /**
         * 创建指定数量的题目（关联到视频）
         */
        public TeachingScenarioBuilder withQuestions(int count) {
            for (int i = 0; i < count; i++) {
                questions.add(createQuestionAndSave());
            }
            return this;
        }

        /**
         * 创建指定数量的学生
         */
        public TeachingScenarioBuilder withStudents() {
            for (int i = 0; i < studentCount; i++) {
                students.add(new UserBuilder()
                        .withUsername("student_" + i + "_" + System.currentTimeMillis())
                        .withRealName("学生" + i)
                        .withUserType(User.UserType.STUDENT)
                        .withClassName("高三 (" + (i + 1) + ") 班")
                        .buildAndSave());
            }
            return this;
        }

        /**
         * 为每个学生创建答题记录（每道题都答）
         */
        public TeachingScenarioBuilder withAnswerRecords() {
            if (students.isEmpty()) {
                withStudents();
            }
            if (questions.isEmpty()) {
                withQuestions(3);
            }

            for (User student : students) {
                for (Question question : questions) {
                    answerRecords.add(createAnswerRecordAndSave(student.getId(), question.getId()));
                }
            }
            return this;
        }

        /**
         * 构建并返回场景对象
         */
        public TeachingScenario build() {
            if (questions.isEmpty())
                withQuestions(3);
            if (students.isEmpty())
                withStudents();

            return new TeachingScenario(questions, students, answerRecords);
        }

        /**
         * 保存所有数据并返回场景
         */
        public TeachingScenario saveAndBuild() {
            return build();
        }
    }

    /**
     * 教学场景封装类
     */
    public static class TeachingScenario {
        private final List<Question> questions;
        private final List<User> students;
        private final List<AnswerRecord> answerRecords;

        public TeachingScenario(List<Question> questions,
                List<User> students, List<AnswerRecord> answerRecords) {
            this.questions = questions;
            this.students = students;
            this.answerRecords = answerRecords;
        }

        public List<Question> getQuestions() {
            return questions;
        }

        public List<User> getStudents() {
            return students;
        }

        public List<AnswerRecord> getAnswerRecords() {
            return answerRecords;
        }
    }
}
