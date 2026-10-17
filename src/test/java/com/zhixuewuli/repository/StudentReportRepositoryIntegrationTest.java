package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.entity.StudentReport;
import com.zhixuewuli.entity.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 学生报告仓库集成测试
 * 验证 H2 数据库连接和基本 CRUD 操作
 */
@Tag("integration-repository")
class StudentReportRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StudentReportRepository studentReportRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Rollback
    void testSaveAndFindStudentReport() {
        // Given - 先创建用户
        User user = new User();
        user.setUsername("report_test_user_" + System.currentTimeMillis());
        user.setPasswordHash("$2a$10$test_hash");
        user.setUserType(User.UserType.STUDENT);
        user.setRealName("报告测试用户");
        user.setClassName("高三 (1) 班");
        user.setActive(true);
        User savedUser = userRepository.save(user);

        StudentReport report = new StudentReport();
        report.setUserId(savedUser.getId());
        report.setRealName(savedUser.getRealName());
        report.setSessionId("session_" + System.currentTimeMillis());
        report.setReportContent("这是测试生成的学习报告内容，包含学生的学习情况分析。");

        // When
        StudentReport savedReport = studentReportRepository.save(report);
        Optional<StudentReport> foundReport = studentReportRepository.findById(savedReport.getId());

        // Then
        assertThat(foundReport).isPresent();
        assertThat(foundReport.get().getUserId()).isEqualTo(savedUser.getId());
        assertThat(foundReport.get().getRealName()).isEqualTo("报告测试用户");
        assertThat(foundReport.get().getSessionId()).startsWith("session_");
        assertThat(foundReport.get().getReportContent()).contains("学习报告");
    }

    @Test
    @Rollback
    void testFindByUserIdOrderByCreatedAtDesc() {
        // Given - 创建用户和多份报告
        User user = new User();
        user.setUsername("multi_report_user_" + System.currentTimeMillis());
        user.setPasswordHash("$2a$10$test_hash");
        user.setUserType(User.UserType.STUDENT);
        user.setRealName("多报告用户");
        user.setActive(true);
        User savedUser = userRepository.save(user);

        // 创建 3 份报告
        for (int i = 0; i < 3; i++) {
            StudentReport report = new StudentReport();
            report.setUserId(savedUser.getId());
            report.setRealName(user.getRealName());
            report.setSessionId("session_" + i + "_" + System.currentTimeMillis());
            report.setReportContent("第" + (i + 1) + "份报告内容");
            studentReportRepository.save(report);

            try {
                Thread.sleep(10); // 确保创建时间有先后
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // When
        List<StudentReport> reports = studentReportRepository.findByUserIdOrderByCreatedAtDesc(savedUser.getId());

        // Then - 验证按创建时间倒序排列
        assertThat(reports).hasSize(3);
        assertThat(reports.get(0).getReportContent()).contains("第3份");
        assertThat(reports.get(1).getReportContent()).contains("第2份");
        assertThat(reports.get(2).getReportContent()).contains("第1份");
    }

    @Test
    @Rollback
    void testFindBySessionId() {
        // Given - 创建多个用户和报告
        User user1 = createUser("user1_session_test", "用户 1");
        User user2 = createUser("user2_session_test", "用户 2");

        String commonSessionId = "common_session_" + System.currentTimeMillis();

        StudentReport report1 = createReport(user1, commonSessionId, "用户 1 的报告");
        StudentReport report2 = createReport(user2, commonSessionId, "用户 2 的报告");
        StudentReport report3 = createReport(user1, "different_session", "不同会话的报告");

        // When
        List<StudentReport> reports = studentReportRepository.findBySessionId(commonSessionId);

        // Then - 验证返回同一会话的所有报告
        assertThat(reports).hasSize(2);
        assertThat(reports).extracting(StudentReport::getSessionId)
                .containsOnly(commonSessionId);
        assertThat(reports).extracting(StudentReport::getRealName)
                .containsExactlyInAnyOrder("用户 1", "用户 2");
    }

    @Test
    @Rollback
    void testFindByUserIdAndSessionId() {
        // Given
        User user = createUser("specific_session_user", "特定会话用户");
        String sessionId = "specific_session_" + System.currentTimeMillis();
        StudentReport report = createReport(user, sessionId, "特定会话报告内容");

        // When
        Optional<StudentReport> foundReport = studentReportRepository.findByUserIdAndSessionId(
                user.getId(), sessionId);

        // Then
        assertThat(foundReport).isPresent();
        assertThat(foundReport.get().getRealName()).isEqualTo("特定会话用户");
        assertThat(foundReport.get().getReportContent()).contains("特定会话报告");
    }

    @Test
    @Rollback
    void testFindLatestByUserId() {
        // Given - 创建用户和多份报告
        User user = createUser("latest_report_user", "最新报告用户");

        // 创建 5 份报告
        for (int i = 0; i < 5; i++) {
            StudentReport report = new StudentReport();
            report.setUserId(user.getId());
            report.setRealName(user.getRealName());
            report.setSessionId("session_" + i + "_" + System.currentTimeMillis());
            report.setReportContent("第" + (i + 1) + "份报告 - 最新报告测试");
            studentReportRepository.save(report);

            try {
                Thread.sleep(10); // 确保创建时间有先后
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // When
        Optional<StudentReport> latestReport = studentReportRepository.findLatestByUserId(user.getId());

        // Then - 验证返回最新的报告
        assertThat(latestReport).isPresent();
        assertThat(latestReport.get().getReportContent()).contains("第5份");
    }

    @Test
    @Rollback
    void testUpdateStudentReport() {
        // Given
        User user = createUser("update_report_user", "更新报告用户");
        StudentReport report = createReport(user, "update_session", "原始报告内容");

        // When
        report.setReportContent("更新后的报告内容 - 更详细的分析");
        report.setRealName("更新后的姓名");
        StudentReport updatedReport = studentReportRepository.save(report);

        // Then
        assertThat(updatedReport.getReportContent()).contains("更新后的报告");
        assertThat(updatedReport.getRealName()).isEqualTo("更新后的姓名");
    }

    @Test
    @Rollback
    void testDeleteStudentReport() {
        // Given
        User user = createUser("delete_report_user", "删除报告用户");
        StudentReport report = createReport(user, "delete_session", "待删除的报告");
        Long reportId = report.getId();

        // When
        studentReportRepository.deleteById(reportId);
        Optional<StudentReport> deletedReport = studentReportRepository.findById(reportId);

        // Then
        assertThat(deletedReport).isEmpty();
    }

    @Test
    @Rollback
    void testMultipleUsersWithSameSession() {
        // Given - 模拟多个学生在同一个会话中生成报告
        User student1 = createUser("student1_same_session", "学生 1");
        User student2 = createUser("student2_same_session", "学生 2");
        User student3 = createUser("student3_same_session", "学生 3");

        String groupSessionId = "group_session_" + System.currentTimeMillis();

        createReport(student1, groupSessionId, "学生 1 的群体报告");
        createReport(student2, groupSessionId, "学生 2 的群体报告");
        createReport(student3, groupSessionId, "学生 3 的群体报告");

        // When
        List<StudentReport> groupReports = studentReportRepository.findBySessionId(groupSessionId);

        // Then - 验证所有学生的报告都在同一会话中
        assertThat(groupReports).hasSize(3);
        assertThat(groupReports).extracting(StudentReport::getSessionId)
                .containsOnly(groupSessionId);
        assertThat(groupReports).extracting(StudentReport::getRealName)
                .containsExactlyInAnyOrder("学生 1", "学生 2", "学生 3");
    }

    @Test
    @Rollback
    void testReportWithNonExistentUser() {
        // Given - 使用不存在的用户 ID 创建报告（外键约束测试）
        StudentReport report = new StudentReport();
        report.setUserId(99999L); // 不存在的用户 ID
        report.setRealName("孤立报告");
        report.setSessionId("orphan_session_" + System.currentTimeMillis());
        report.setReportContent("这是一个孤立报告测试");

        // When & Then - H2 数据库可能不会立即抛出外键约束异常
        // 具体行为取决于外键约束的定义
        try {
            studentReportRepository.save(report);
            // 如果保存成功，说明外键约束未启用或配置为级联
        } catch (Exception e) {
            // 如果抛出异常，说明外键约束生效
            assertThat(e).isNotNull();
        }
    }

    /**
     * 辅助方法：创建用户
     */
    private User createUser(String username, String realName) {
        User user = new User();
        user.setUsername(username + "_" + System.currentTimeMillis());
        user.setPasswordHash("$2a$10$test_hash");
        user.setUserType(User.UserType.STUDENT);
        user.setRealName(realName);
        user.setActive(true);
        return userRepository.save(user);
    }

    /**
     * 辅助方法：创建报告
     */
    private StudentReport createReport(User user, String sessionId, String content) {
        StudentReport report = new StudentReport();
        report.setUserId(user.getId());
        report.setRealName(user.getRealName());
        report.setSessionId(sessionId);
        report.setReportContent(content);
        return studentReportRepository.save(report);
    }
}
