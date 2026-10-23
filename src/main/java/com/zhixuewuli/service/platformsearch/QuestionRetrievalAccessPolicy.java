package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.EligibleQuestion;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.TeacherEventQuestionRepository;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.service.question.AnswerAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 平台题库检索统一授权入口。
 *
 * <p>规则（设计 §6）：
 * 候选必须显式 {@code ai_practice_enabled=true}；沿用现有保守的待作答题保护——
 * 对尚未首次作答的进行中/未开始作业关联题，整个题目不进入新 AI 题库检索与补练候选；
 * 工具不接受模型传入的 userId/classId；来源 ID 即使猜中也要重新鉴权。</p>
 */
@Component
@RequiredArgsConstructor
public class QuestionRetrievalAccessPolicy {

    private final QuestionRepository questions;
    private final AnswerRecordRepository answers;
    private final TeacherEventQuestionRepository taskQuestions;
    private final TeacherEventRepository tasks;
    private final AnswerAccessPolicy answerAccess;

    /**
     * 生成当前查看者在给定请求下可检索的候选题集合（仅 ID+revision）。
     * 过滤后不足时不得扩大授权范围。
     */
    public List<EligibleQuestion> eligible(Long viewerId, SearchRequest request) {
        if (viewerId == null) return List.of();
        boolean teacher = answerAccess.isTeacher(viewerId);

        Set<Long> protectedIds = protectedQuestionIds(viewerId, teacher);
        Set<Long> attempted = request != null && request.excludeAttempted()
                ? attemptedQuestionIds(viewerId) : Set.of();

        List<Question> all = questions.findAll();
        List<EligibleQuestion> result = new ArrayList<>();
        for (Question q : all) {
            if (q == null || q.getId() == null) continue;
            if (!q.isAiPracticeEnabled()) continue;
            if (protectedIds.contains(q.getId())) continue;
            if (attempted.contains(q.getId())) continue;
            if (request != null && request.difficulty() != null
                    && !request.difficulty().equals(q.getDifficulty())) continue;
            if (request != null && request.knowledgePointCodes() != null
                    && !request.knowledgePointCodes().isEmpty()
                    && !overlap(request.knowledgePointCodes(), q.getKnowledgePointCodes())) continue;
            result.add(new EligibleQuestion(q.getId(), q.getAiContentRevision()));
        }
        return result;
    }

    /**
     * 单题是否可作为检索/来源/补练候选（重新鉴权用）。
     */
    public boolean isEligible(Long viewerId, Long questionId, long revision) {
        if (viewerId == null || questionId == null) return false;
        Question q = questions.findById(questionId).orElse(null);
        if (q == null || !q.isAiPracticeEnabled()) return false;
        if (q.getAiContentRevision() != revision) return false;
        if (protectedQuestionIds(viewerId, answerAccess.isTeacher(viewerId)).contains(questionId)) return false;
        return true;
    }

    /** 待考题保护集合：尚未首次作答的进行中/未开始作业关联题。教师不受此限制。 */
    public Set<Long> protectedQuestionIds(Long viewerId, boolean teacher) {
        if (teacher) return Set.of();
        Set<Long> protectedIds = new HashSet<>();
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        for (var task : tasks.findPublishedTasks()) {
            if (task != null && task.isEndedAt(now)) continue;
            Set<Long> completed = answers.findCompletedAssignmentQuestionIds(viewerId, task.getId());
            for (Long questionId : taskQuestions.findQuestionIdsByEventId(task.getId())) {
                if (!completed.contains(questionId)) protectedIds.add(questionId);
            }
        }
        return protectedIds;
    }

    /** 本人已作答题（含补练 practice 记录）。 */
    public Set<Long> attemptedQuestionIds(Long viewerId) {
        return answers.findByUserIdOrderByAnsweredAtDesc(viewerId).stream()
                .map(r -> r.getQuestionId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean overlap(List<String> filter, List<String> owned) {
        if (owned == null || owned.isEmpty()) return false;
        Set<String> set = new HashSet<>(owned);
        return filter.stream().anyMatch(set::contains);
    }
}
