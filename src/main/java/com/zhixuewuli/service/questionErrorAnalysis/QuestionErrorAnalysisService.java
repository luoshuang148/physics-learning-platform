package com.zhixuewuli.service.questionErrorAnalysis;

import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.dto.response.QuestionErrorDetailsResponse;
import com.zhixuewuli.dto.response.QuestionErrorSummaryResponse;
import com.zhixuewuli.dto.response.QuestionStudentErrorDetailResponse;

import java.util.List;
import java.util.Map;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 错题分析服务接口
 * 
 * 提供教师端错题分析的核心业务逻辑接口，支持两个维度的信息查询：
 * 1. 题目维度：统计每道题的错误情况（错误人次、错误率）
 * 2. 学生维度：展示指定题目答错的学生详细信息
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>题目错误统计查询</li>
 * <li>单题学生错误详情查询</li>
 * <li>权限验证和数据安全</li>
 * <li>异常处理和日志记录</li>
 * </ul>
 * 
 * <p>
 * 技术特点：
 * </p>
 * <ul>
 * <li>采用分步加载机制优化性能</li>
 * <li>使用数据库单次遍历查询提升效率</li>
 * <li>集成转换工具类简化代码</li>
 * <li>完善的异常处理体系</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionErrorSummaryResponse 题目错误统计响应DTO
 * @see QuestionStudentErrorDetailResponse 学生错误详情响应DTO
 */
public interface QuestionErrorAnalysisService {

    /**
     * 获取所有题目的错误统计信息（优化版本）
     * 
     * @param teacherId 教师ID
     * @return 题目错误统计列表（仅包含有错误的题目）
     * @throws com.zhixuewuli.common.exception.BusinessException  当业务验证失败时抛出
     * @throws com.zhixuewuli.common.exception.TechnicalException 当技术异常发生时抛出
     * 
     * @implSpec 只返回有错误记录的题目统计信息
     * @implSpec 使用优化的数据库查询减少数据传输
     * @implSpec 集成权限验证确保数据安全
     */
    List<QuestionErrorSummaryResponse> getAllQuestionErrorStatistics(Long teacherId);

    /**
     * 教师对特定题目进行学生作答情况分析（流式）
     *
     * @param request   AI 请求，包含问题内容和题目 ID
     * @param emitter   SSE 发射器
     * @param teacherId 教师 ID
     * @param sessionId 会话 ID（用于上下文管理）
     */
    void streamTeacherQuestionAnalysis(AIAskRequest request, SseEmitter emitter, Long teacherId, String sessionId);

    /**
     * 获取题目错误详情（学生列表）- 单次查询优化
     * 
     * @param questionId 题目 ID
     * @param teacherId  教师 ID
     * @return 题目错误详情响应
     */
    QuestionErrorDetailsResponse getQuestionErrorDetails(Long questionId, Long teacherId);
}