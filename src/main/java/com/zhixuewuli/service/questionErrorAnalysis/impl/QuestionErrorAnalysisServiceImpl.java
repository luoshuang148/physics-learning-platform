package com.zhixuewuli.service.questionErrorAnalysis.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.AIStreamExecutor;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.common.util.QuestionErrorConverter;
import com.zhixuewuli.common.util.StreamContentFixer;
import com.zhixuewuli.common.util.StreamUtils;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.dto.response.QuestionErrorDetailsResponse;
import com.zhixuewuli.dto.response.QuestionErrorSummaryResponse;
import com.zhixuewuli.dto.response.QuestionStudentErrorDetailResponse;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.ConversationHistory;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.questionErrorAnalysis.QuestionErrorAnalysisService;
import com.zhixuewuli.service.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionErrorAnalysisServiceImpl implements QuestionErrorAnalysisService {

    private final com.zhixuewuli.service.ai.report.ReportInputRepository reportInputs;
    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final QuestionErrorConverter errorConverter;
    private final OptionMappingUtil optionMappingUtil;
    private final SessionManager sessionManager;
    private final AliyunAIClient aliyunAIClient;
    private final AIBusinessProperties aiBusinessProperties;
    private final AIStreamingProperties aiStreamingProperties;
    private final Executor aiTaskExecutor;
    private final StreamUtils streamUtils;
    private final ObjectMapper objectMapper;

    @Override
    public List<QuestionErrorSummaryResponse> getAllQuestionErrorStatistics(Long teacherId) {
        log.info("开始查询题目错误统计 - TeacherId: {}", teacherId);
        try {
            List<Object[]> statistics = answerRecordRepository.findQuestionErrorStatisticsOptimized();
            List<QuestionErrorSummaryResponse> result = errorConverter.convertQuestionErrorSummaries(statistics);
            log.info("题目错误统计查询完成 - 找到{}道有错误的题目", result.size());
            return result;
        } catch (com.zhixuewuli.common.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询题目错误统计失败 - TeacherId: {}", teacherId, e);
            throw new TechnicalException("查询题目错误统计失败：" + e.getMessage());
        }
    }

    @Override
    public void streamTeacherQuestionAnalysis(AIAskRequest request, SseEmitter emitter, Long teacherId,
            String sessionId) {
        AIStreamExecutor.submit(aiTaskExecutor, teacherId, emitter, streamUtils, () -> {
            executeTeacherQuestionAnalysisStream(request, emitter, teacherId, sessionId);
        });
    }

    @Override
    public QuestionErrorDetailsResponse getQuestionErrorDetails(Long questionId, Long teacherId) {
        log.info("获取题目错误详情 - QuestionId: {}, TeacherId: {}", questionId, teacherId);

        try {
            log.info("权限验证通过");

            // 2. 验证题目存在
            var questionOpt = questionRepository.findById(questionId);
            if (questionOpt.isEmpty()) {
                log.error("题目不存在 - QuestionId: {}", questionId);
                throw new QuestionNotFoundException(questionId);
            }
            log.info("题目存在：{}", questionOpt.get().getContent());

            // 3. 单次查询获取所有错误记录（保留优化）
            List<Object[]> studentErrors = answerRecordRepository.findStudentErrorsByQuestionId(questionId);
            log.info("找到 {} 条学生错误记录", studentErrors.size());

            // 4. 转换学生列表
            List<QuestionStudentErrorDetailResponse> studentList = errorConverter
                    .convertStudentErrorDetails(studentErrors);
            log.info("转换完成，返回 {} 个学生", studentList.size());

            return QuestionErrorDetailsResponse.builder()
                    .studentList(studentList)
                    .build();
        } catch (Exception e) {
            log.error("获取题目错误详情失败 - QuestionId: {}, Error: {}", questionId, e.getMessage(), e);
            throw e;
        }
    }

    // ==================== 私有方法 ====================

    private void executeTeacherQuestionAnalysisStream(AIAskRequest request, SseEmitter emitter, Long teacherId,
            String sessionId) {
        AIStreamExecutor.executeStream("教师题目分析", teacherId, emitter, streamUtils, () -> {
            log.info("教师题目分析开始 - 教师ID: {}, 会话ID: {}, 题目ID: {}", teacherId, sessionId, request.getQuestionId());

            // 2. 验证题目存在答题记录
            Long questionId = request.getQuestionId();
            Long recordCount = answerRecordRepository.countByQuestionId(questionId);
            if (recordCount == null || recordCount == 0) {
                log.error("题目无答题记录 - QuestionId: {}", questionId);
                throw new AnswerRecordNotFoundException("题目ID: " + questionId, "题目无答题记录");
            }

            // 3. 获取或创建会话上下文
            SessionContextInternalDTO sessioncontext = sessionManager.getOrCreateSession(
                    sessionId,
                    teacherId,
                    ConversationType.TEACHER_QUESTION_ANALYSIS,
                    request.getQuestionId(),
                    () -> buildTeacherQuestionAnalysisContext(request.getQuestionId()));
                streamUtils.bindContext(emitter, sessioncontext);

            // 4. 判断是否为首次交互
            boolean isFirstInteraction = sessioncontext.getInteractionRecords() == null
                    || sessioncontext.getInteractionRecords().isEmpty();
            log.debug("判断是否为首次交互: {}, interactionRecords: {}", isFirstInteraction,
                    sessioncontext.getInteractionRecords() == null ? "null"
                            : "size=" + sessioncontext.getInteractionRecords().size());

            // 5. 确定最终使用的请求对象（首次交互时替换问题）
            AIAskRequest finalRequest;
            if (isFirstInteraction) {
                // 不再发送初始化数据，仅使用默认提示词
                finalRequest = new AIAskRequest();
                finalRequest.setQuestionId(request.getQuestionId());
                String defaultPrompt = aiBusinessProperties.getTeacherQuestionAnalysisDefaultPrompt();
                finalRequest.setQuestion(defaultPrompt);
                log.info("首次交互，使用默认提示词：{}", defaultPrompt);
            } else {
                finalRequest = request;
                log.debug("非首次交互，使用原始请求");
            }

            // 6. 构建提示词（使用 finalRequest）
            String prompt = buildTeacherQuestionAnalysisPrompt(finalRequest, sessioncontext);
            log.debug("教师题目分析提示词构建完成，长度: {}", prompt.length());

            // 7. 获取教师报告专用模型
            String model = aiBusinessProperties.getTeacherReportGenerationModel();
            log.debug("使用AI模型: {}", model);

            // 8. 调用阿里云客户端流式生成
            log.debug("调用阿里云客户端流式生成");
            StringBuilder fullResponse = new StringBuilder();
            aliyunAIClient.streamGenerate(prompt, model, new AIStreamCallback() {
                @Override
                public void onCancel(Runnable action) { streamUtils.onCancel(emitter, action); }

                @Override
                public boolean isCancelled() {
                    return streamUtils.isCancelled(emitter) || (sessioncontext != null && sessioncontext.isCancelled());
                }

                @Override
                public void onContent(String content, boolean isLast) {
                    if (isCancelled()) {
                        return;
                    }
                    try {
                        // 使用 StreamContentFixer 修复 LaTeX/Markdown 格式问题
                        String fixedContent = StreamContentFixer.fix(content);
                        
                        emitter.send(SseEmitter.event().name("ai-content").data(fixedContent));
                        fullResponse.append(fixedContent);
                    } catch (IOException e) {
                        log.error("发送SSE数据失败", e);
                        streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "SSE发送失败: " + e.getMessage());
                    }
                }

                @Override
                public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                    try {
                    boolean cancelled = isCancelled();

                        // 保存对话历史时，使用 finalRequest 中的问题
                    if (fullResponse.length() > 0) {
                        sessionManager.addInteraction(sessioncontext, finalRequest.getQuestion(),
                            fullResponse.toString(),
                            totalTokens);
                        sessionManager.updateTokenUsage(sessioncontext, totalTokens);
                    }

                    if (cancelled) {
                        log.info("教师题目分析任务已取消，已保存部分内容 - 会话ID: {}, 响应长度: {}",
                            sessionId, fullResponse.length());
                        return;
                    }

                        Map<String, Object> completeData = new HashMap<>();
                        completeData.put("promptTokens", promptTokens);
                        completeData.put("completionTokens", completionTokens);
                        completeData.put("totalTokens", totalTokens);
                        emitter.send(SseEmitter.event().name("complete")
                                .data(objectMapper.writeValueAsString(completeData)));

                        log.info("教师题目分析完成 - 教师ID: {}, 会话ID: {}, token消耗: {}", teacherId, sessionId, totalTokens);
                        streamUtils.safeCompleteEmitter(emitter);
                    } catch (Exception e) {
                        log.error("完成处理失败", e);
                        streamUtils.sendError(emitter, ResultCode.AI_PARSE_ERROR, "处理完成时发生错误");
                    }
                }

                @Override
                public void onError(Exception error) {
                    if (isCancelled()) {
                        log.info("教师题目分析任务已取消，忽略 onError 回调");
                        return;
                    }
                    log.error("教师题目分析过程中发生错误", error);
                    streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "分析失败，请稍后重试");
                }
            });
        });
    }

    /**
     * 构建教师题目分析的初始上下文（题目详情 + 所有学生答题记录）
     */
    private String buildTeacherQuestionAnalysisContext(Long questionId) {
        List<AnswerRecord> records=reportInputs.question(questionId);
        if(records.isEmpty()) return "本题暂无学生作答，不能推断班级掌握情况。";
        return com.zhixuewuli.service.ai.report.ReportPreparation.prepare(records,Map.of(),objectMapper,true,
                aiBusinessProperties.getTeacherReportGenerationModel()).prompt();
    }

    /**
     * 构建教师题目分析的提示词（包含会话历史 + 当前问题）
     */
    private String buildTeacherQuestionAnalysisPrompt(AIAskRequest request, SessionContextInternalDTO context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(context.getContext()).append("\n\n");
        context.getLock().readLock().lock();
        try {
            Map<Integer, ConversationHistory> records = context.getInteractionRecords();
            if (records != null && !records.isEmpty()) {
                prompt.append("【历史对话】\n");
                records.values().forEach(history -> {
                    prompt.append("教师：").append(history.getQuestion()).append("\n");
                    prompt.append("AI：").append(history.getAnswer()).append("\n\n");
                });
            }
        } finally {
            context.getLock().readLock().unlock();
        }

        prompt.append("【教师的问题】\n").append(request.getQuestion());
        return prompt.toString();
    }

    /**
     * 格式化题目选项
     */
    private String formatOptionsForPrompt(Question question) {
        if (question == null || question.getOptions() == null) {
            return "无选项信息";
        }
        try {
            Map<String, String> optionsMap = optionMappingUtil.jsonToOptions(question.getOptions());
            StringBuilder sb = new StringBuilder();
            sb.append("A: ").append(optionsMap.get("A")).append("\n");
            sb.append("B: ").append(optionsMap.get("B")).append("\n");
            sb.append("C: ").append(optionsMap.get("C")).append("\n");
            sb.append("D: ").append(optionsMap.get("D"));
            return sb.toString();
        } catch (Exception e) {
            log.warn("解析题目选项JSON失败，使用原始字符串: {}", question.getOptions(), e);
            return question.getOptions();
        }
    }

}