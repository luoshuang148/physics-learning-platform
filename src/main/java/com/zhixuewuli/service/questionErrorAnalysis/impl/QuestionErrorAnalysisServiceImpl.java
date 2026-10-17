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
import com.zhixuewuli.common.util.HanLPWordCloudUtil;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.common.util.QuestionErrorConverter;
import com.zhixuewuli.common.util.StreamContentFixer;
import com.zhixuewuli.common.util.StreamUtils;
import com.zhixuewuli.common.util.UserPermissionUtil;
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

    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final UserPermissionUtil userPermissionUtil;
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
            userPermissionUtil.checkTeacherPermission(teacherId);
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
        aiTaskExecutor.execute(() -> {
            executeTeacherQuestionAnalysisStream(request, emitter, teacherId, sessionId);
        });
    }

    @Override
    public QuestionErrorDetailsResponse getQuestionErrorDetails(Long questionId, Long teacherId) {
        log.info("获取题目错误详情 - QuestionId: {}, TeacherId: {}", questionId, teacherId);

        try {
            // 1. 权限验证
            userPermissionUtil.checkTeacherPermission(teacherId);
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

            // 5. 从查询结果中提取错误原因（避免第二次查询）
            List<String> errorReasons = studentErrors.stream()
                    .map(row -> (String) row[5]) // error_reason 在第 5 个位置
                    .filter(reason -> reason != null && !reason.trim().isEmpty())
                    .collect(java.util.stream.Collectors.toList());

            // 6. 生成词云数据
            Map<String, Integer> wordCloud = HanLPWordCloudUtil.analyzeErrorReasons(errorReasons);
            log.info("词云生成完成，包含 {} 个词", wordCloud.size());

            // 7. 构建响应
            return QuestionErrorDetailsResponse.builder()
                    .studentList(studentList)
                    .wordCloud(wordCloud)
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
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Starting executeTeacherQuestionAnalysisStream");

            // 1. 权限验证
            userPermissionUtil.checkTeacherPermission(teacherId);
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Permission check passed");

            // 2. 验证题目存在答题记录
            Long questionId = request.getQuestionId();
            Long recordCount = answerRecordRepository.countByQuestionId(questionId);
            if (recordCount == null || recordCount == 0) {
                log.error("题目无答题记录 - QuestionId: {}", questionId);
                throw new AnswerRecordNotFoundException("题目ID: " + questionId, "题目无答题记录");
            }
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Question has " + recordCount + " answer records");

            // 3. 获取或创建会话上下文
            SessionContextInternalDTO sessioncontext = sessionManager.getOrCreateSession(
                    sessionId,
                    teacherId,
                    ConversationType.TEACHER_QUESTION_ANALYSIS,
                    request.getQuestionId(),
                    () -> buildTeacherQuestionAnalysisContext(request.getQuestionId()));
                streamUtils.bindContext(emitter, sessioncontext);
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Got session context, lock is "
                    + (sessioncontext.getLock() == null ? "null" : "not null"));

            // 4. 判断是否为首次交互
            boolean isFirstInteraction = sessioncontext.getInteractionRecords() == null
                    || sessioncontext.getInteractionRecords().isEmpty();
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: isFirstInteraction = " + isFirstInteraction
                    + ", interactionRecords = " + (sessioncontext.getInteractionRecords() == null ? "null"
                            : "not null, size=" + sessioncontext.getInteractionRecords().size()));
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
                System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Created finalRequest with default prompt");
            } else {
                finalRequest = request;
                log.debug("非首次交互，使用原始请求");
                System.out.println(
                        "DEBUG QuestionErrorAnalysisServiceImpl: isFirstInteraction=false, using original request");
            }

            // 6. 构建提示词（使用 finalRequest）
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: About to build prompt");
            String prompt = buildTeacherQuestionAnalysisPrompt(finalRequest, sessioncontext);
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Prompt built, length=" + prompt.length());
            log.debug("教师题目分析提示词构建完成，长度: {}", prompt.length());

            // 7. 获取教师报告专用模型
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: About to get model");
            String model = aiBusinessProperties.getTeacherReportGenerationModel();
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Got model=" + model);
            log.debug("使用AI模型: {}", model);

            // 8. 调用阿里云客户端流式生成
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: About to call aliyunAIClient.streamGenerate");
            log.debug("调用阿里云客户端流式生成");
            StringBuilder fullResponse = new StringBuilder();
            aliyunAIClient.streamGenerate(prompt, model, new AIStreamCallback() {
                @Override
                public boolean isCancelled() {
                    return sessioncontext != null && sessioncontext.isCancelled();
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
                    streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "分析失败: " + error.getMessage());
                }
            });
            System.out.println(
                    "DEBUG QuestionErrorAnalysisServiceImpl: aliyunAIClient.streamGenerate called and callback returned");
        });
        System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: AIStreamExecutor.executeStream completed");
    }

    /**
     * 构建教师题目分析的初始上下文（题目详情 + 所有学生答题记录）
     */
    private String buildTeacherQuestionAnalysisContext(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));

        // 获取该题目的所有答题记录，按答题时间倒序排列
        List<AnswerRecord> records = answerRecordRepository.findByQuestionIdOrderByAnsweredAtDesc(questionId);

        StringBuilder context = new StringBuilder();
        context.append("请根据以下题目及学生的答题情况回答问题。\n\n");
        context.append("【题目信息】\n");
        context.append("题目内容：").append(question.getContent()).append("\n\n");
        if (question.getOptions() != null) {
            context.append("题目选项：\n").append(formatOptionsForPrompt(question)).append("\n\n");
        }
        context.append("正确答案：").append(question.getCorrectOption()).append("\n\n");
        if (question.getExplanation() != null) {
            context.append("题目解析：").append(question.getExplanation()).append("\n\n");
        }

        context.append("【学生答题情况】\n");
        if (records.isEmpty()) {
            context.append("暂无学生答题记录。\n");
        } else {
            // 按用户分组显示所有答题记录
            Map<Long, List<AnswerRecord>> recordsByUser = records.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            AnswerRecord::getUserId,
                            java.util.stream.Collectors.toList()));

            for (Map.Entry<Long, List<AnswerRecord>> entry : recordsByUser.entrySet()) {
                List<AnswerRecord> userRecords = entry.getValue();
                if (!userRecords.isEmpty()) {
                    AnswerRecord firstRecord = userRecords.get(0);
                    User student = firstRecord.getUser();
                    String studentName = student != null ? student.getRealName() : "未知学生";

                    context.append("学生：").append(studentName).append("\n");

                    // 显示该学生的所有答题记录，按时间倒序
                    for (int i = 0; i < userRecords.size(); i++) {
                        AnswerRecord record = userRecords.get(i);
                        context.append("  第").append(i + 1).append("次答题：\n");
                        context.append("    答题时间：").append(record.getAnsweredAt()).append("\n");
                        context.append("    答案：").append(record.getUserAnswer()).append("\n");
                        context.append("    是否正确：").append(record.getCorrect() ? "正确" : "错误").append("\n");
                        context.append("    错误原因：")
                                .append(record.getErrorReason() != null ? record.getErrorReason() : "无")
                                .append("\n");
                    }
                    context.append("\n");
                }
            }
        }
        return context.toString();
    }

    /**
     * 构建教师题目分析的提示词（包含会话历史 + 当前问题）
     */
    private String buildTeacherQuestionAnalysisPrompt(AIAskRequest request, SessionContextInternalDTO context) {
        System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: buildTeacherQuestionAnalysisPrompt called");
        System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: context.getLock() is "
                + (context.getLock() == null ? "null" : "not null"));
        StringBuilder prompt = new StringBuilder();
        prompt.append(context.getContext()).append("\n\n");

        System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: About to acquire read lock");
        context.getLock().readLock().lock();
        try {
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Read lock acquired");
            Map<Integer, ConversationHistory> records = context.getInteractionRecords();
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: interactionRecords is "
                    + (records == null ? "null" : "not null, size=" + records.size()));
            if (records != null && !records.isEmpty()) {
                prompt.append("【历史对话】\n");
                records.values().forEach(history -> {
                    prompt.append("教师：").append(history.getQuestion()).append("\n");
                    prompt.append("AI：").append(history.getAnswer()).append("\n\n");
                });
            }
        } finally {
            System.out.println("DEBUG QuestionErrorAnalysisServiceImpl: Releasing read lock");
            context.getLock().readLock().unlock();
        }

        prompt.append("【教师的问题】\n").append(request.getQuestion());
        System.out.println(
                "DEBUG QuestionErrorAnalysisServiceImpl: buildTeacherQuestionAnalysisPrompt returning, prompt length="
                        + prompt.length());
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