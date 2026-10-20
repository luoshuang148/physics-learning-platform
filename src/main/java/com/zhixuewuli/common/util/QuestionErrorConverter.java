package com.zhixuewuli.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.question.InvalidQuestionStatisticsException;
import com.zhixuewuli.dto.response.QuestionErrorSummaryResponse;
import com.zhixuewuli.dto.response.QuestionStudentErrorDetailResponse;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.repository.AnswerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目错误分析专用转换工具类
 * 
 * 集中处理DTO转换逻辑，简化Service层代码。
 * 新增性能优化和错误率计算功能。
 * 
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>题目错误统计数据行转换</li>
 * <li>学生错误详情数据行转换</li>
 * <li>JSON选项解析</li>
 * <li>批量转换处理</li>
 * <li>错误率计算优化</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionErrorConverter {

    private final OptionMappingUtil optionMappingUtil;

    /**
     * 转换题目错误统计数据行到响应DTO
     * 
     * @param row 查询结果行数据
     * @return QuestionErrorSummaryResponse 题目错误统计DTO
     * @throws InvalidQuestionStatisticsException 数据转换异常
     */
    public QuestionErrorSummaryResponse convertToQuestionErrorSummary(Object[] row) {
        try {
            QuestionErrorSummaryResponse dto = new QuestionErrorSummaryResponse();

            dto.setQuestionId((Long) row[0]);
            dto.setContent((String) row[1]);
            dto.setOptions(parseOptionsFromJson((String) row[2]));
            dto.setCorrectOption((String) row[3]);
            dto.setExplanation((String) row[4]);
            dto.setDifficulty((String) row[5]);
            dto.setImageBase64((String) row[6]);
            dto.setErrorCount(((Number) row[7]).longValue());
            dto.setCorrectCount(((Number) row[8]).longValue());
            dto.setErrorRate(((Number) row[9]).doubleValue());

            return dto;
        } catch (Exception e) {
            log.error("题目错误统计数据转换失败: {}", Arrays.toString(row), e);
            throw new InvalidQuestionStatisticsException("数据转换异常：" + e.getMessage());
        }
    }

    /**
     * 转换学生错误详情数据行到响应 DTO
     * 
     * @param row 查询结果行数据
     * @return QuestionStudentErrorDetailResponse 学生错误详情 DTO
     * @throws TechnicalException 数据转换异常
     */
    public QuestionStudentErrorDetailResponse convertToStudentErrorDetail(Object[] row) {
        try {
            QuestionStudentErrorDetailResponse dto = new QuestionStudentErrorDetailResponse();

            dto.setUserId((Long) row[0]);
            dto.setStudentName((String) row[1]);
            dto.setClassName((String) row[2]);
            dto.setUserAnswer((String) row[3]);

            // 处理时间类型转换：可能是 Timestamp 或 LocalDateTime
            Object answerTimeObj = row[4];
            if (answerTimeObj instanceof java.sql.Timestamp) {
                dto.setAnswerTime(((java.sql.Timestamp) answerTimeObj).toLocalDateTime());
            } else if (answerTimeObj instanceof java.time.LocalDateTime) {
                dto.setAnswerTime((java.time.LocalDateTime) answerTimeObj);
            }

            return dto;
        } catch (Exception e) {
            log.error("学生错误详情数据转换失败：{}", Arrays.toString(row), e);
            throw new TechnicalException("学生详情数据转换异常：" + e.getMessage());
        }
    }

    /**
     * 解析题目选项JSON
     * 
     * @param json 选项JSON字符串
     * @return Map<String, String> 选项键值对映射
     */
    private Map<String, String> parseOptionsFromJson(String json) {
        try {
            return optionMappingUtil.jsonToOptions(json);
        } catch (Exception e) {
            log.warn("选项JSON解析失败，返回空映射: {}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * 批量转换题目错误统计列表
     * 
     * @param rows 查询结果行列表
     * @return List<QuestionErrorSummaryResponse> DTO列表
     */
    public List<QuestionErrorSummaryResponse> convertQuestionErrorSummaries(
            List<Object[]> rows) {
        return rows.stream()
                .map(this::convertToQuestionErrorSummary)
                .collect(Collectors.toList());
    }

    /**
     * 批量转换学生错误详情列表
     * 
     * @param rows 查询结果行列表
     * @return List<QuestionStudentErrorDetailResponse> DTO列表
     */
    public List<QuestionStudentErrorDetailResponse> convertStudentErrorDetails(
            List<Object[]> rows) {
        return rows.stream()
                .map(this::convertToStudentErrorDetail)
                .collect(Collectors.toList());
    }

    /**
     * 计算总答题人数（用于错误率计算优化）
     * 
     * @param questionId 题目ID
     * @param repository 答题记录Repository
     * @return Long 总答题人次
     */
    public Long calculateTotalAttempts(Long questionId, AnswerRecordRepository repository) {
        // 可以通过缓存优化，避免重复查询
        return repository.countByQuestionId(questionId);
    }

    /**
     * 批量错误率计算优化
     * 
     * @param responses  响应DTO列表
     * @param repository 答题记录Repository
     */
    public void populateErrorRates(List<QuestionErrorSummaryResponse> responses,
            AnswerRecordRepository repository) {
        responses.parallelStream().forEach(response -> {
            Long totalAttempts = calculateTotalAttempts(response.getQuestionId(), repository);
            if (totalAttempts > 0) {
                double errorRate = Math.round((double) response.getErrorCount() / totalAttempts * 10000) / 100.0;
                response.setErrorRate(errorRate);
            }
        });
    }

    /**
     * 批量转换 AnswerRecord 列表为学生错误详情 DTO 列表
     * 
     * @param records 答题记录列表（需预加载 user）
     * @return DTO 列表
     */
    public List<QuestionStudentErrorDetailResponse> convertStudentErrorDetailsFromRecords(List<AnswerRecord> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        // 注意：这个方法可能不再使用，因为 findStudentErrorsByQuestionId 返回 Object[]
        return records.stream()
                .map(record -> {
                    QuestionStudentErrorDetailResponse dto = new QuestionStudentErrorDetailResponse();
                    dto.setUserId(record.getUserId());
                    if (record.getUser() != null) {
                        dto.setStudentName(record.getUser().getRealName());
                        dto.setClassName(record.getUser().getClassName());
                    } else {
                        dto.setStudentName("未知");
                        dto.setClassName("未知");
                    }
                    dto.setUserAnswer(record.getUserAnswer());
                    dto.setAnswerTime(record.getAnsweredAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}