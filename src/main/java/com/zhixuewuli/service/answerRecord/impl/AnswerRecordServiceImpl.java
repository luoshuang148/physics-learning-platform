package com.zhixuewuli.service.answerRecord.impl;

import com.zhixuewuli.common.exception.*;
import com.zhixuewuli.common.exception.answerrecord.*;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.util.*;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.RecordBatchDeleteRequest;
import com.zhixuewuli.dto.response.AnswerRecordResponse;
import com.zhixuewuli.dto.response.AnswerRecordBatchResponse;
import com.zhixuewuli.dto.response.AnswerStatsResponse;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 答题记录服务实现类
 * 
 * 实现答题记录管理的核心业务逻辑，包括权限验证、事务管理和数据一致性保障。
 * 严格按照权限控制原则，确保数据安全和操作合规。
 * 
 * <p>
 * 核心特性：
 * </p>
 * <ul>
 * <li><strong>权限控制</strong>: 严格的用户权限验证机制</li>
 * <li><strong>事务安全</strong>: 完整的事务回滚保障</li>
 * <li><strong>数据一致性</strong>: 异常情况下的数据状态保证</li>
 * <li><strong>操作审计</strong>: 详细的操作日志记录</li>
 * </ul>
 * 
 * <p>
 * 事务管理：
 * </p>
 * <ul>
 * <li>所有写操作使用@Transactional注解</li>
 * <li>配置rollbackFor包含BusinessException和TechnicalException</li>
 * <li>读操作根据需要设置合适的隔离级别</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AnswerRecordService 服务接口定义
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnswerRecordServiceImpl implements AnswerRecordService {

    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final com.zhixuewuli.repository.TeacherEventRepository teacherEventRepository;
    private final UserPermissionUtil userPermissionUtil;
    private final QuestionResponseConverter questionResponseConverter;

    @Override
    @Transactional(readOnly = true)
    public com.zhixuewuli.common.result.PageResult<AnswerRecordResponse> getTeacherAnswerPage(
            Long currentUserId, int page, int size, boolean wrongOnly, String studentName) {
        userPermissionUtil.checkTeacherPermission(currentUserId);
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("页码须大于零，每页条数须在1到100之间");
        }
        String name = studentName == null ? "" : studentName.trim();
        if (name.length() > 100) throw new IllegalArgumentException("姓名筛选不得超过100个字符");
        name = "%" + name.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = answerRecordRepository.findTeacherPage(wrongOnly, name,
                org.springframework.data.domain.PageRequest.of(page - 1, size));
        var rows = result.getContent().stream().map(row -> AnswerRecordResponse.builder()
                .recordId(row.recordId()).userId(row.userId()).questionId(row.questionId())
                .sessionId(row.sessionId()).userAnswer(row.userAnswer()).correct(row.correct())
                .answeredAt(row.answeredAt()).errorReason(row.errorReason()).studentName(row.studentName())
                .questionContent(row.questionContent()).correctAnswer(row.correctAnswer())
                .questionOptions(questionResponseConverter.parseOptionsFromJson(row.options()))
                .questionAnalysis(row.analysis()).difficulty(questionResponseConverter.getDifficultyString(row.difficulty()))
                .build()).toList();
        return com.zhixuewuli.common.result.PageResult.<AnswerRecordResponse>builder().records(rows)
                .total(result.getTotalElements()).pages(result.getTotalPages()).currentPage(page).pageSize(size).build();
    }

    // === 学生基础功能 ===

    @Override
    @Transactional(rollbackFor = { BusinessException.class, TechnicalException.class })
    public AnswerRecordResponse submitAnswerRecord(AnswerRecordSubmitRequest request, Long currentUserId,
            String sessionId) {
        log.info("处理答题记录提交 - UserId: {}, QuestionId: {}", currentUserId, request.getQuestionId());

        Long eventId = teacherEventRepository.findByIsGlobalTrue().map(com.zhixuewuli.entity.TeacherEvent::getId).orElse(null);
        if (request.getEventId() != null && !request.getEventId().equals(eventId)) {
            throw new BusinessException("教学任务已切换，请刷新后再提交");
        }

        try {
            // 1. 验证题目存在性
            Question question = questionRepository.findById(request.getQuestionId())
                    .orElseThrow(() -> new QuestionNotFoundException(request.getQuestionId()));

            // 2. 判断答案正确性
            boolean isCorrect = request.getUserAnswer().equals(question.getCorrectOption());
            log.debug("答案判断 - QuestionId: {}, UserAnswer: {}, Correct: {}",
                    request.getQuestionId(), request.getUserAnswer(), isCorrect);

            // 3. 构建答题记录实体
            AnswerRecord record = new AnswerRecord();
            record.setId(UUID.randomUUID().toString());
            record.setSessionId(sessionId);
            record.setEventId(eventId);
            record.setUserId(currentUserId);
            record.setQuestionId(request.getQuestionId());
            record.setUserAnswer(request.getUserAnswer());
            record.setCorrect(isCorrect);
            record.setErrorReason(request.getErrorReason());
            record.setAnsweredAt(request.getAnsweredAt() != null ? request.getAnsweredAt() : LocalDateTime.now());

            // 4. 保存记录
            AnswerRecord savedRecord = answerRecordRepository.save(record);
            log.info("答题记录保存成功 - RecordId: {}, UserId: {}", savedRecord.getId(), currentUserId);

            // 5. 使用转换器构建完整响应
            return questionResponseConverter.convertToFullResponseDTO(savedRecord, question);

        } catch (Exception e) {
            log.error("答题记录提交失败 - UserId: {}", currentUserId, e);
            throw new AnswerRecordProcessingException("提交", "答题记录提交失败：" + e.getMessage());
            // 事务会自动回滚
        }
    }

    @Override
    public AnswerRecordResponse getAnswerRecordById(String recordId, Long currentUserId) {
        log.debug("查询答题记录详情 - RecordId: {}, UserId: {}", recordId, currentUserId);

        // 验证记录存在性和访问权限
        AnswerRecord record = answerRecordRepository.findById(recordId)
                .orElseThrow(() -> new AnswerRecordNotFoundException(recordId));

        // 检查访问权限（只能访问自己的记录，或教师可以访问所有记录）
        if (!record.getUserId().equals(currentUserId)) {
            if (!userPermissionUtil.isTeacherUser(currentUserId)) {
                throw new InsufficientPermissionException(currentUserId, "学生只能访问自己的答题记录");
            }
        }

        // 获取关联的题目信息
        Question question = questionRepository.findById(record.getQuestionId())
                .orElseThrow(() -> new QuestionNotFoundException(record.getQuestionId()));

        return questionResponseConverter.convertToFullResponseDTO(record, question);
    }

    @Override
    public AnswerRecordBatchResponse getCurrentUserWrongAnswers(Long currentUserId) {
        log.debug("查询当前用户错题记录 - UserId: {}", currentUserId);

        List<AnswerRecord> wrongAnswers = answerRecordRepository.findWrongAnswersByUserId(currentUserId);

        return convertToBatchResponse(wrongAnswers);
    }

    @Override
    public AnswerRecordBatchResponse getCurrentUserAnswerRecords(Long currentUserId) {
        log.debug("查询当前用户答题记录 - UserId: {}", currentUserId);

        List<AnswerRecord> records = answerRecordRepository.findByUserIdOrderByAnsweredAtDesc(currentUserId);

        return convertToBatchResponse(records);
    }

    // === 权限控制查询 ===

    @Override
    public AnswerRecordBatchResponse getUserAnswerRecords(Long targetUserId, Long currentUserId) {
        log.debug("查询用户答题记录 - TargetUserId: {}, CurrentUserId: {}", targetUserId, currentUserId);

        // 1. 验证用户存在性
        if (!userRepository.existsById(targetUserId)) {
            throw new UserNotFoundException(String.format("用户不存在：%d", targetUserId));
        }

        // 2. 权限检查
        if (!userPermissionUtil.canQueryUserData(targetUserId, currentUserId)) {
            throw new InsufficientPermissionException(currentUserId, "学生只能访问自己的错题记录");
        }

        List<AnswerRecord> records = answerRecordRepository.findByUserIdOrderByAnsweredAtDesc(targetUserId);

        return convertToBatchResponse(records);
    }

    @Override
    public AnswerRecordBatchResponse getWrongAnswersByUser(Long targetUserId, Long currentUserId) {
        log.debug("查询用户错题记录 - TargetUserId: {}, CurrentUserId: {}", targetUserId, currentUserId);

        // 1. 验证用户存在性
        if (!userRepository.existsById(targetUserId)) {
            throw new UserNotFoundException(String.format("用户不存在：%d", targetUserId));
        }

        // 2. 权限检查
        if (!userPermissionUtil.canQueryUserData(targetUserId, currentUserId)) {
            throw new InsufficientPermissionException(currentUserId, "学生只能访问自己的答题记录");
        }

        List<AnswerRecord> wrongAnswers = answerRecordRepository.findWrongAnswersByUserId(targetUserId);

        return convertToBatchResponse(wrongAnswers);
    }

    // === 教师专属功能 ===

    @Override
    @Transactional(rollbackFor = { BusinessException.class, TechnicalException.class })
    public void deleteAnswerRecord(String recordId, Long currentUserId) {
        log.info("删除答题记录 - RecordId: {}, Operator: {}", recordId, currentUserId);

        // 1. 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);

        // 2. 验证记录存在性
        AnswerRecord record = answerRecordRepository.findById(recordId)
                .orElseThrow(() -> new AnswerRecordNotFoundException(recordId));

        // 3. 执行删除
        try {
            answerRecordRepository.deleteById(recordId);
            log.info("答题记录删除成功 - RecordId: {}", recordId);

            // 记录删除操作日志
            log.info("答题记录删除审计 - RecordId: {}, Operator: {}, TargetUserId: {}, Action: DELETE",
                    recordId, currentUserId, record.getUserId());

        } catch (Exception e) {
            log.error("删除记录失败 - RecordId: {}", recordId, e);
            throw new TechnicalException("删除记录失败：" + e.getMessage());
            // 事务回滚
        }
    }

    @Override
    @Transactional(rollbackFor = { BusinessException.class, TechnicalException.class })
    public void batchDeleteByTimeRange(RecordBatchDeleteRequest request, Long currentUserId) {
        log.info("开始批量删除操作 - StartTime: {}, EndTime: {}, Operator: {}",
                request.getStartTime(), request.getEndTime(), currentUserId);

        // 1. 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);

        // 2. 参数验证
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new BusinessException("批量删除的开始时间不能晚于结束时间");
        }

        // 3. 执行批量删除（事务保护）
        try {
            int deletedCount = answerRecordRepository.deleteByTimeRange(
                    request.getStartTime(), request.getEndTime());

            log.info("批量删除完成 - 删除记录数: {}, Operator: {}", deletedCount, currentUserId);

            // 记录批量删除操作日志
            log.info("批量删除审计 - Operator: {}, TimeRange: [{}-{}], DeletedCount: {}, Action: BATCH_DELETE",
                    currentUserId, request.getStartTime(), request.getEndTime(), deletedCount);

        } catch (Exception e) {
            log.error("批量删除操作失败 - Operator: {}", currentUserId, e);
            throw new TechnicalException("批量删除操作失败：" + e.getMessage());
            // 事务会自动回滚
        }
    }

    @Override
    public Long countTotalAnswers(Long currentUserId) {
        log.debug("统计总答题记录数 - Operator: {}", currentUserId);

        // 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);

        return answerRecordRepository.count();
    }

    @Override
    public AnswerRecordBatchResponse getAllUsersAnswerRecords(Long currentUserId) {
        log.info("查询所有用户答题记录 - Operator: {}", currentUserId);

        // 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);

        List<AnswerRecord> records = answerRecordRepository.findAll();

        return convertToBatchResponse(records);
    }

    @Override
    public AnswerRecordBatchResponse getAllUsersWrongAnswers(Long currentUserId) {
        log.info("查询所有用户错题记录 - Operator: {}", currentUserId);

        // 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);

        List<AnswerRecord> wrongAnswers = answerRecordRepository.findByCorrectFalseOrderByAnsweredAtDesc();

        return convertToBatchResponse(wrongAnswers);
    }

    // === 会话维度查询功能实现 ===

    @Override
    public AnswerRecordBatchResponse getSessionAnswerRecords(String sessionId, Long currentUserId) {
        log.info("查询会话答题记录 - SessionId: {}, UserId: {}", sessionId, currentUserId);

        try {
            // 1. 验证用户是否有权访问此会话
            validateSessionAccess(sessionId, currentUserId);

            // 2. 根据用户类型查询不同的数据
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new UserNotFoundException(String.format("用户不存在：%d", currentUserId)));

            List<AnswerRecord> records;
            if (user.getUserType() == User.UserType.TEACHER) {
                // 教师可以查看会话中的所有记录
                records = answerRecordRepository.findBySessionIdOrderByAnsweredAtDesc(sessionId);
            } else {
                // 学生只能查看自己的记录
                records = answerRecordRepository.findBySessionIdAndUserId(sessionId, currentUserId);
            }

            if (records.isEmpty()) {
                return AnswerRecordBatchResponse.builder()
                        .records(List.of())
                        .totalCount(0L)
                        .build();
            }

            log.info("会话答题记录查询成功 - 找到{}条记录", records.size());

            return convertToBatchResponse(records);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("会话答题记录查询失败 - SessionId: {}, UserId: {}", sessionId, currentUserId, e);
            throw new TechnicalException("查询会话答题记录失败：" + e.getMessage());
        }
    }

    @Override
    public AnswerRecordBatchResponse getStudentLatestSessionRecords(Long studentId, Long teacherId) {
        log.info("查询学生最近会话记录 - StudentId: {}, TeacherId: {}", studentId, teacherId);

        try {
            // 权限验证：只有教师可以查询学生记录
            userPermissionUtil.checkTeacherPermission(teacherId);

            // 验证学生是否存在
            userRepository.findById(studentId)
                    .orElseThrow(() -> new UserNotFoundException("学生不存在"));

            // 获取学生最近的会话ID
            String latestSessionId = getLatestSessionIdForUser(studentId);

            if (latestSessionId == null) {
                return AnswerRecordBatchResponse.builder()
                        .records(List.of())
                        .totalCount(0L)
                        .build();
            }

            // 查询该会话的所有答题记录
            List<AnswerRecord> records = answerRecordRepository.findBySessionIdAndUserId(latestSessionId, studentId);

            log.info("学生最近会话记录查询成功 - 找到{}条记录", records.size());

            return convertToBatchResponse(records);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("学生最近会话记录查询失败 - StudentId: {}, TeacherId: {}", studentId, teacherId, e);
            throw new TechnicalException("查询学生最近会话记录失败：" + e.getMessage());
        }
    }

    /**
     * 验证用户对会话的访问权限
     * 
     * 采用分层验证机制，明确区分权限问题和资源不存在问题：
     * 1. 首先进行业务权限验证（身份权限）
     * 2. 然后进行数据存在性验证（会话是否存在）
     * 
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @throws SessionAccessException   当用户无权访问会话时抛出
     * @throws SessionNotFoundException 当会话不存在时抛出
     */
    private void validateSessionAccess(String sessionId, Long userId) {
        // 1. 获取用户信息，判断用户类型
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(String.format("用户不存在：%d", userId)));

        // 2. 教师用户可以访问任意会话
        if (user.getUserType() == User.UserType.TEACHER) {
            log.debug("教师用户可访问任意会话 - SessionId: {}, UserId: {}", sessionId, userId);
            return;
        }

        // 3. 学生用户只能访问自己的会话（通过检查该会话中是否有该用户的记录）
        List<AnswerRecord> sessionRecords = answerRecordRepository.findBySessionIdAndUserId(sessionId, userId);

        if (sessionRecords.isEmpty()) {
            // 学生无权访问该会话（要么会话不存在，要么不属于该学生）
            throw new InsufficientPermissionException("权限不足：无法访问该会话");
        }
    }

    /**
     * 获取用户最近的会话ID
     */
    private String getLatestSessionIdForUser(Long userId) {
        List<String> sessionIds = answerRecordRepository.findDistinctSessionIdsByUserId(userId);
        return sessionIds.isEmpty() ? null : sessionIds.get(0);
    }

    /**
     * 将答题记录实体转换为包含完整信息的响应DTO
     */
    private AnswerRecordResponse convertToFullResponseDTO(AnswerRecord record) {
        // 获取关联的题目信息
        Question question = record.getQuestion();
        if (question == null) {
            question = questionRepository.findById(record.getQuestionId())
                    .orElseThrow(() -> new QuestionNotFoundException(record.getQuestionId()));
        }
        return questionResponseConverter.convertToFullResponseDTO(record, question);
    }

    /**
     * 将答题记录列表转换为批量响应DTO
     * 
     * 统一封装答题记录列表到响应DTO的转换逻辑，
     * 避免重复代码，提高代码复用性和维护性。
     * 
     * @param records 答题记录实体列表
     * @return 包含转换后记录列表和总数的批量响应DTO
     */
    private AnswerRecordBatchResponse convertToBatchResponse(List<AnswerRecord> records) {
        List<AnswerRecordResponse> responseRecords = records.stream()
                .map(this::convertToFullResponseDTO)
                .toList();

        return AnswerRecordBatchResponse.builder()
                .records(responseRecords)
                .totalCount((long) responseRecords.size())
                .build();
    }

    @Override
    public AnswerRecordBatchResponse getStudentLatestSessionRecordsByName(String studentName, Long teacherId) {
        log.info("查询学生最近会话记录 - StudentName: {}, TeacherId: {}", studentName, teacherId);

        try {
            // 权限验证：只有教师可以查询学生记录
            userPermissionUtil.checkTeacherPermission(teacherId);

            // 根据姓名查找学生（精确匹配）
            User student = userRepository.findByRealName(studentName)
                    .orElseThrow(() -> new UserNotFoundException("学生不存在"));

            // 直接复用核心查询逻辑，避免重复的学生存在性验证
            Long studentId = student.getId();

            // 获取学生最近的会话ID
            String latestSessionId = getLatestSessionIdForUser(studentId);

            if (latestSessionId == null) {
                return AnswerRecordBatchResponse.builder()
                        .records(List.of())
                        .totalCount(0L)
                        .build();
            }

            // 查询该会话的所有答题记录
            List<AnswerRecord> records = answerRecordRepository.findBySessionIdAndUserId(latestSessionId, studentId);

            log.info("学生最近会话记录查询成功 - 找到{}条记录", records.size());

            return convertToBatchResponse(records);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("学生最近会话记录查询失败 - StudentName: {}, TeacherId: {}", studentName, teacherId, e);
            throw new TechnicalException("查询学生最近会话记录失败：" + e.getMessage());
        }
    }

    @Override
    public AnswerStatsResponse getAnswerStats(Long currentUserId) {
        log.info("获取答题统计数据 - Operator: {}", currentUserId);

        // 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);

        // 获取有答题记录的学生人数
        long answeredStudentCount = answerRecordRepository.countDistinctUserIds();

        // 获取学生总人数
        long totalStudentCount = userRepository.countByUserType(User.UserType.STUDENT);

        // 计算覆盖率
        double coverageRate = totalStudentCount > 0
                ? Math.round((double) answeredStudentCount / totalStudentCount * 1000) / 10.0
                : 0.0;

        AnswerStatsResponse response = new AnswerStatsResponse();
        response.setAnsweredStudentCount(answeredStudentCount);
        response.setTotalStudentCount(totalStudentCount);
        response.setCoverageRate(coverageRate);

        log.info("答题统计数据查询成功 - 有答题记录人数: {}, 学生总人数: {}, 覆盖率: {}%",
                answeredStudentCount, totalStudentCount, coverageRate);

        return response;
    }
}
