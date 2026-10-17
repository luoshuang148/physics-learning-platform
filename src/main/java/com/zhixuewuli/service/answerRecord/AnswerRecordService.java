package com.zhixuewuli.service.answerRecord;

import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.RecordBatchDeleteRequest;
import com.zhixuewuli.dto.response.AnswerRecordResponse;
import com.zhixuewuli.dto.response.AnswerRecordBatchResponse;
import com.zhixuewuli.dto.response.AnswerStatsResponse;

/**
 * 答题记录服务接口
 * 
 * 定义答题记录管理的核心业务功能契约，包括学生基础功能、
 * 权限控制查询和教师专属功能三大模块。
 * 
 * <p>
 * 功能分类：
 * </p>
 * <ul>
 * <li><strong>学生基础功能</strong>: 提交答题、查询个人记录等</li>
 * <li><strong>权限控制查询</strong>: 按权限查询用户记录</li>
 * <li><strong>教师专属功能</strong>: 数据管理、统计分析等</li>
 * </ul>
 * 
 * <p>
 * 安全设计：
 * </p>
 * <ul>
 * <li>所有方法都包含用户身份验证</li>
 * <li>敏感操作需要特定角色权限</li>
 * <li>数据访问遵循最小权限原则</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public interface AnswerRecordService {
    com.zhixuewuli.common.result.PageResult<AnswerRecordResponse> getTeacherAnswerPage(
            Long currentUserId, int page, int size, boolean wrongOnly, String studentName);


    // === 学生基础功能 ===

    /**
     * 提交答题记录
     * 
     * 学生用户提交答题结果，系统自动判断正误并保存记录。
     * 
     * @param request       答题提交请求
     * @param currentUserId 当前用户ID
     * @param sessionId     会话ID
     * @return 答题记录响应（包含答题时间和结果信息）
     */
    AnswerRecordResponse submitAnswerRecord(AnswerRecordSubmitRequest request, Long currentUserId, String sessionId);

    /**
     * 根据ID获取答题记录
     * 
     * 查询指定ID的答题记录详情。
     * 
     * @param recordId      记录ID
     * @param currentUserId 当前用户ID
     * @return 答题记录响应
     */
    AnswerRecordResponse getAnswerRecordById(String recordId, Long currentUserId);

    /**
     * 获取当前用户的错题记录
     * 
     * 查询当前登录用户的所有错误答题记录。
     * 
     * @param currentUserId 当前用户ID
     * @return 错题记录批量响应
     */
    AnswerRecordBatchResponse getCurrentUserWrongAnswers(Long currentUserId);

    /**
     * 获取当前用户的所有答题记录
     * 
     * 查询当前登录用户的所有答题历史记录。
     * 
     * @param currentUserId 当前用户ID
     * @return 答题记录批量响应
     */
    AnswerRecordBatchResponse getCurrentUserAnswerRecords(Long currentUserId);

    // === 权限控制查询 ===

    /**
     * 获取指定用户的答题记录
     * 
     * 根据权限查询指定用户的答题记录。
     * 学生只能查询自己的记录，教师可以查询任意用户记录。
     * 
     * @param targetUserId  目标用户ID
     * @param currentUserId 当前用户ID
     * @return 答题记录批量响应
     */
    AnswerRecordBatchResponse getUserAnswerRecords(Long targetUserId, Long currentUserId);

    /**
     * 获取指定用户的错题记录
     * 
     * 根据权限查询指定用户的错题记录。
     * 学生只能查询自己的错题，教师可以查询任意用户错题。
     * 
     * @param targetUserId  目标用户ID
     * @param currentUserId 当前用户ID
     * @return 错题记录批量响应
     */
    AnswerRecordBatchResponse getWrongAnswersByUser(Long targetUserId, Long currentUserId);

    // === 教师专属功能 ===

    /**
     * 删除答题记录
     * 
     * 教师删除指定的答题记录。
     * 需要教师权限验证。
     * 
     * @param recordId      要删除的记录ID
     * @param currentUserId 当前用户ID（必须为教师）
     */
    void deleteAnswerRecord(String recordId, Long currentUserId);

    /**
     * 批量删除指定时间范围的答题记录
     * 
     * 教师批量删除指定时间范围内的答题记录。
     * 需要教师权限验证和事务保护。
     * 
     * @param request       批量删除请求（包含时间范围）
     * @param currentUserId 当前用户ID（必须为教师）
     */
    void batchDeleteByTimeRange(RecordBatchDeleteRequest request, Long currentUserId);

    /**
     * 统计系统总答题记录数
     * 
     * 教师查询整个系统的答题记录总数。
     * 需要教师权限验证。
     * 
     * @param currentUserId 当前用户ID（必须为教师）
     * @return 系统总答题记录数
     */
    Long countTotalAnswers(Long currentUserId);

    /**
     * 获取所有用户的答题记录
     * 
     * 教师查询系统内所有用户的答题记录。
     * 需要教师权限验证。
     * 
     * @param currentUserId 当前用户ID（必须为教师）
     * @return 答题记录批量响应
     */
    AnswerRecordBatchResponse getAllUsersAnswerRecords(Long currentUserId);

    /**
     * 获取所有用户的错题记录
     * 
     * 教师查询系统内所有用户的错题记录。
     * 需要教师权限验证。
     * 
     * @param currentUserId 当前用户ID（必须为教师）
     * @return 错题记录批量响应
     */
    AnswerRecordBatchResponse getAllUsersWrongAnswers(Long currentUserId);

    // === 会话维度查询功能 ===

    /**
     * 获取指定会话的答题记录
     * 
     * 根据会话ID和用户ID获取该会话内的所有答题记录。
     * 返回完整的答题信息，包括题目详情。
     * 
     * @param sessionId     会话ID
     * @param currentUserId 当前用户ID
     * @return 答题记录批量响应
     */
    AnswerRecordBatchResponse getSessionAnswerRecords(String sessionId, Long currentUserId);

    /**
     * 获取学生最近一次会话的答题记录
     * 
     * 教师查询指定学生的最近一次学习会话中的所有答题记录。
     * 自动查找学生最近的会话ID并返回相关记录。
     * 
     * @param studentId 学生用户ID
     * @param teacherId 教师用户ID（用于权限验证）
     * @return 答题记录批量响应
     */
    AnswerRecordBatchResponse getStudentLatestSessionRecords(Long studentId, Long teacherId);

    /**
     * 根据学生姓名获取最近一次会话的答题记录
     * 
     * 教师通过学生真实姓名查询其最近一次学习会话中的所有答题记录。
     * 系统会先根据姓名精确查找学生，然后获取其最近会话记录。
     * 
     * @param studentName 学生真实姓名（精确匹配）
     * @param teacherId   教师用户ID（用于权限验证）
     * @return 答题记录批量响应
     * @throws com.zhixuewuli.common.exception.BusinessException 当学生不存在时抛出404异常
     */
    AnswerRecordBatchResponse getStudentLatestSessionRecordsByName(String studentName, Long teacherId);

    /**
     * 获取答题统计数据
     * 
     * 教师查询答题统计数据，包括有答题记录的学生人数和总学生人数。
     * 用于教师端展示答题覆盖率等统计信息。
     * 
     * @param currentUserId 当前用户ID（必须为教师）
     * @return 答题统计响应（包含有答题记录人数和总人数）
     */
    AnswerStatsResponse getAnswerStats(Long currentUserId);
}