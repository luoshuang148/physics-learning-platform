package com.zhixuewuli.service.question;

import com.zhixuewuli.dto.response.QuestionResponse;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.TeacherEventQuestionRepository;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 集中决定学生何时可以看到答案、解析和解析视频映射。
 *
 * <p>教师始终可以读取完整题目。学生只有在完成对应任务题的首次作答后，
 * 才能看到该题的受保护字段；任务外自由练习保留原有学习功能。</p>
 */
@Component
@RequiredArgsConstructor
public class AnswerAccessPolicy {
    private final UserRepository users;
    private final AnswerRecordRepository answers;
    private final TeacherEventQuestionRepository taskQuestions;
    private final TeacherEventRepository tasks;

    public boolean isTeacher(Long userId) {
        return userId != null && users.findById(userId)
                .map(user -> user.getUserType() == User.UserType.TEACHER)
                .orElse(false);
    }

    /** Task snapshot entry: one completion query for the whole batch. */
    public List<QuestionResponse> forTask(Long viewerId, Long eventId, List<QuestionResponse> questions) {
        if (isTeacher(viewerId)) return questions;
        Set<Long> completed = answers.findCompletedAssignmentQuestionIds(viewerId, eventId);
        return questions.stream()
                .map(question -> completed.contains(question.getId()) ? question : protectedView(question))
                .toList();
    }

    /** Question-bank entry: protect questions that belong to the active task and are not yet first-answered. */
    public QuestionResponse forBank(Long viewerId, QuestionResponse question) {
        return forBank(viewerId, List.of(question)).getFirst();
    }

    public List<QuestionResponse> forBank(Long viewerId, List<QuestionResponse> questions) {
        if (isTeacher(viewerId)) return questions;
        var publishedTasks = tasks.findPublishedTasks();
        if (publishedTasks.isEmpty()) return questions;
        Set<Long> protectedIds = new HashSet<>();
        for (var task : publishedTasks) {
            Set<Long> completed = answers.findCompletedAssignmentQuestionIds(viewerId, task.getId());
            for (Long questionId : taskQuestions.findQuestionIdsByEventId(task.getId())) {
                if (!completed.contains(questionId)) protectedIds.add(questionId);
            }
        }
        return questions.stream()
                .map(question -> protectedIds.contains(question.getId()) ? protectedView(question) : question)
                .toList();
    }

    /**
     * A practice/free-practice request cannot be used to bypass the active task's first attempt.
     * Archived tasks keep their historical practice behaviour.
     */
    public boolean blocksPracticeBeforeAssignment(Long userId, Long eventId, Long questionId) {
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        if (eventId != null) {
            var task = tasks.findById(eventId).filter(value -> value.isOpenAt(now)).orElse(null);
            return task != null
                    && taskQuestions.findByEventIdAndQuestionId(task.getId(), questionId).isPresent()
                    && !answers.existsByUserIdAndEventIdAndQuestionIdAndPracticeFalse(userId, task.getId(), questionId);
        }
        for (var task : openTasks()) {
            if (taskQuestions.findByEventIdAndQuestionId(task.getId(), questionId).isPresent()
                    && !answers.existsByUserIdAndEventIdAndQuestionIdAndPracticeFalse(userId, task.getId(), questionId)) {
                return true;
            }
        }
        return false;
    }

    private List<com.zhixuewuli.entity.TeacherEvent> openTasks() {
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        return tasks.findPublishedTasks().stream().filter(task -> task.isOpenAt(now)).toList();
    }

    private QuestionResponse protectedView(QuestionResponse source) {
        return QuestionResponse.builder()
                .snapshotOrigin(source.getSnapshotOrigin())
                .id(source.getId())
                .content(source.getContent())
                .options(source.getOptions())
                .difficulty(source.getDifficulty())
                .imageBase64(source.getImageBase64())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
