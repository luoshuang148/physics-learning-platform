package com.zhixuewuli.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.exception.ai.*;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.common.util.StreamContentFixer;
import com.zhixuewuli.common.util.StreamUtils;
import com.zhixuewuli.common.util.AIStreamExecutor;
import com.zhixuewuli.common.util.AIStreamExecutor.StreamOperation;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.ConversationHistory;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.ai.ZhiPuAIAssistantService;
import com.zhixuewuli.service.session.SessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * 智谱AI助手服务实现类
 * 统一处理实时问答和错题分析功能，基于会话上下文管理和流式输出。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ZhiPuAIAssistantServiceImpl implements ZhiPuAIAssistantService {

    private final QuestionRepository questionRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final ConversationHistoryRepository conversationHistoryRepository;
    private final OptionMappingUtil optionMappingUtil;
    private final ObjectMapper objectMapper;

    private final ZhipuAIClient zhipuAIClient;
    private final AIBusinessProperties aiBusinessProperties;
    private final AIStreamingProperties aiStreamingProperties;
    private final Executor aiTaskExecutor;
    private final StreamUtils streamUtils;

    // 注入会话管理器
    private final SessionManager sessionManager;

    // 从配置中读取的背景信息和模型配置
    private String ContextBackground() {
        return aiBusinessProperties.getContextBackground();
    }

    private String RealTimeQaModel() {
        return aiBusinessProperties.getRealTimeQaModel();
    }

    private String WrongQuestionAnalysisModel() {
        return aiBusinessProperties.getWrongQuestionAnalysisModel();
    }

    private String buildWrongQuestionAnalysisDefaultPrompt() {
        return "请从以下几个方面进行分析，内容精炼，清晰明了：\n"
                + "1. 错误原因分析：相比正确过程，用户可能错在何处，为什么是错的？\n"
                + "2. 思路引导：如何从题中发现破题点和切入点，实现从无思路到清晰思路的构建？\n"
                + "3. 重难点突破：对用户可能或者已经遇到的重难点进行深入细致而且易懂的讲解。\n"
                + "4. 学习建议：以后如何避免类似错误？";
    }

    @Override
    public void validateAIAskRequest(AIAskRequest request, Long userId, String sessionId, boolean requireQuestionId) {
        // 基础参数验证
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("会话 ID 不能为空");
        }

        // 问题内容验证（@Valid 已覆盖，但双重检查）
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            throw new IllegalArgumentException("问题内容不能为空");
        }

        if (request.getQuestion().length() > 800) {
            throw new IllegalArgumentException("问题内容长度不能超过 800 个字符");
        }

        // 错题分析需要 questionId
        if (requireQuestionId && request.getQuestionId() == null) {
            throw new IllegalArgumentException("错题分析需要题目 ID");
        }

        // 权限验证（根据业务需要）
        // 可以添加用户是否存在、会话所有权等验证
        log.debug("智谱 AI 请求验证通过 - 用户 ID: {}, 会话 ID: {}, 需要题目 ID: {}",
                userId, sessionId, requireQuestionId);
    }

    @Override
    public void streamQAResponse(AIAskRequest request, SseEmitter emitter, Long userId, String sessionId) {
        AIStreamExecutor.submit(aiTaskExecutor, userId, emitter, streamUtils, () -> {
            executeStreamOperation(
                    "实时问答处理",
                    sessionId,
                    userId,
                    null,
                    emitter,
                    () -> {
                        // 通过会话管理器获取或创建会话上下文
                        SessionContextInternalDTO sessionContext = sessionManager.getOrCreateSession(
                                sessionId, userId, ConversationType.REAL_TIME_QA, null,
                                this::ContextBackground
                        );

                        // 绑定上下文到 emitter，以便断连时自动取消任务
                        streamUtils.bindContext(emitter, sessionContext);

                        // 构建多模态消息（支持图片）
                        List<Map<String, Object>> messages = buildQAMessages(request, sessionContext);
                        int contentCount = 0;
                        if (!messages.isEmpty() && messages.get(0).get("content") instanceof List) {
                            contentCount = ((List<?>)messages.get(0).get("content")).size();
                        }
                        log.debug("实时问答多模态消息构建完成，包含 {} 个内容块", contentCount);

                        streamAIMultimodalResponse(messages, emitter, sessionContext, request.getQuestion(), RealTimeQaModel());
                    });
        });
    }

    @Override
    public void streamWrongQuestionAnalysis(AIAskRequest request, SseEmitter emitter,
            Long userId, String sessionId) {
        AIStreamExecutor.submit(aiTaskExecutor, userId, emitter, streamUtils, () -> {
            executeStreamOperation(
                    "错题分析处理",
                    sessionId,
                    userId,
                    request.getQuestionId(),
                    emitter,
                    () -> {
                        // 通过会话管理器获取或创建会话上下文
                        SessionContextInternalDTO sessionContext = sessionManager.getOrCreateSession(
                                sessionId, userId, ConversationType.WRONG_QUESTION_ANALYSIS, request.getQuestionId(),
                                () -> buildWrongQuestionContext(userId, request.getQuestionId(), sessionId));

                        // 绑定上下文到 emitter，以便断连时自动取消任务
                        streamUtils.bindContext(emitter, sessionContext);

                        boolean isFirstInteraction = sessionContext.getInteractionRecords() == null
                                || sessionContext.getInteractionRecords().isEmpty();

                        AIAskRequest finalRequest;
                        if (isFirstInteraction) {
                            finalRequest = new AIAskRequest();
                            finalRequest.setQuestionId(request.getQuestionId());
                            finalRequest.setQuestion(buildWrongQuestionAnalysisDefaultPrompt());
                            log.info("错题分析首次交互，使用默认提示词");
                        } else {
                            finalRequest = request;
                            log.debug("错题分析非首次交互，使用原始问题");
                        }

                        // 构建多模态消息（支持图片）
                        List<Map<String, Object>> messages = buildAnalysisMessages(finalRequest, sessionContext);

                        streamAIMultimodalResponse(messages, emitter, sessionContext, finalRequest.getQuestion(),
                                WrongQuestionAnalysisModel());
                    });
        });
    }

    @Override
    public List<ConversationHistory> getSessionHistory(String sessionId, Long userId) {
        // 会话存在且不属于当前用户：403；会话不存在：返回空历史
        if (!sessionManager.canAccessSession(sessionId, userId) && sessionManager.hasAnySessionRecord(sessionId)) {
            throw new InsufficientPermissionException("无权访问该会话");
        }
        return conversationHistoryRepository.findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(userId, sessionId, true);
    }

    @Override
    public void clearSession(String sessionId, Long userId) {
        // 直接调用会话管理器的清除方法
        sessionManager.clearSession(sessionId, userId);
    }

    @Override
    public SseEmitter createConfiguredEmitter() {
        return streamUtils.createConfiguredEmitterWithLimit(
                aiStreamingProperties.getSseEmitterTimeout(),
                aiStreamingProperties.getSseReconnectTime());
    }

    /**
     * 定时清理过期会话
     * 委托给会话管理器执行
     */
    @Scheduled(fixedDelayString = "#{@aiBusinessProperties.cleanupInterval.toMillis()}")
    public void cleanupExpiredSessions() {
        sessionManager.cleanupExpiredSessions(aiBusinessProperties.getSessionExpiration());
    }

    // ========== 流式执行模板方法 ==========
    private void executeStreamOperation(String operationName, String sessionId, Long userId,
            Long questionId, SseEmitter emitter, StreamOperation operation) {
        log.info("{}开始 - 用户ID: {}, 会话ID: {}, 题目ID: {}", operationName, userId, sessionId, questionId);
        AIStreamExecutor.executeStream(operationName, userId, emitter, streamUtils, operation);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 构建错题分析上下文
     */
    private String buildWrongQuestionContext(Long userId, Long questionId, String sessionId) {
        try {
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(
                            () -> new com.zhixuewuli.common.exception.question.QuestionNotFoundException(questionId));
            AnswerRecord answerRecord = answerRecordRepository
                    .findTopByUserIdAndQuestionIdOrderByAnsweredAtDesc(userId, questionId)
                    .orElse(null);
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append(ContextBackground()).append("\n\n");
            contextBuilder.append("我们正在讨论下面的题目，请帮助学生解决困惑并掌握相关知识：\n\n");
            contextBuilder.append("【题目信息】\n");
            contextBuilder.append("题目内容：").append(question.getContent()).append("\n\n");
            if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                contextBuilder.append("题目选项：\n");
                try {
                    Map<String, String> optionsMap = optionMappingUtil.jsonToOptions(question.getOptions());
                    contextBuilder.append("A: ").append(optionsMap.get(OptionMappingUtil.OPTION_A)).append("\n");
                    contextBuilder.append("B: ").append(optionsMap.get(OptionMappingUtil.OPTION_B)).append("\n");
                    contextBuilder.append("C: ").append(optionsMap.get(OptionMappingUtil.OPTION_C)).append("\n");
                    contextBuilder.append("D: ").append(optionsMap.get(OptionMappingUtil.OPTION_D)).append("\n");
                } catch (Exception e) {
                    log.warn("解析题目选项JSON失败: {}", question.getOptions(), e);
                    contextBuilder.append("选项解析失败\n");
                }
                contextBuilder.append("\n");
            }
            contextBuilder.append("参考答案：选项 ").append(question.getCorrectOption()).append("\n");
            if (question.getExplanation() != null) {
                contextBuilder.append("答案解析：").append(question.getExplanation()).append("\n");
            }
            contextBuilder.append("\n【学生答题情况】\n");
            if (answerRecord != null) {
                contextBuilder.append("学生选择的答案是：").append(answerRecord.getUserAnswer()).append("\n");
                if (answerRecord.getErrorReason() != null) {
                    contextBuilder.append("学生自述自己错误原因：").append(answerRecord.getErrorReason()).append("\n");
                }
            } else {
                contextBuilder.append("用户答案：暂无答题记录\n");
            }
            return contextBuilder.toString();
        } catch (Exception e) {
            log.error("构建错题分析上下文失败 - 用户ID: {}, 题目ID: {}", userId, questionId, e);
            return ContextBackground();
        }
    }
   
    /**
     * 构建实时问答的多模态消息列表（支持图片）
     */
    private List<Map<String, Object>> buildQAMessages(AIAskRequest request, SessionContextInternalDTO context) {
        List<Map<String, Object>> contentList = new ArrayList<>();
        
        // 1. 添加文本内容
        StringBuilder textContent = new StringBuilder();
        textContent.append(context.getContext()).append("\n\n");
        
        context.getLock().readLock().lock();
        try {
            Map<Integer, ConversationHistory> records = context.getInteractionRecords();
            if (records != null && !records.isEmpty()) {
                textContent.append("\n【相关对话历史】\n");
                for (ConversationHistory history : records.values()) {
                    textContent.append("用户：").append(history.getQuestion()).append("\n");
                    textContent.append("AI助手：").append(history.getAnswer()).append("\n\n");
                }
            }
        } finally {
            context.getLock().readLock().unlock();
        }
        textContent.append("\n【你的任务：回答下面用户的具体问题】\n").append(request.getQuestion());
        
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", textContent.toString());
        contentList.add(textPart);
        
        // 2. 如果有图片，添加图片部分
        if (request.getImageBase64() != null && !request.getImageBase64().trim().isEmpty()) {
            String imageData = request.getImageBase64().trim();
            
            // 自动检测并补充图片前缀
            if (!imageData.startsWith("data:image/")) {
                imageData = "data:image/png;base64," + imageData;
                log.debug("自动添加图片前缀");
            }
            
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            Map<String, String> imageUrl = new HashMap<>();
            imageUrl.put("url", imageData);
            imagePart.put("image_url", imageUrl);
            contentList.add(imagePart);
            log.debug("添加图片到消息，Base64长度: {}", imageData.length());
        }
        
        // 3. 构建用户消息
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", contentList);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage);
        
        // 记录最终提示词（不含图片）
        log.info("========== 实时问答最终提示词 ==========\n{}\n=========================================", textContent);
        
        return messages;
    }

    /**
     * 构建错题分析的多模态消息列表（支持图片）
     */
    private List<Map<String, Object>> buildAnalysisMessages(AIAskRequest request, SessionContextInternalDTO context) {
        List<Map<String, Object>> contentList = new ArrayList<>();
        
        StringBuilder textContent = new StringBuilder();
        textContent.append(context.getContext()).append("\n\n");
        
        context.getLock().readLock().lock();
        try {
            Map<Integer, ConversationHistory> records = context.getInteractionRecords();
            if (records != null && !records.isEmpty()) {
                textContent.append("\n【相关对话历史】\n");
                for (ConversationHistory history : records.values()) {
                    textContent.append("用户：").append(history.getQuestion()).append("\n");
                    textContent.append("AI助手：").append(history.getAnswer()).append("\n\n");
                }
            }
        } finally {
            context.getLock().readLock().unlock();
        }

        textContent.append("\n【你的任务】\n下面是用户的具体问题，必须尽可能结合学生实际选项情况与学生自述错因做出针对性回答：\n").append(request.getQuestion());
        
        // 添加文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", textContent.toString());
        contentList.add(textPart);
        
        // 如果有题目图片，添加图片部分
        if (context.getQuestionId() != null) {
            questionRepository.findById(context.getQuestionId())
                    .ifPresent(question -> {
                        if (question.getImageBase64() != null && !question.getImageBase64().isEmpty()) {
                            String imageData = question.getImageBase64();
                            if (!imageData.startsWith("data:image/")) {
                                imageData = "data:image/png;base64," + imageData;
                            }
                            
                            Map<String, Object> imagePart = new HashMap<>();
                            imagePart.put("type", "image_url");
                            Map<String, String> imageUrl = new HashMap<>();
                            imageUrl.put("url", imageData);
                            imagePart.put("image_url", imageUrl);
                            contentList.add(imagePart);
                            log.debug("添加题目图片到消息，Base64长度: {}", imageData.length());
                        }
                    });
        }
        
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", contentList);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage);
        
        // 记录最终提示词（不含图片）
        log.info("========== 错题分析最终提示词 ==========\n{}\n=========================================", textContent);
        
        return messages;
    }

    /**
     * @deprecated 该方法已废弃，不再被调用。请使用 streamAIMultimodalResponse() 调用智谱AI多模态接口，
     *             支持文本和图片的混合消息格式。
     */
    @Deprecated
    private void streamAIResponse(String prompt, SseEmitter emitter,
            SessionContextInternalDTO sessionContext, String userQuestion, String model) {
        try {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder fullReasoning = new StringBuilder();

            zhipuAIClient.streamChat(prompt, model, new AIStreamCallback() {
                @Override
                public void onContent(String content, boolean isLast) {
                    try {
                        // 使用 StreamContentFixer 修复 LaTeX/Markdown 格式问题
                        String fixedContent = StreamContentFixer.fix(content);
                        
                        // 发送 content 事件，事件名称为 content，内容为纯字符串
                        emitter.send(SseEmitter.event()
                                .name("content")
                                .data(fixedContent));
                        fullResponse.append(fixedContent);
                    } catch (IOException e) {
                        log.error("发送SSE数据失败", e);
                        streamUtils.sendError(emitter, ResultCode.NETWORK_COMMUNICATION_ERROR,
                                "SSE 发送失败：" + e.getMessage());
                    }
                }

                @Override
                public void onReasoningContent(String reasoning) {
                    if (Boolean.TRUE.equals(aiBusinessProperties.getZhipuEnableReasoning())) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("reasoning")
                                    .data(reasoning));
                            fullReasoning.append(reasoning);
                        } catch (IOException e) {
                            log.error("发送reasoning数据失败", e);
                        } catch (IllegalStateException e) {
                            // 防御性处理：客户端可能已断开连接
                            log.warn("reasoning发送失败，emitter已关闭: {}", e.getMessage());
                        }
                    }
                }

                @Override
                public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                    try {
                        // 处理 token 统计：如果 AI 未返回，则使用估算值
                        int actualTokens = totalTokens;
                        if (actualTokens == 0 && prompt != null && fullResponse.length() > 0) {
                            actualTokens = estimateTokenCount(prompt) + estimateTokenCount(fullResponse.toString());
                            log.debug("使用估算 token 用量: {}", actualTokens);
                        }

                        // 使用会话管理器保存对话历史（传入实际 token 数）
                        sessionManager.addInteraction(sessionContext, userQuestion,
                                fullResponse.toString(), actualTokens);

                        // 更新 token 用量
                        sessionManager.updateTokenUsage(sessionContext, actualTokens);

                        // 发送完成事件
                        Map<String, Object> completeData = new HashMap<>();
                        completeData.put("promptTokens", promptTokens);
                        completeData.put("completionTokens", completionTokens);
                        completeData.put("totalTokens", actualTokens); // 这里也可以选择保留原始 totalTokens，但展示估算值更合理
                        completeData.put("reasoningLength", fullReasoning.length());

                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(objectMapper.writeValueAsString(completeData)));

                        log.info("AI响应完成 - 会话ID: {}, 响应长度: {}, 思考长度: {}, token消耗: {}",
                                sessionContext.getSessionId(), fullResponse.length(),
                                fullReasoning.length(), actualTokens);

                        streamUtils.safeCompleteEmitter(emitter);
                    } catch (Exception e) {
                        log.error("完成处理失败", e);
                        streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "处理完成时发生错误");
                    }
                }

                @Override
                public void onError(Exception error) {
                    log.error("AI流式响应处理失败", error);
                    streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI 处理失败：" + error.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("AI流式响应处理失败", e);
            streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI处理失败: " + e.getMessage());
        }
    }

    /**
     * 调用智谱AI多模态流式接口（支持文本+图片混合）
     */
    private void streamAIMultimodalResponse(List<Map<String, Object>> messages, SseEmitter emitter,
            SessionContextInternalDTO sessionContext, String userQuestion, String model) {
        try {
            StringBuilder fullResponse = new StringBuilder();
            StringBuilder fullReasoning = new StringBuilder();

            zhipuAIClient.streamChatWithMessages(messages, model, new AIStreamCallback() {
                @Override
                public void onCancel(Runnable action) { streamUtils.onCancel(emitter, action); }

                @Override
                public boolean isCancelled() {
                    return streamUtils.isCancelled(emitter) || (sessionContext != null && sessionContext.isCancelled());
                }

                @Override
                public void onContent(String content, boolean isLast) {
                    if (isCancelled()) return;
                    try {
                        String fixedContent = StreamContentFixer.fix(content);
                        emitter.send(SseEmitter.event()
                                .name("content")
                                .data(fixedContent));
                        fullResponse.append(fixedContent);
                    } catch (IOException e) {
                        log.error("发送SSE数据失败", e);
                        streamUtils.sendError(emitter, ResultCode.NETWORK_COMMUNICATION_ERROR,
                                "SSE 发送失败：" + e.getMessage());
                    }
                }

                @Override
                public void onReasoningContent(String reasoning) {
                    if (isCancelled()) return;
                    if (Boolean.TRUE.equals(aiBusinessProperties.getZhipuEnableReasoning())) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("reasoning")
                                    .data(reasoning));
                            fullReasoning.append(reasoning);
                        } catch (IOException e) {
                            log.error("发送reasoning数据失败", e);
                        } catch (IllegalStateException e) {
                            // 防御性处理：客户端可能已断开连接
                            log.warn("reasoning发送失败，emitter已关闭: {}", e.getMessage());
                        }
                    }
                }

                @Override
                public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                    try {
                        boolean cancelled = isCancelled();
                        int actualTokens = totalTokens;
                        if (actualTokens == 0 && fullResponse.length() > 0) {
                            actualTokens = estimateTokenCount(messages.toString()) + estimateTokenCount(fullResponse.toString());
                            log.debug("使用估算 token 用量: {}", actualTokens);
                        }

                        if (fullResponse.length() > 0) {
                            sessionManager.addInteraction(sessionContext, userQuestion, fullResponse.toString(), actualTokens);
                            sessionManager.updateTokenUsage(sessionContext, actualTokens);
                        }

                        if (cancelled) {
                            log.info("任务已取消，已保存部分响应内容: session={}, responseLength={}",
                                    sessionContext.getSessionId(), fullResponse.length());
                            return;
                        }

                        Map<String, Object> completeData = new HashMap<>();
                        completeData.put("promptTokens", promptTokens);
                        completeData.put("completionTokens", completionTokens);
                        completeData.put("totalTokens", actualTokens);
                        completeData.put("reasoningLength", fullReasoning.length());

                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(objectMapper.writeValueAsString(completeData)));

                        log.info("AI多模态响应完成 - 会话ID: {}, 响应长度: {}, token消耗: {}",
                                sessionContext.getSessionId(), fullResponse.length(), actualTokens);

                        streamUtils.safeCompleteEmitter(emitter);
                    } catch (Exception e) {
                        log.error("完成处理失败", e);
                        streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "处理完成时发生错误");
                    }
                }

                @Override
                public void onError(Exception error) {
                    log.error("AI多模态流式响应处理失败", error);
                    streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI 处理失败：" + error.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("AI多模态流式响应处理失败", e);
            streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI处理失败: " + e.getMessage());
        }
    }

    /**
     * 简单估计token数量
     */
    private Integer estimateTokenCount(String text) {
        try {
            if (text == null)
                return 0;
            return text.length() * 2;
        } catch (Exception e) {
            log.error("Token计算异常，返回0", e);
            return 0;
        }
    }
}