package com.zhixuewuli.service.answerRecord.impl;

import com.zhixuewuli.dto.response.StudentAnswerStats;
import org.springframework.data.domain.PageRequest;
import com.zhixuewuli.common.result.PageResult;
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
import com.zhixuewuli.dto.response.AnswerRecordSummaryResponse;
import com.zhixuewuli.dto.response.StudentAnswerSummary;
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
import java.util.Optional;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.service.teacherEvent.TaskQuestionService;
import com.zhixuewuli.common.exception.teacherevent.EventAlreadyExistsException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnswerRecordServiceImpl implements AnswerRecordService {

    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final TeacherEventRepository teacherEventRepository;
    private final UserPermissionUtil userPermissionUtil;
    private final QuestionResponseConverter questionResponseConverter;
    private final TaskQuestionService taskQuestions;
    private final com.zhixuewuli.service.question.AnswerAccessPolicy answerAccess;
    private final com.zhixuewuli.service.question.QuestionImageStore questionImages;
    private final ObjectMapper snapshotJson;

    @Override
    @Transactional(readOnly = true)
    public List<StudentAnswerStats> getStudentAnswerStats(Long currentUserId) {
        userPermissionUtil.checkTeacherPermission(currentUserId);
        return answerRecordRepository.summarizeStudents();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AnswerRecordResponse> getTeacherAnswerPage(
            Long currentUserId, int page, int size, boolean wrongOnly, String studentName) {
        return getTeacherAnswerPage(currentUserId,page,size,wrongOnly,studentName,null);
    }
    @Override
    @Transactional(readOnly = true)
    public PageResult<AnswerRecordResponse> getTeacherAnswerPage(
            Long currentUserId,int page,int size,boolean wrongOnly,String studentName,Long eventId) {
        userPermissionUtil.checkTeacherPermission(currentUserId);
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("页码须大于零，每页条数须在1到100之间");
        }
        String name = studentName == null ? "" : studentName.trim();
        if (name.length() > 100) throw new IllegalArgumentException("姓名筛选不得超过100个字符");
        name = "%" + name.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = answerRecordRepository.findTeacherPage(wrongOnly, name,eventId,
                PageRequest.of(page - 1, size));
        var rows = result.getContent().stream().map(row -> AnswerRecordResponse.builder()
                .recordId(row.recordId()).userId(row.userId()).questionId(row.questionId())
                .sessionId(row.sessionId()).userAnswer(row.userAnswer()).correct(row.correct())
                .answeredAt(row.answeredAt()).timestampUtc(row.timestampUtc()).studentName(row.studentName())
                .questionContent(row.questionContent()).correctAnswer(row.correctAnswer())
                .questionOptions(null)
                // Full options and explanation are loaded from the record detail endpoint only.
                .questionAnalysis(null).difficulty(questionResponseConverter.getDifficultyString(row.difficulty()))
                .build()).toList();
        return PageResult.<AnswerRecordResponse>builder().records(rows)
                .total(result.getTotalElements()).pages(result.getTotalPages()).currentPage(page).pageSize(size).build();
    }

    // === 学生基础功能 ===

    @Override
    @Transactional(rollbackFor = { BusinessException.class, TechnicalException.class })
    public AnswerRecordResponse submitAnswerRecord(AnswerRecordSubmitRequest request, Long currentUserId,
            String sessionId) {
        log.info("处理答题记录提交 - UserId: {}, QuestionId: {}", currentUserId, request.getQuestionId());

        Long eventId=request.getEventId();
        if("ASSIGNMENT".equals(request.getIntent()) && eventId==null) throw new IllegalArgumentException("任务作答必须指定eventId");
        boolean practice=eventId==null || "PRACTICE".equals(request.getIntent());
        if(eventId!=null || request.getRequestId()!=null) {
            if(request.getRequestId()==null || request.getRequestId().isBlank()) throw new IllegalArgumentException("任务提交必须提供requestId");
            userRepository.lockById(currentUserId).orElseThrow(()->new IllegalArgumentException("用户不存在"));
            var previous=answerRecordRepository.findByUserIdAndRequestId(currentUserId,request.getRequestId());
            if(previous.isPresent()) {
                var old=previous.get();
                if(!java.util.Objects.equals(old.getEventId(),eventId)||!old.getQuestionId().equals(request.getQuestionId())
                    ||!old.getUserAnswer().equals(request.getUserAnswer())||old.isPractice()!=practice)
                    throw new EventAlreadyExistsException("requestId已用于不同的提交内容");
                return convertToFullResponseDTO(old);
            }
        }
        if(practice && answerAccess.blocksPracticeBeforeAssignment(currentUserId,eventId,request.getQuestionId()))
            throw new EventAlreadyExistsException("该题属于当前活动任务，请先完成首次作答后再补练");
        if(eventId!=null) {
            if(!practice && answerRecordRepository.existsByUserIdAndEventIdAndQuestionIdAndPracticeFalse(currentUserId,eventId,request.getQuestionId()))
                throw new EventAlreadyExistsException("本题已完成，请使用补练入口");
            // Shared lock lets different students submit first attempts concurrently; archive/replace takes an exclusive lock.
            var event = practice
                    ? teacherEventRepository.findById(eventId).orElseThrow(()->new IllegalArgumentException("教学任务不存在"))
                    : teacherEventRepository.lockSharedById(eventId).orElseThrow(()->new IllegalArgumentException("教学任务不存在"));
            if(event.getStatus()==com.zhixuewuli.entity.TeacherEvent.Status.DRAFT)
                throw new EventAlreadyExistsException("教学任务尚未发布");
            if(!practice && event.getStatus()!=com.zhixuewuli.entity.TeacherEvent.Status.PUBLISHED)
                throw new EventAlreadyExistsException("教学任务已截止或归档，请刷新或选择补练");
            if(!practice && !event.isOpenAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))) {
                if(event.isScheduledAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)))
                    throw new EventAlreadyExistsException("教学任务尚未开始");
                throw new EventAlreadyExistsException("教学任务已截止");
            }
        }
        // Unscoped legacy submissions are free practice and never attach themselves to the current assignment.
        Question taskQuestion=eventId==null ? null : taskQuestions.get(eventId,request.getQuestionId());
        if(taskQuestion!=null) {
            try {if(!snapshotJson.readTree(taskQuestion.getOptions()).has(request.getUserAnswer())) throw new IllegalArgumentException("答案选项无效");}
            catch(JsonProcessingException e){throw new IllegalStateException("任务选项损坏",e);}
        }
        try {
            // 1. 验证题目存在性
            Question question = taskQuestion!=null ? taskQuestion : questionRepository.findById(request.getQuestionId())
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
            record.setRequestId(request.getRequestId());record.setPractice(practice || eventId==null);
            record.setImageRef(questionImages.store(question.getImageBase64()));
            record.setQuestionSnapshot(snapshotJsonWithoutImage(question));
            record.setContentSnapshot(question.getContent());record.setOptionsSnapshot(question.getOptions());
            record.setCorrectSnapshot(question.getCorrectOption());record.setAnalysisSnapshot(question.getExplanation());record.setDifficultySnapshot(question.getDifficulty());
            record.setUserId(currentUserId);
            record.setQuestionId(request.getQuestionId());
            record.setUserAnswer(request.getUserAnswer());
            record.setCorrect(isCorrect);
            record.setAnsweredAt(LocalDateTime.now(java.time.ZoneOffset.UTC));

            // 4. 保存记录
            AnswerRecord savedRecord = eventId==null ? answerRecordRepository.save(record) : answerRecordRepository.saveAndFlush(record);
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

        return convertToFullResponseDTO(record);
    }

    @Override
    @Transactional(readOnly = true)
    public AnswerRecordBatchResponse getCurrentUserWrongAnswers(Long currentUserId) {
        log.debug("查询当前用户错题记录 - UserId: {}", currentUserId);
        ensureLegacyHistoryBounded(answerRecordRepository.countWrongByUserId(currentUserId));
        return convertToBatchResponse(answerRecordRepository.findWrongAnswersByUserId(currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public AnswerRecordBatchResponse getCurrentUserAnswerRecords(Long currentUserId) {
        log.debug("查询当前用户答题记录 - UserId: {}", currentUserId);
        ensureLegacyHistoryBounded(answerRecordRepository.countByUserId(currentUserId));
        return convertToBatchResponse(answerRecordRepository.findByUserIdOrderByAnsweredAtDesc(currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AnswerRecordSummaryResponse> getStudentHistory(
            Long currentUserId, int page, int size, boolean wrongOnly, String keyword) {
        return historyPage(currentUserId, page, size, wrongOnly, keyword);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AnswerRecordSummaryResponse> getPracticeReviewPage(
            Long currentUserId, int page, int size, String keyword) {
        var pageable = historyPageable(page, size);
        var result = answerRecordRepository.findPracticeReviewPage(currentUserId, likeKeyword(keyword), pageable);
        return pageResult(result, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AnswerRecordSummaryResponse> getUserHistory(
            Long targetUserId, Long currentUserId, int page, int size, boolean wrongOnly, String keyword) {
        if (!userRepository.existsById(targetUserId)) {
            throw new UserNotFoundException(String.format("用户不存在：%d", targetUserId));
        }
        if (!userPermissionUtil.canQueryUserData(targetUserId, currentUserId)) {
            throw new InsufficientPermissionException(currentUserId, "学生只能访问自己的答题记录");
        }
        return historyPage(targetUserId, page, size, wrongOnly, keyword);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAnswerSummary getStudentAnswerSummary(Long currentUserId) {
        return new StudentAnswerSummary(
                answerRecordRepository.countByUserId(currentUserId),
                answerRecordRepository.countCorrectByUserId(currentUserId),
                answerRecordRepository.countDistinctWrongQuestionsByUserId(currentUserId));
    }

    private PageResult<AnswerRecordSummaryResponse> historyPage(
            Long userId, int page, int size, boolean wrongOnly, String keyword) {
        var result = answerRecordRepository.findHistoryPage(
                userId, wrongOnly, likeKeyword(keyword), historyPageable(page, size));
        return pageResult(result, page, size);
    }

    private PageRequest historyPageable(int page, int size) {
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("页码须大于零，每页条数须在1到100之间");
        }
        return PageRequest.of(page - 1, size);
    }

    private PageResult<AnswerRecordSummaryResponse> pageResult(
            org.springframework.data.domain.Page<AnswerRecordSummaryResponse> result, int page, int size) {
        return PageResult.<AnswerRecordSummaryResponse>builder()
                .records(result.getContent()).total(result.getTotalElements()).pages(result.getTotalPages())
                .currentPage(page).pageSize(size).build();
    }

    private String likeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.length() > 100) throw new IllegalArgumentException("搜索词不得超过100个字符");
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private void ensureLegacyHistoryBounded(long count) {
        if (count > 100) {
            throw new IllegalArgumentException("历史记录超过100条，请使用分页接口获取");
        }
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
        ensureLegacyHistoryBounded(answerRecordRepository.countByUserId(targetUserId));

        return convertToBatchResponse(answerRecordRepository.findByUserIdOrderByAnsweredAtDesc(targetUserId));
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
        ensureLegacyHistoryBounded(answerRecordRepository.countWrongByUserId(targetUserId));

        return convertToBatchResponse(answerRecordRepository.findWrongAnswersByUserId(targetUserId));
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
        ensureLegacyHistoryBounded(answerRecordRepository.count());

        return convertToBatchResponse(answerRecordRepository.findAll());
    }

    @Override
    public AnswerRecordBatchResponse getAllUsersWrongAnswers(Long currentUserId) {
        log.info("查询所有用户错题记录 - Operator: {}", currentUserId);

        // 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);
        ensureLegacyHistoryBounded(answerRecordRepository.countWrong());

        return convertToBatchResponse(answerRecordRepository.findByCorrectFalseOrderByAnsweredAtDesc());
    }

    // === 会话维度查询功能实现 ===

    @Override
    public AnswerRecordBatchResponse getSessionAnswerRecords(String sessionId, Long currentUserId) {
        log.info("查询会话答题记录 - SessionId: {}, UserId: {}", sessionId, currentUserId);

        try {
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

            if (user.getUserType() != User.UserType.TEACHER && records.isEmpty()) {
                throw new InsufficientPermissionException("权限不足：无法访问该会话");
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
        return latestSessionRecords(teacherId, () -> userRepository.findById(studentId));
    }

    @Override
    public AnswerRecordBatchResponse getStudentLatestSessionRecordsByName(String studentName, Long teacherId) {
        return latestSessionRecords(teacherId, () -> userRepository.findByRealName(studentName));
    }

    private AnswerRecordBatchResponse latestSessionRecords(Long teacherId,
            Supplier<Optional<User>> studentLookup) {
        try {
            userPermissionUtil.checkTeacherPermission(teacherId);
            User student = studentLookup.get().orElseThrow(() -> new UserNotFoundException("学生不存在"));
            if (student.getUserType() != User.UserType.STUDENT) {
                throw new UserNotFoundException("学生不存在");
            }
            String sessionId = getLatestSessionIdForUser(student.getId());
            return convertToBatchResponse(sessionId == null ? List.of()
                    : answerRecordRepository.findBySessionIdAndUserId(sessionId, student.getId()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("学生最近会话记录查询失败 - TeacherId: {}", teacherId, e);
            throw new TechnicalException("查询学生最近会话记录失败：" + e.getMessage());
        }
    }

    private String getLatestSessionIdForUser(Long userId) {
        // 最近会话由最后一条作答决定；时间相同用唯一的 attemptOrder 稳定排序。
        List<String> latest = answerRecordRepository.findLatestSessionIdsByUserId(userId, PageRequest.of(0, 1));
        if (latest.isEmpty() || latest.get(0) == null || latest.get(0).isBlank()) return null;
        return latest.get(0);
    }

    private String snapshotJsonWithoutImage(Question question) throws JsonProcessingException {
        var node = snapshotJson.valueToTree(question);
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.remove("imageBase64");
        }
        return snapshotJson.writeValueAsString(node);
    }

    private AnswerRecordResponse convertToFullResponseDTO(AnswerRecord record) {
        // 获取关联的题目信息
        Question question = record.getQuestionSnapshot()!=null ? null : record.getQuestion();
        if (question == null && record.getQuestionSnapshot()==null) {
            question = questionRepository.findById(record.getQuestionId())
                    .orElseThrow(() -> new QuestionNotFoundException(record.getQuestionId()));
        }
        return questionResponseConverter.convertToFullResponseDTO(record, question);
    }

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
    public AnswerStatsResponse getAnswerStats(Long currentUserId) {
        log.info("获取答题统计数据 - Operator: {}", currentUserId);

        // 权限验证
        userPermissionUtil.checkTeacherPermission(currentUserId);

        // 获取有答题记录的学生人数
        long answeredStudentCount = answerRecordRepository.countDistinctActiveStudentIds();

        // 分母同样是当前启用学生，停用账号只保留历史明细，不进入进度人数。
        long totalStudentCount = userRepository.countByUserTypeAndActiveTrue(User.UserType.STUDENT);

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
