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
import com.zhixuewuli.dto.knowledge.RetrievalResult;
import com.zhixuewuli.service.knowledge.KnowledgeMode;
import com.zhixuewuli.service.knowledge.RagPrompt;
import com.zhixuewuli.service.knowledge.RetrievalMode;
import com.zhixuewuli.service.knowledge.RetrievalStatus;
import com.zhixuewuli.service.knowledge.KnowledgeRetriever;
import com.zhixuewuli.service.knowledge.RagPromptBuilder;
import com.zhixuewuli.service.knowledge.CitationValidator;
import com.zhixuewuli.service.knowledge.AiRunHistoryService;
import com.zhixuewuli.service.knowledge.RagInteractionData;
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
    private final KnowledgeRetriever knowledgeRetriever;
    private final RagPromptBuilder ragPromptBuilder;
    private final CitationValidator citationValidator;
    private final AiRunHistoryService aiRunHistoryService;

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

        if (requireQuestionId) resolveWrongRecord(request, userId);

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
                        AnswerRecord source = resolveWrongRecord(request, userId);
                        Question snapshot = com.zhixuewuli.service.ai.report.ReportPreparation.question(source,
                                source.getQuestionSnapshot() == null ? questionRepository.findById(source.getQuestionId())
                                .map(q -> Map.of(q.getId(),q)).orElse(Map.of()) : Map.of(), objectMapper);
                        String scopedSession = java.util.UUID.nameUUIDFromBytes((sessionId + ":" + source.getId())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                        SessionContextInternalDTO sessionContext = sessionManager.getOrCreateSession(
                                scopedSession, userId, ConversationType.WRONG_QUESTION_ANALYSIS, request.getQuestionId(), source.getEventId(),
                                () -> buildWrongQuestionContext(source, snapshot));

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
                        finalRequest.setImageBase64(snapshot == null ? null : snapshot.getImageBase64());

                        // 构建多模态消息（支持图片）
                        if (request.getKnowledgeMode() == KnowledgeMode.AUTO) {
                            if (source.getQuestionSnapshot() == null && source.getContentSnapshot() == null) {
                                streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE,
                                        "该作答记录没有可验证的题目快照，暂不能使用讲义辅导");
                                return;
                            }
                            var ragQuestion=request.getQuestion();
                            var retrieval=knowledgeRetriever.retrieve(userId,source.getEventId(),ragQuestion,
                                    RetrievalMode.HYBRID,()->streamUtils.isCancelled(emitter)||sessionContext.isCancelled());
                            if(retrieval.status()!=RetrievalStatus.MATCHED) {
                                String message=retrieval.status()==RetrievalStatus.INSUFFICIENT
                                        ?"班级讲义中没有足够依据，请切回普通 AI 问答继续。"
                                        :"讲义检索暂不可用，请切回普通 AI 问答或稍后重试。";
                                try { emitter.send(SseEmitter.event().name("knowledge-status").data(Map.of("status",retrieval.status().name(),"message",message))); }
                                catch(IOException ignored) { }
                                streamUtils.sendError(emitter,ResultCode.AI_SERVICE_UNAVAILABLE,message);
                                return;
                            }
                            var ragPrompt=ragPromptBuilder.build(buildWrongQuestionContext(source,snapshot),ragQuestion,retrieval.passages(),priorConversation(sessionContext));
                            try { emitter.send(SseEmitter.event().name("sources").data(objectMapper.writeValueAsString(retrieval.passages()))); }
                            catch(IOException sendError) { streamUtils.sendError(emitter,ResultCode.NETWORK_COMMUNICATION_ERROR,"来源发送失败"); return; }
                            streamAIMultimodalResponse(buildRagAnalysisMessages(ragPrompt,finalRequest.getImageBase64()),emitter,
                                    sessionContext,ragQuestion,WrongQuestionAnalysisModel(),ragPrompt,retrieval,request.getQuestion());
                        } else {
                            List<Map<String, Object>> messages = buildAnalysisMessages(finalRequest, sessionContext);
                            streamAIMultimodalResponse(messages, emitter, sessionContext, finalRequest.getQuestion(),WrongQuestionAnalysisModel());
                        }
                    });
        });
    }

    @Override
    public List<ConversationHistory> getSessionHistory(String sessionId, Long userId) {
        // 会话存在且不属于当前用户：403；会话不存在：返回空历史
        if (!sessionManager.canAccessSession(sessionId, userId) && sessionManager.hasAnySessionRecord(sessionId)) {
            throw new InsufficientPermissionException("无权访问该会话");
        }
        return conversationHistoryRepository.findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(userId, sessionId, true)
                .stream().map(history->aiRunHistoryService.attachSources(history,userId)).toList();
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
    private AnswerRecord resolveWrongRecord(AIAskRequest request, Long userId) {
        AnswerRecord record = request.getRecordId() == null
                ? answerRecordRepository.findTopByUserIdAndQuestionIdOrderByAnsweredAtDesc(userId,request.getQuestionId())
                    .orElseThrow(() -> new AnswerRecordNotFoundException("请先提交本题作答"))
                : answerRecordRepository.findById(request.getRecordId())
                    .orElseThrow(() -> new AnswerRecordNotFoundException("作答记录不存在"));
        if (!userId.equals(record.getUserId())) throw new InsufficientPermissionException("无权分析该作答记录");
        if (!request.getQuestionId().equals(record.getQuestionId())) throw new IllegalArgumentException("题目与作答记录不匹配");
        return record;
    }

    private String buildWrongQuestionContext(Long userId, Long questionId, String sessionId) {
        AIAskRequest request = new AIAskRequest();request.setQuestionId(questionId);
        AnswerRecord record=resolveWrongRecord(request,userId);
        Question snapshot=com.zhixuewuli.service.ai.report.ReportPreparation.question(record,
                record.getQuestionSnapshot()==null ? questionRepository.findById(questionId).map(q->Map.of(questionId,q)).orElse(Map.of()) : Map.of(),objectMapper);
        return buildWrongQuestionContext(record,snapshot);
    }

    private String buildWrongQuestionContext(AnswerRecord answerRecord, Question question) {
        if(question==null) throw new IllegalStateException("历史题目不可恢复");
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
            } else {
                contextBuilder.append("用户答案：暂无答题记录\n");
            }
            return contextBuilder.toString();

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
        log.debug("实时问答输入长度: {}", textContent.length());
        
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

        textContent.append("\n【你的任务】\n下面是用户的具体问题，必须结合题目和学生实际选项情况做出针对性回答：\n").append(request.getQuestion());
        
        // 添加文本部分
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", textContent.toString());
        contentList.add(textPart);
        
        // Use the same immutable snapshot image as the text, never the current bank.
        if (request.getImageBase64()!=null && !request.getImageBase64().isBlank()) {
            String image=request.getImageBase64();
            if(!image.startsWith("data:image/")) image="data:image/png;base64,"+image;
            contentList.add(Map.of("type","image_url","image_url",Map.of("url",image)));
        }

        return List.of(Map.of("role", "user", "content", contentList));
    }

    private List<Map<String,Object>> buildRagAnalysisMessages(RagPrompt prompt,String imageBase64) {
        List<Map<String,Object>> content=new ArrayList<>();content.add(Map.of("type","text","text",prompt.userMessage()));
        if(imageBase64!=null&&!imageBase64.isBlank()) {
            String image=imageBase64.startsWith("data:image/")?imageBase64:"data:image/png;base64,"+imageBase64;
            content.add(Map.of("type","image_url","image_url",Map.of("url",image)));
        }
        return List.of(Map.of("role","system","content",prompt.systemMessage()),Map.of("role","user","content",content));
    }

    private List<ConversationHistory> priorConversation(SessionContextInternalDTO context) {
        aiRunHistoryService.refreshContext(context,context.getUserId());
        context.getLock().readLock().lock();
        try {var records=context.getInteractionRecords();return records==null?List.of():List.copyOf(records.values());}
        finally {context.getLock().readLock().unlock();}
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
                    streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI 处理失败，请稍后重试");
                }
            });

        } catch (Exception e) {
            log.error("AI流式响应处理失败", e);
            streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI处理失败，请稍后重试");
        }
    }

    /**
     * 调用智谱AI多模态流式接口（支持文本+图片混合）
     */
    private void streamAIMultimodalResponse(List<Map<String, Object>> messages, SseEmitter emitter,
            SessionContextInternalDTO sessionContext, String userQuestion, String model) {
        streamAIMultimodalResponse(messages,emitter,sessionContext,userQuestion,model,null,null,null);
    }

    private void streamAIMultimodalResponse(List<Map<String, Object>> messages, SseEmitter emitter,
            SessionContextInternalDTO sessionContext, String userQuestion, String model,
            RagPrompt ragPrompt,RetrievalResult retrieval,String originalInput) {
        try {
            long modelStarted=System.nanoTime();
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
                            String answer=fullResponse.toString();
                            if(ragPrompt!=null) {
                                var validated=citationValidator.validate(answer,retrieval.passages());answer=validated.text();
                                var ragData=new RagInteractionData(retrieval.actualMode(),cancelled?"CANCELLED":"ANSWERED",model,
                                        ragPrompt.systemMessage(),ragPrompt.userMessage(),originalInput,promptTokens,completionTokens,actualTokens,
                                        retrieval.elapsedMillis(),(System.nanoTime()-modelStarted)/1_000_000,retrieval.passages());
                                sessionManager.addRagInteraction(sessionContext,userQuestion,answer,actualTokens,ragData);
                                emitter.send(SseEmitter.event().name("citation-validation").data(objectMapper.writeValueAsString(validated.validSourceIds())));
                            } else sessionManager.addInteraction(sessionContext, userQuestion, answer, actualTokens);
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
                    streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI 处理失败，请稍后重试");
                }
            });

        } catch (Exception e) {
            log.error("AI多模态流式响应处理失败", e);
            streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI处理失败，请稍后重试");
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
