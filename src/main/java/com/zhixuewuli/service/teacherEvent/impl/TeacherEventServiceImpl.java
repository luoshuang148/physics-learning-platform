package com.zhixuewuli.service.teacherEvent.impl;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.exception.teacherevent.EventNotFoundException;
import com.zhixuewuli.common.exception.teacherevent.TeacherEventBusinessException;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.QuestionResponse;
import com.zhixuewuli.dto.response.TeacherEventResponse;
import com.zhixuewuli.dto.response.TaskDetailResponse;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.question.AnswerAccessPolicy;
import com.zhixuewuli.service.teacherEvent.TaskQuestionService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherEventServiceImpl implements TeacherEventService {

    private final TeacherEventRepository teacherEventRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final UserRepository userRepository;
    private final UserPermissionUtil userPermissionUtil;
    private final TaskQuestionService taskQuestions;
    private final AnswerAccessPolicy answerAccess;

    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).withNano(0); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherEventResponse publishEvent(TeacherEventCreateRequest request, Long currentUserId) {
        return createTask(request, currentUserId);
    }

    private TeacherEventResponse createTask(TeacherEventCreateRequest request, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        TeacherEvent.Status status = request.getStatus() == null ? TeacherEvent.Status.PUBLISHED : request.getStatus();
        if (status != TeacherEvent.Status.DRAFT && status != TeacherEvent.Status.PUBLISHED) {
            throw new IllegalArgumentException("新建任务只能保存为草稿或直接发布");
        }
        validateWindow(request.getStartAt(), request.getDueAt());
        // Legacy callers omitted status and questionIds; keep them working while the new UI enforces non-empty published tasks.
        boolean legacyImplicitPublish = request.getStatus() == null;
        var orderedQuestions = taskQuestions.prepare(request, status == TeacherEvent.Status.DRAFT || legacyImplicitPublish);
        User teacher = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));

        LocalDateTime now = now();
        TeacherEvent event = new TeacherEvent();
        event.setTeacherId(userId);
        event.setTeacherName(teacher.getRealName());
        event.setEventTitle(request.getEventTitle().trim());
        event.setEventDescription(request.getEventDescription());
        event.setEventTimestamp(now);
        event.setStatus(status);
        event.setStartAt(status == TeacherEvent.Status.PUBLISHED ? fallback(request.getStartAt(), now) : request.getStartAt());
        event.setDueAt(request.getDueAt());
        TeacherEvent saved = teacherEventRepository.saveAndFlush(event);
        taskQuestions.capture(saved.getId(), orderedQuestions);
        log.info("教学任务已创建 - EventId: {}, Status: {}, UserId: {}", saved.getId(), status, userId);
        return convertToResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherEventResponse updateDraft(Long id, TeacherEventCreateRequest request, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        TeacherEvent event = teacherEventRepository.lockById(id).orElseThrow(EventNotFoundException::new);
        if (event.getStatus() != TeacherEvent.Status.DRAFT) {
            throw new TeacherEventBusinessException("只有草稿任务可以编辑");
        }
        validateWindow(request.getStartAt(), request.getDueAt());
        var orderedQuestions = taskQuestions.prepare(request, true);
        event.setEventTitle(request.getEventTitle().trim());
        event.setEventDescription(request.getEventDescription());
        event.setStartAt(request.getStartAt());
        event.setDueAt(request.getDueAt());
        teacherEventRepository.saveAndFlush(event);
        taskQuestions.replace(event.getId(), orderedQuestions);
        return convertToResponse(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherEventResponse publishTask(Long id, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        TeacherEvent event = teacherEventRepository.lockById(id).orElseThrow(EventNotFoundException::new);
        if (event.getStatus() != TeacherEvent.Status.DRAFT) {
            throw new TeacherEventBusinessException("只有草稿任务可以发布");
        }
        if (taskQuestions.count(event.getId()) == 0) {
            throw new IllegalArgumentException("任务至少需要一道题目才能发布");
        }
        validateWindow(event.getStartAt(), event.getDueAt());
        event.setStatus(TeacherEvent.Status.PUBLISHED);
        if (event.getStartAt() == null) event.setStartAt(now());
        teacherEventRepository.saveAndFlush(event);
        return convertToResponse(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherEventResponse closeTask(Long id, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        TeacherEvent event = teacherEventRepository.lockById(id).orElseThrow(EventNotFoundException::new);
        if (event.getStatus() != TeacherEvent.Status.PUBLISHED) {
            throw new TeacherEventBusinessException("只有已发布任务可以截止");
        }
        event.setStatus(TeacherEvent.Status.CLOSED);
        teacherEventRepository.saveAndFlush(event);
        return convertToResponse(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentEvent(Long currentUserId) {
        userPermissionUtil.checkTeacherPermission(currentUserId);
        teacherEventRepository.findByIsGlobalTrue().ifPresent(event -> {
            event.setStatus(TeacherEvent.Status.ARCHIVED);
            teacherEventRepository.saveAndFlush(event);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveEvent(Long expectedId, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        TeacherEvent event = teacherEventRepository.lockById(expectedId).orElse(null);
        if (event == null || event.getStatus() == TeacherEvent.Status.ARCHIVED) return;
        event.setStatus(TeacherEvent.Status.ARCHIVED);
        teacherEventRepository.saveAndFlush(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherEventResponse replaceEvent(Long expectedId, TeacherEventCreateRequest request, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        TeacherEvent event = teacherEventRepository.lockById(expectedId).orElseThrow(EventNotFoundException::new);
        taskQuestions.prepare(request);
        event.setStatus(TeacherEvent.Status.ARCHIVED);
        teacherEventRepository.saveAndFlush(event);
        return createTask(request, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherEventResponse getEvent(Long id) {
        return convertToResponse(teacherEventRepository.findById(id).orElseThrow(EventNotFoundException::new));
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherEventResponse getEvent(Long id, Long viewerId) {
        TeacherEvent event = teacherEventRepository.findById(id).orElseThrow(EventNotFoundException::new);
        if (event.getStatus() == TeacherEvent.Status.DRAFT && !answerAccess.isTeacher(viewerId)) {
            throw new EventNotFoundException();
        }
        return convertToResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionResponse> getEventQuestions(Long id, Long viewerId) {
        TeacherEvent event = teacherEventRepository.findById(id).orElseThrow(EventNotFoundException::new);
        if (event.getStatus() == TeacherEvent.Status.DRAFT && !answerAccess.isTeacher(viewerId)) {
            throw new EventNotFoundException();
        }
        if (event.isScheduledAt(now()) && !answerAccess.isTeacher(viewerId)) {
            throw new TeacherEventBusinessException("任务尚未开始，暂不能查看题目");
        }
        return answerAccess.forTask(viewerId, id, taskQuestions.responses(id));
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherEventResponse getGlobalEvent() {
        return teacherEventRepository.findByIsGlobalTrue().map(this::convertToResponse).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherEventResponse> listTeacherTasks(Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        return teacherEventRepository.findByStatusNotOrderByCreatedAtDesc(TeacherEvent.Status.ARCHIVED)
                .stream().map(this::convertToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeacherEventResponse> listStudentTasks(Long userId) {
        if (userId == null) throw new IllegalArgumentException("用户未登录");
        return teacherEventRepository.findByStatusOrderByStartAtAscCreatedAtDesc(TeacherEvent.Status.PUBLISHED)
                .stream().map(this::convertToResponse).toList();
    }


    @Override
    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskDetail(Long id, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        TeacherEvent event = teacherEventRepository.findById(id).orElseThrow(EventNotFoundException::new);
        List<Long> questionIds = taskQuestions.ids(id);
        List<QuestionResponse> questionResponses = taskQuestions.responses(id);
        Map<Long, String> previews = new HashMap<>();
        for (QuestionResponse question : questionResponses) {
            String content = question.getContent() == null ? "" : question.getContent().trim();
            previews.put(question.getId(), content.length() > 80 ? content.substring(0, 80) + "…" : content);
        }

        List<User> activeStudents = userRepository.findActiveStudents();
        Map<Long, AnswerRecordRepository.TaskStudentStatsRow> studentStats = new HashMap<>();
        for (var row : answerRecordRepository.findTaskStudentStats(id)) {
            studentStats.put(row.getUserId(), row);
        }

        List<TaskDetailResponse.StudentRow> students = new ArrayList<>();
        long participated = 0;
        long completed = 0;
        long answeredTotal = 0;
        long correctTotal = 0;
        for (User student : activeStudents) {
            var stat = studentStats.get(student.getId());
            long answered = stat == null || stat.getAnsweredQuestions() == null ? 0 : stat.getAnsweredQuestions();
            long correct = stat == null || stat.getCorrectQuestions() == null ? 0 : stat.getCorrectQuestions();
            String status;
            if (answered == 0) status = "NOT_STARTED";
            else if (!questionIds.isEmpty() && answered >= questionIds.size()) status = "COMPLETED";
            else status = "PARTIAL";
            if (!"NOT_STARTED".equals(status)) participated++;
            if ("COMPLETED".equals(status)) completed++;
            answeredTotal += answered;
            correctTotal += correct;
            students.add(TaskDetailResponse.StudentRow.builder()
                    .userId(student.getId())
                    .username(student.getUsername())
                    .realName(student.getRealName())
                    .className(student.getClassName())
                    .status(status)
                    .answeredQuestions(answered)
                    .correctQuestions(correct)
                    .missingQuestions(Math.max(questionIds.size() - answered, 0))
                    .lastAnsweredAt(stat == null || stat.getLastAnsweredAt() == null ? null
                            : com.zhixuewuli.common.util.LearningTimeWire.format(stat.getLastAnsweredAt(), true))
                    .build());
        }
        students.sort((a, b) -> {
            int rank = Integer.compare(statusRank(a.getStatus()), statusRank(b.getStatus()));
            return rank != 0 ? rank : a.getRealName().compareTo(b.getRealName());
        });

        Map<Long, AnswerRecordRepository.TaskQuestionStatsRow> questionStats = new HashMap<>();
        for (var row : answerRecordRepository.findTaskQuestionStats(id)) {
            questionStats.put(row.getQuestionId(), row);
        }
        List<TaskDetailResponse.QuestionRow> questions = new ArrayList<>();
        long totalStudents = activeStudents.size();
        for (Long questionId : questionIds) {
            var stat = questionStats.get(questionId);
            long answered = stat == null || stat.getAnsweredStudents() == null ? 0 : stat.getAnsweredStudents();
            long correct = stat == null || stat.getCorrectStudents() == null ? 0 : stat.getCorrectStudents();
            questions.add(TaskDetailResponse.QuestionRow.builder()
                    .questionId(questionId)
                    .contentPreview(previews.getOrDefault(questionId, "题目 #" + questionId))
                    .answeredStudents(answered)
                    .correctStudents(correct)
                    .unansweredStudents(Math.max(totalStudents - answered, 0))
                    .accuracy(percent(correct, answered))
                    .build());
        }

        TeacherEventResponse meta = convertToResponse(event);
        return TaskDetailResponse.builder()
                .taskId(event.getId())
                .title(event.getEventTitle())
                .status(event.getStatus().name())
                .availability(meta.getAvailability())
                .startAt(event.getStartAt() == null ? null : com.zhixuewuli.common.util.LearningTimeWire.format(event.getStartAt(), event.isTimestampUtc()))
                .dueAt(event.getDueAt() == null ? null : com.zhixuewuli.common.util.LearningTimeWire.format(event.getDueAt(), event.isTimestampUtc()))
                .questionCount(questionIds.size())
                .totalStudents(totalStudents)
                .participatedStudents(participated)
                .completedStudents(completed)
                .notStartedStudents(totalStudents - participated)
                .partialStudents(participated - completed)
                .completionRate(percent(completed, totalStudents))
                .participationRate(percent(participated, totalStudents))
                .accuracy(percent(correctTotal, answeredTotal))
                .answeredQuestions(answeredTotal)
                .correctQuestions(correctTotal)
                .students(students)
                .questions(questions)
                .build();
    }

    private int statusRank(String status) {
        if ("NOT_STARTED".equals(status)) return 0;
        if ("PARTIAL".equals(status)) return 1;
        return 2;
    }

    private double percent(long numerator, long denominator) {
        if (denominator <= 0) return 0;
        return Math.round((double) numerator / denominator * 1000) / 10.0;
    }

    @Override
    @Transactional(readOnly = true)
    public com.zhixuewuli.common.result.PageResult<TeacherEventResponse> getEventHistory(Long userId, int page, int size) {
        userPermissionUtil.checkTeacherPermission(userId);
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        var result = teacherEventRepository.findByIsGlobalFalse(
                org.springframework.data.domain.PageRequest.of(page - 1, size,
                        org.springframework.data.domain.Sort.by("updatedAt", "id").descending()));
        return com.zhixuewuli.common.result.PageResult.<TeacherEventResponse>builder()
                .records(result.map(this::convertToResponse).getContent()).total(result.getTotalElements())
                .pages(result.getTotalPages()).currentPage(page).pageSize(size).build();
    }

    private void validateWindow(LocalDateTime startAt, LocalDateTime dueAt) {
        if (startAt != null && dueAt != null && !dueAt.isAfter(startAt)) {
            throw new IllegalArgumentException("截止时间必须晚于开始时间");
        }
    }

    private LocalDateTime fallback(LocalDateTime value, LocalDateTime defaultValue) {
        return value == null ? defaultValue : value;
    }

    private TeacherEventResponse convertToResponse(TeacherEvent event) {
        LocalDateTime now = now();
        String availability;
        if (event.getStatus() == TeacherEvent.Status.DRAFT) availability = "DRAFT";
        else if (event.getStatus() == TeacherEvent.Status.CLOSED) availability = "CLOSED";
        else if (event.getStatus() == TeacherEvent.Status.ARCHIVED) availability = "ARCHIVED";
        else if (event.isScheduledAt(now)) availability = "SCHEDULED";
        else if (event.isEndedAt(now)) availability = "ENDED";
        else availability = "OPEN";

        List<Long> ids = taskQuestions.ids(event.getId());
        return TeacherEventResponse.builder()
                .id(event.getId())
                .questionIds(ids)
                .questionCount(ids.size())
                .teacherId(event.getTeacherId())
                .teacherName(event.getTeacherName())
                .eventTitle(event.getEventTitle())
                .eventDescription(event.getEventDescription())
                .eventTimestamp(event.getEventTimestamp()).timestampUtc(event.isTimestampUtc())
                .status(event.getStatus())
                .startAt(event.getStartAt())
                .dueAt(event.getDueAt())
                .availability(availability)
                .canAnswer("OPEN".equals(availability))
                .isGlobal(event.getIsGlobal())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
