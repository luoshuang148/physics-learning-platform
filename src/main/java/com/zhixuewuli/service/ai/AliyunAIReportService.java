package com.zhixuewuli.service.ai;

import com.zhixuewuli.dto.response.StudentReportResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 阿里AI报告服务接口
 * 
 * 专门处理基于阿里AI的学生个人报告和教师综合分析报告生成功能，
 * 提供流式的报告生成服务。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public interface AliyunAIReportService {

    /**
     * 验证学生报告请求参数
     * 
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @throws IllegalArgumentException        当参数不合法时抛出
     * @throws InsufficientPermissionException 当权限不足时抛出
     */
    void validateStudentReportRequest(String sessionId, Long userId);

    /**
     * 验证教师分析请求参数
     * 
     * @param userId 用户 ID
     * @throws IllegalArgumentException        当参数不合法时抛出
     * @throws InsufficientPermissionException 当权限不足时抛出
     */
    void validateTeacherAnalysisRequest(Long userId);

    /**
     * 学生个人报告流式生成
     * 
     * @param sessionId 会话 ID，标识学生本次学习的会话
     * @param emitter   SSE 发射器
     * @param userId    用户 ID
     */
    void streamStudentReport(String sessionId, SseEmitter emitter, Long userId);

    /**
     * 教师综合分析报告流式生成
     * 
     * @param emitter SSE发射器
     * @param userId  教师用户ID
     */
    void streamTeacherAnalysis(SseEmitter emitter, Long userId);

    void streamTeacherAnalysis(SseEmitter emitter, Long userId, Long eventId);

    /**
     * 创建配置好的SseEmitter实例
     */
    SseEmitter createConfiguredEmitter();

    /**
     * 获取学生最新报告
     * 
     * 教师通过身份验证后，获取指定学生的最新学习报告。
     * 
     * @param teacherId 教师ID（用于身份验证）
     * @param studentId 学生ID
     * @return StudentLatestReportResponse 学生最新报告信息
     * @throws com.zhixuewuli.common.exception.auth.InsufficientPermissionException       当教师权限不足时抛出
     * @throws com.zhixuewuli.common.exception.auth.UserNotFoundException                 当用户不存在时抛出
     * @throws com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException 当学生无报告记录时抛出
     */
    StudentReportResponse getStudentLatestReport(Long teacherId, Long studentId);
}