package com.zhixuewuli.service.integration;

import com.zhixuewuli.common.base.WireMockTestBase;
import com.zhixuewuli.common.factory.IntegrationTestDataFactory;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.entity.Video;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.repository.VideoRepository;
import com.zhixuewuli.repository.QuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * 集成测试基础设施验证测试
 * 
 * 演示如何使用阶段一新增的所有测试基础设施：
 * 1. BaseIntegrationTest 增强工具方法
 * 2. IntegrationTestDataFactory 数据工厂
 * 3. WireMock 外部服务模拟
 * 4. SQL 初始化脚本
 */
@SpringBootTest
@DisplayName("集成测试基础设施验证")
class IntegrationInfrastructureTest extends WireMockTestBase {

    @Autowired
    private IntegrationTestDataFactory factory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    @DisplayName("验证 BaseIntegrationTest 工具方法")
    void testBaseIntegrationTestUtils() {
        // 验证测试环境
        verifyTestEnvironment();

        // 清理数据
        cleanAllTables();

        // 创建并保存用户
        User user = new User();
        user.setUsername("test_user");
        user.setPasswordHash("hash");
        user.setRealName("测试用户");
        user.setUserType(User.UserType.STUDENT);
        saveAndFlush(user);

        // 验证记录存在
        assertThat(existsInTable("users", "username='test_user'")).isTrue();

        // 验证记录数
        assertRecordCount("users", "user_type='STUDENT'", 1);

        // 执行原生 SQL
        int updated = executeSql("UPDATE users SET active = false WHERE username = 'test_user'");
        assertThat(updated).isEqualTo(1);

        // 查询数据
        Long count = queryForObject("SELECT COUNT(*) FROM users", Long.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("验证 IntegrationTestDataFactory 功能")
    void testIntegrationTestDataFactory() {
        // 清理数据
        cleanAllTables();

        // 简单创建
        User student = factory.createUserAndSave();
        assertThat(student.getId()).isNotNull();

        // 使用构建器自定义
        IntegrationTestDataFactory.UserBuilder builder = factory.new UserBuilder();
        User teacher = builder
                .withUsername("teacher_test")
                .withRealName("张老师")
                .withUserType(User.UserType.TEACHER)
                .buildAndSave();

        assertThat(teacher.getUserType()).isEqualTo(User.UserType.TEACHER);

        // 创建教学场景
        var scenario = factory.createTeachingScenario(teacher.getId(), 3)
                .withVideo()
                .withQuestions(2)
                .withStudents()
                .withAnswerRecords()
                .build();

        // 验证场景数据
        assertThat(scenario.getVideo()).isNotNull();
        assertThat(scenario.getQuestions()).hasSize(2);
        assertThat(scenario.getStudents()).hasSize(3);
        assertThat(scenario.getAnswerRecords()).hasSize(6); // 3 学生 × 2 题目

        // 验证数据库中有数据
        assertThat(userRepository.count()).isEqualTo(5); // 1 教师 + 3 学生 + 1 初始
        assertThat(videoRepository.count()).isEqualTo(1);
        assertThat(questionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("验证 WireMock AI 服务模拟")
    void testWireMockAISimulation() {
        // 设置 WireMock stubbing
        stubFor(post("/api/v1/services/aigc/text-generation/generation")
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("aliyun-ai-chat-response.json")));

        // 验证 WireMock 服务器已启动
        assertThat(wireMockServer.isRunning()).isTrue();

        // 可以通过 HTTP 客户端调用验证（这里简化）
        // 实际使用中会调用真实的 AI 服务客户端
    }

    @Test
    @DisplayName("验证 SQL 脚本加载")
    void testSqlDataInitialization() {
        // 这个测试演示如何验证 SQL 脚本正确加载
        // 实际使用时通过@SpringBootTest 的 properties 配置自动加载

        // 验证 core-data.sql 的数据（如果已加载）
        List<User> allUsers = userRepository.findAll();
        List<User> teachers = allUsers.stream()
                .filter(u -> u.getUserType() == User.UserType.TEACHER)
                .collect(java.util.stream.Collectors.toList());

        // 如果没有预加载数据，数量为 0
        // 如果加载了 core-data.sql，应该有 3 个教师
        System.out.println("教师数量：" + teachers.size());
    }
}
