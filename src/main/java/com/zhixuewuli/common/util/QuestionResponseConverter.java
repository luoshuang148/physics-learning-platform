package com.zhixuewuli.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.dto.response.AnswerRecordResponse;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目响应转换工具类
 * 
 * 提供答题记录到响应DTO的转换功能，包括题目详细信息的解析和填充。
 * 统一处理JSON选项解析、难度转换等重复逻辑。
 * 
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>答题记录到完整响应DTO的转换</li>
 * <li>JSON选项字符串到列表的解析</li>
 * <li>数字难度到中文描述的转换</li>
 * <li>异常情况的安全处理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionResponseConverter {

    private final OptionMappingUtil optionMappingUtil;
    private final com.fasterxml.jackson.databind.ObjectMapper snapshotJson;

    /**
     * 将答题记录实体转换为包含完整信息的响应DTO
     * 
     * @param record   答题记录实体
     * @param question 关联的题目实体
     * @return 包含完整信息的响应DTO
     */
    public AnswerRecordResponse convertToFullResponseDTO(AnswerRecord record, Question question) {
        if (record == null) {
            throw new AnswerRecordNotFoundException("答题记录不能为空");
        }
        if (record.getQuestionSnapshot() != null) {
            try { question=snapshotJson.readValue(record.getQuestionSnapshot(),Question.class); }
            catch(Exception error){throw new IllegalStateException("作答快照损坏",error);}
        }
        if (question == null) {
            throw new QuestionNotFoundException("题目不存在");
        }

        // 解析选项JSON
        Map<String, String> optionsMap = parseOptionsFromJson(question.getOptions());

        return AnswerRecordResponse.builder()
                .recordId(record.getId())
                .eventId(record.getEventId()).requestId(record.getRequestId()).practice(record.isPractice()).attemptOrder(record.getAttemptOrder())
                .userId(record.getUserId())
                .questionId(question.getId())
                .userAnswer(record.getUserAnswer())
                .correct(record.getCorrect())
                .answeredAt(record.getAnsweredAt())
                .errorReason(record.getErrorReason())
                .questionContent(question.getContent())
                .questionOptions(optionsMap)
                .correctAnswer(question.getCorrectOption())
                .questionAnalysis(question.getExplanation())
                .difficulty(getDifficultyString(question.getDifficulty()))
                .imageBase64(question.getImageBase64())
                .sessionId(record.getSessionId())
                .build();
    }

    /**
     * 从JSON字符串解析选项键值对映射
     * 
     * @param optionsJson 选项JSON字符串
     * @return 解析后的选项键值对映射
     */
    public Map<String, String> parseOptionsFromJson(String optionsJson) {
        try {
            return optionMappingUtil.jsonToOptions(optionsJson);
        } catch (Exception e) {
            log.warn("解析选项JSON失败，返回空映射: {}", optionsJson, e);
            return new HashMap<>();
        }
    }

    /**
     * 将数字难度转换为字符串表示
     * 
     * @param difficulty 数字难度(1-5)
     * @return 中文难度描述
     */
    public String getDifficultyString(Integer difficulty) {
        if (difficulty == null)
            return "未知";

        return switch (difficulty) {
            case 1 -> "简单";
            case 2 -> "较易";
            case 3 -> "中等";
            case 4 -> "较难";
            case 5 -> "困难";
            default -> "未知";
        };
    }
}