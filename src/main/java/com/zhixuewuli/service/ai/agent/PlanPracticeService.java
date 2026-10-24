package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.dto.request.PlanPracticeAnswerRequest;
import com.zhixuewuli.dto.response.QuestionResponse;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.question.AnswerAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 计划条目补练：按 planItemId 授权并计分。
 * 生成 practice=true、eventId=null 的作答，并在同一事务中关联条目；
 * 此记录不混入原作业首次成绩；进度按 planItemId 汇总。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanPracticeService {

    private final JdbcTemplate jdbc;
    private final QuestionRepository questions;
    private final AnswerRecordRepository answers;
    private final AnswerAccessPolicy answerAccess;
    private final ObjectMapper json;

    /** 提交前仅返回题干/选项（不含正确答案）。 */
    @Transactional(readOnly = true)
    public QuestionResponse question(Long viewerId, Long planId, Long itemId) {
        Map<String, Object> row = loadItem(viewerId, planId, itemId);
        Long questionId = ((Number) row.get("question_id")).longValue();
        Object revision = row.get("question_revision");
        Question question = questions.findById(questionId)
                .orElseThrow(() -> new BusinessException("题目已不可用，请重新生成计划"));
        if (revision != null && question.getAiContentRevision() != ((Number) revision).longValue()) {
            // 激活后题目被修改：拒绝新练习，历史记录仍保留
            throw new BusinessException("题目已更新，请重新生成补练计划");
        }
        if (answerAccess.blocksPracticeBeforeAssignment(viewerId, null, questionId)) {
            throw new BusinessException("该题仍受作业保护，请先完成首次作答");
        }
        return QuestionResponse.builder()
                .id(question.getId())
                .content(question.getContent())
                .options(com.zhixuewuli.common.util.OptionMappingUtil.class != null
                        ? parseOptions(question.getOptions()) : null)
                .difficulty(question.getDifficulty())
                .imageBase64(question.getImageBase64())
                .knowledgePointCodes(question.getKnowledgePointCodes())
                .build();
    }

    /**
     * 提交作答：服务端确定题目与归属；同 requestId 重放得到原结果，参数不同则 409。
     */
    @Transactional
    public Map<String, Object> submit(Long viewerId, Long planId, Long itemId, PlanPracticeAnswerRequest request) {
        Map<String, Object> row = loadItem(viewerId, planId, itemId);
        Long questionId = ((Number) row.get("question_id")).longValue();
        Object revision = row.get("question_revision");

        var previous = answers.findByUserIdAndRequestId(viewerId, request.getRequestId());
        if (previous.isPresent()) {
            AnswerRecord old = previous.get();
            if (!old.getQuestionId().equals(questionId) || !old.getUserAnswer().equals(request.getUserAnswer())) {
                throw new BusinessException("requestId 已用于不同的提交内容");
            }
            return progressOf(viewerId, planId, itemId, old);
        }

        Question question = questions.findById(questionId)
                .orElseThrow(() -> new BusinessException("题目已不可用，请重新生成计划"));
        if (revision != null && question.getAiContentRevision() != ((Number) revision).longValue()) {
            throw new BusinessException("题目已更新，请重新生成补练计划");
        }
        // 题目在激活后被禁用/重新受保护则拒绝新练习
        if (!question.isAiPracticeEnabled()) {
            throw new BusinessException("该题已下架，请重新生成补练计划");
        }
        if (answerAccess.blocksPracticeBeforeAssignment(viewerId, null, questionId)) {
            throw new BusinessException("该题仍受作业保护，请先完成首次作答");
        }

        boolean correct = request.getUserAnswer().equals(question.getCorrectOption());
        AnswerRecord record = new AnswerRecord();
        record.setId(UUID.randomUUID().toString());
        record.setSessionId("plan-" + planId + "-" + itemId);
        record.setUserId(viewerId);
        record.setQuestionId(questionId);
        record.setEventId(null);
        record.setPractice(true);
        record.setUserAnswer(request.getUserAnswer());
        record.setCorrect(correct);
        record.setRequestId(request.getRequestId());
        record.setAnsweredAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        record.setContentSnapshot(question.getContent());
        record.setOptionsSnapshot(question.getOptions());
        record.setCorrectSnapshot(question.getCorrectOption());
        record.setAnalysisSnapshot(question.getExplanation());
        record.setDifficultySnapshot(question.getDifficulty());
        try {
            // Plan records retain the exact graded question, including its image, for later review.
            record.setQuestionSnapshot(json.writeValueAsString(question));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalidSnapshot) {
            throw new BusinessException("无法保存题目快照，请重试");
        }
        answers.save(record);

        // 同事务关联条目；answer_record_id 与 (user_id, request_id) 唯一
        try {
            jdbc.update("""
                    INSERT INTO learning_plan_attempt_links(plan_item_id, answer_record_id, user_id, request_id)
                    VALUES(?,?,?,?)
                    """, itemId, record.getId(), viewerId, request.getRequestId());
        } catch (Exception duplicate) {
            // 并发重放：回滚后由上面的 findByUserIdAndRequestId 路径返回原结果
            throw new BusinessException("提交冲突，请重试");
        }
        return progressOf(viewerId, planId, itemId, record);
    }

    /** 进度按条目链接汇总；ON_TRACK 看最新一次作答。 */
    @Transactional(readOnly = true)
    public Map<String, Object> progressOf(Long viewerId, Long planId, Long itemId, AnswerRecord last) {
        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT COUNT(*) AS attempts,
                       COALESCE(SUM(CASE WHEN a.correct = 1 THEN 1 ELSE 0 END),0) AS correctCount
                FROM learning_plan_attempt_links l
                JOIN answer_records a ON a.id = l.answer_record_id
                WHERE l.plan_item_id = ? AND l.user_id = ?
                """, itemId, viewerId);
        long attempts = ((Number) counts.get("attempts")).longValue();
        long correctCount = ((Number) counts.get("correctCount")).longValue();
        String progressStatus = last != null && Boolean.TRUE.equals(last.getCorrect()) ? "ON_TRACK" : "NEEDS_WORK";
        return Map.of(
                "planId", planId,
                "itemId", itemId,
                "correct", last != null ? Boolean.TRUE.equals(last.getCorrect()) : false,
                "attempts", attempts,
                "correctCount", correctCount,
                "progressStatus", progressStatus);
    }

    private Map<String, Object> loadItem(Long viewerId, Long planId, Long itemId) {
        try {
            return jdbc.queryForMap("""
                    SELECT i.id, i.question_id, i.question_revision, p.status, p.user_id
                    FROM learning_plan_items i
                    JOIN learning_plans p ON p.id = i.plan_id
                    WHERE i.id = ? AND p.id = ? AND p.user_id = ?
                    """, itemId, planId, viewerId);
        } catch (Exception notFound) {
            throw new BusinessException("计划条目不存在");
        }
    }

    private Map<String, String> parseOptions(String optionsJson) {
        try {
            return json.readValue(optionsJson,
                    json.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception error) {
            return Map.of();
        }
    }
}
