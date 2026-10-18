package com.zhixuewuli.common.factory;

import com.zhixuewuli.entity.User;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.ConversationHistory;
import com.zhixuewuli.entity.StudentReport;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.dto.internal.ConversationType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 测试数据工厂基类
 * 提供常用的测试实体创建方法
 */
public class TestDataFactory {

    /**
     * 创建测试用户
     */
    public static User createUser() {
        User user = new User();
        user.setUsername("testuser_" + System.currentTimeMillis());
        user.setPasswordHash("$2a$10$example_hash"); // BCrypt hash示例
        user.setUserType(User.UserType.STUDENT);
        user.setRealName("测试用户");
        user.setClassName("高三(1)班");
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    /**
     * 创建测试题目
     */
    public static Question createQuestion() {
        Question question = new Question();
        question.setContent("一个物体从高度h自由落下，求落地时间");
        question.setContentHash(UUID.randomUUID().toString().replace("-", "") + "0123456789abcdef0123456789abcdef");
        question.setOptions("{\"A\":\"√(2h/g)\",\"B\":\"2h/g\",\"C\":\"h/g\",\"D\":\"√(h/g)\"}");
        question.setCorrectOption("A");
        question.setExplanation("根据自由落体运动公式 h = 1/2gt²，可得 t = √(2h/g)");
        question.setDifficulty(2);
        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());
        return question;
    }

    /**
     * 创建测试答题记录
     */
    public static AnswerRecord createAnswerRecord(Long userId, Long questionId) {
        AnswerRecord record = new AnswerRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setSessionId("test-session-" + System.currentTimeMillis());
        record.setUserAnswer("A");
        record.setCorrect(true);
        record.setAnsweredAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }

    /**
     * 创建测试对话历史记录
     */
    public static ConversationHistory createConversationHistory() {
        ConversationHistory history = new ConversationHistory();
        history.setUserId(100L);
        history.setSessionId("test-session-" + System.currentTimeMillis());
        history.setQuestion("测试问题内容");
        history.setAnswer("测试回答内容");
        history.setSequenceNumber(1);
        history.setIsActive(true);
        history.setTokenCount(50);
        history.setConversationType(ConversationType.REAL_TIME_QA);
        history.setQuestionId(null);
        history.setCreatedAt(LocalDateTime.now());
        history.setUpdatedAt(LocalDateTime.now());
        return history;
    }

    /**
     * 创建测试学生报告
     */
    public static StudentReport createStudentReport(Long userId) {
        StudentReport report = new StudentReport();
        report.setUserId(userId);
        report.setRealName("测试学生");
        report.setSessionId("test-session-" + System.currentTimeMillis());
        report.setReportContent("这是测试生成的学习报告内容");
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }

    /**
     * 创建测试教师事件
     */
    public static TeacherEvent createTeacherEvent(Long teacherId) {
        TeacherEvent event = new TeacherEvent();
        event.setTeacherId(teacherId);
        event.setTeacherName("测试教师");
        event.setEventTitle("测试事件标题");
        event.setEventDescription("测试事件描述内容");
        event.setEventTimestamp(LocalDateTime.now().minusDays(1));
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        return event;
    }
}