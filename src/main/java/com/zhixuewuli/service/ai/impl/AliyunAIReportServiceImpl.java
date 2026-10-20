package com.zhixuewuli.service.ai.impl;

import com.zhixuewuli.dto.response.StudentReportResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.ai.*;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.StreamContentFixer;
import com.zhixuewuli.common.util.StreamUtils;
import com.zhixuewuli.common.util.AIStreamExecutor;
import com.zhixuewuli.common.util.AIStreamExecutor.StreamOperation;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.StudentReport;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.StudentReportRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.ai.AliyunAIReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class AliyunAIReportServiceImpl implements AliyunAIReportService {

    private final com.zhixuewuli.service.ai.report.ReportInputRepository reportInputs;
    private final AliyunAIClient aliyunAIClient;
    private final StudentReportRepository studentReportRepository;
    private final UserRepository userRepository;
    private final AIStreamingProperties aiStreamingProperties;
    private final AIBusinessProperties aiBusinessProperties;
    private final StreamUtils streamUtils;
    private final ObjectMapper objectMapper;

    // 注入事务模板，用于异步线程中的数据库操作
    private final TransactionTemplate transactionTemplate;

    // 注入 AI 任务专用线程池
    private final Executor aiTaskExecutor;

    @Override
    public void validateStudentReportRequest(String sessionId, Long userId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("会话 ID 不能为空");
        }

        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        log.debug("学生报告请求验证通过 - 用户 ID: {}, 会话 ID: {}", userId, sessionId);
    }

    @Override
    public void validateTeacherAnalysisRequest(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("教师 ID 不能为空");
        }

        log.debug("教师分析请求验证通过 - 教师 ID: {}", userId);
    }

    // ========== 流式执行模板方法 ==========
    private void executeStreamOperation(String operationName, Long userId,
            SseEmitter emitter, StreamOperation operation) {
        AIStreamExecutor.executeStream(operationName, userId, emitter, streamUtils, operation);
    }

    private void sendReportError(SseEmitter emitter, Exception error, String subject) {
        if (error instanceof AITimeoutException) {
            streamUtils.sendError(emitter, ResultCode.AI_TIMEOUT, "AI" + subject + "响应超时，请稍后再生成。");
        } else if (error instanceof AIUpstreamException upstream && upstream.getStatus() == 429) {
            streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE,
                    "AI模型当前繁忙，自动重试后仍未恢复，请稍后再生成。");
        } else if (error instanceof AIUpstreamException upstream
                && (upstream.getStatus() == 401 || upstream.getStatus() == 403)) {
            streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE,
                    "AI服务认证失败，请联系管理员检查模型配置。");
        } else {
            streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE,
                    subject + "生成失败，请稍后重试");
        }
    }

    // ========== 学生报告 ==========
    @Override
    public void streamStudentReport(String sessionId, SseEmitter emitter, Long userId) {
        streamStudentReport(sessionId, emitter, userId, null);
    }

    @Override
    public void streamStudentReport(String sessionId, SseEmitter emitter, Long userId, Long eventId) {
        // 立即提交异步任务，不阻塞当前线程
        AIStreamExecutor.submit(aiTaskExecutor, userId, emitter, streamUtils, () -> {
            executeStreamOperation(
                    "学生个人报告生成",
                    userId,
                    emitter,
                    () -> {
                        AtomicBoolean cancelled = new AtomicBoolean(false);
                        emitter.onCompletion(() -> cancelled.set(true));
                        emitter.onTimeout(() -> cancelled.set(true));
                        emitter.onError(t -> cancelled.set(true));

                        // 查询该会话的答题记录
                        List<AnswerRecord> records = reportInputs.student(userId,sessionId,eventId);
                        if (records.isEmpty()) {
                            throw new AnswerRecordNotFoundException("该会话无答题记录，无法生成报告");
                        }

                        var eventIds = records.stream().map(AnswerRecord::getEventId).distinct().toList();
                        if(eventIds.size()>1) throw new IllegalArgumentException("请选择单个教学任务生成报告");
                        Long reportEventId = eventId != null ? eventId : eventIds.size() == 1 ? eventIds.getFirst() : null;
                        // 批量加载题目
                        Map<Long, Question> questionMap = Map.of();

                        String model = aiBusinessProperties.getStudentReportGenerationModel();
                        var prepared = com.zhixuewuli.service.ai.report.ReportPreparation.prepare(records, questionMap, objectMapper, false, model);
                        String prompt = prepared.prompt();
                        emitter.send(SseEmitter.event().name("evidence").data(objectMapper.writeValueAsString(prepared.evidence())));
                        emitter.send(SseEmitter.event().name("content").data(prepared.header()));

                        // 调用阿里AI客户端
                        StringBuilder fullReportContent = new StringBuilder(prepared.header());

                        aliyunAIClient.streamGenerate(prompt, model, new AIStreamCallback() {
                            @Override
                            public void onCancel(Runnable action) { streamUtils.onCancel(emitter, action); }

                @Override
                public boolean isCancelled() {
                                return streamUtils.isCancelled(emitter) || cancelled.get();
                            }

                            @Override
                            public void onContent(String content, boolean isLast) {
                                if (isCancelled()) {
                                    return;
                                }
                                try {
                                    // 使用 StreamContentFixer 修复 LaTeX/Markdown 格式问题
                                    String fixedContent = StreamContentFixer.fix(content);
                                    
                                    // 收集报告内容
                                    fullReportContent.append(fixedContent);

                                    // 发送 content 事件，事件名称为 content，内容为纯字符串
                                    emitter.send(SseEmitter.event()
                                            .name("content")
                                            .data(fixedContent));
                                } catch (IOException e) {
                                    log.error("发送学生报告内容失败", e);
                                    streamUtils.sendError(emitter, ResultCode.NETWORK_COMMUNICATION_ERROR,
                                            "SSE 发送失败：" + e.getMessage());
                                }
                            }

                            @Override
                            public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                                try {
                                    boolean cancelledNow = isCancelled();

                                    // 在 onComplete 阶段做最终持久化，避免供应商最后一个 chunk 仅包含 usage 时漏存
                                    if (fullReportContent.length() > prepared.header().length()) {
                                        log.info("学生报告流式结束，开始持久化 - 用户ID: {}, 会话ID: {}, 报告长度: {}字符",
                                                userId, sessionId, fullReportContent.length());
                                        transactionTemplate.executeWithoutResult(status -> {
                                            saveStudentReport(userId, sessionId, reportEventId, fullReportContent.toString(), objectMapper.valueToTree(prepared.evidence()).toString());
                                        });
                                    } else {
                                        log.warn("学生报告流式结束但内容为空，跳过持久化 - 用户ID: {}, 会话ID: {}",
                                                userId, sessionId);
                                    }

                                    if (cancelledNow) {
                                        log.info("学生报告任务已取消，已停止后续推送 - 用户ID: {}, 会话ID: {}", userId, sessionId);
                                        return;
                                    }

                                    // 发送完成事件，让前端能够收到 complete 回调
                                    Map<String, Object> completeData = new HashMap<>();
                                    completeData.put("promptTokens", promptTokens);
                                    completeData.put("completionTokens", completionTokens);
                                    completeData.put("totalTokens", totalTokens);
                                    emitter.send(SseEmitter.event()
                                            .name("complete")
                                            .data(objectMapper.writeValueAsString(completeData)));
                                    log.info(
                                            "学生报告生成完成 - 用户ID: {}, promptTokens: {}, completionTokens: {}, totalTokens: {}",
                                            userId, promptTokens, completionTokens, totalTokens);
                                    streamUtils.safeCompleteEmitter(emitter);
                                } catch (Exception e) {
                                    log.error("完成学生报告处理失败", e);
                                    streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "处理完成时发生错误");
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                if (isCancelled()) {
                                    log.info("学生报告任务已取消，忽略 onError 回调");
                                    return;
                                }
                                log.error("学生报告生成过程中发生错误", error);
                                sendReportError(emitter, error, "报告");
                            }
                        });
                    });
        });
    }

    // ========== 教师报告 ==========
    @Override
    public void streamTeacherAnalysis(SseEmitter emitter, Long userId) {
        streamTeacherAnalysis(emitter, userId, null);
    }

    @Override
    public void streamTeacherAnalysis(SseEmitter emitter, Long userId, Long eventId) {
        AIStreamExecutor.submit(aiTaskExecutor, userId, emitter, streamUtils, () -> {
            executeStreamOperation(
                    "教师综合分析报告生成",
                    userId,
                    emitter,
                    () -> {
                        AtomicBoolean cancelled = new AtomicBoolean(false);
                        emitter.onCompletion(() -> cancelled.set(true));
                        emitter.onTimeout(() -> cancelled.set(true));
                        emitter.onError(t -> cancelled.set(true));

                        List<AnswerRecord> allRecords = reportInputs.teacher(eventId);
                        if (allRecords.isEmpty()) {
                            throw new AnswerRecordNotFoundException("暂无任何学生答题数据，无法生成教师报告");
                        }
                        Map<Long, Question> questionMap = Map.of();
                        String model = aiBusinessProperties.getTeacherReportGenerationModel();
                        var prepared = com.zhixuewuli.service.ai.report.ReportPreparation.prepare(allRecords, questionMap, objectMapper, true, model);
                        String prompt = prepared.prompt();
                        emitter.send(SseEmitter.event().name("evidence").data(objectMapper.writeValueAsString(prepared.evidence())));
                        emitter.send(SseEmitter.event().name("content").data(prepared.header()));

                        aliyunAIClient.streamGenerate(prompt, model, new AIStreamCallback() {
                            @Override
                            public void onCancel(Runnable action) { streamUtils.onCancel(emitter, action); }

                @Override
                public boolean isCancelled() {
                                return streamUtils.isCancelled(emitter) || cancelled.get();
                            }

                            @Override
                            public void onContent(String content, boolean isLast) {
                                if (isCancelled()) {
                                    return;
                                }
                                try {
                                    // 使用 StreamContentFixer 修复 LaTeX/Markdown 格式问题
                                    String fixedContent = StreamContentFixer.fix(content);
                                    
                                    // 发送 content 事件，事件名称为 content，内容为纯字符串
                                    emitter.send(SseEmitter.event()
                                            .name("content")
                                            .data(fixedContent));
                                    if (isLast) {
                                        log.debug("教师分析内容传输完成");
                                    }
                                } catch (IOException e) {
                                    log.error("发送教师分析内容失败", e);
                                    streamUtils.sendError(emitter, ResultCode.NETWORK_COMMUNICATION_ERROR,
                                            "SSE 发送失败：" + e.getMessage());
                                }
                            }

                            @Override
                            public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                                try {
                                    if (isCancelled()) {
                                        log.info("教师报告任务已取消，停止发送完成事件 - 教师ID: {}", userId);
                                        return;
                                    }

                                    // 发送完成事件，让前端能够收到 complete 回调
                                    Map<String, Object> completeData = new HashMap<>();
                                    completeData.put("promptTokens", promptTokens);
                                    completeData.put("completionTokens", completionTokens);
                                    completeData.put("totalTokens", totalTokens);
                                    emitter.send(SseEmitter.event()
                                            .name("complete")
                                            .data(objectMapper.writeValueAsString(completeData)));
                                    log.info(
                                            "教师分析生成完成 - 教师ID: {}, promptTokens: {}, completionTokens: {}, totalTokens: {}",
                                            userId, promptTokens, completionTokens, totalTokens);
                                    streamUtils.safeCompleteEmitter(emitter);
                                } catch (Exception e) {
                                    log.error("完成教师分析处理失败", e);
                                    streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "处理完成时发生错误");
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                if (isCancelled()) {
                                    log.info("教师报告任务已取消，忽略 onError 回调");
                                    return;
                                }
                                log.error("教师分析生成过程中发生错误", error);
                                sendReportError(emitter, error, "分析");
                            }
                        });
                    });
        });
    }

    /**
     * 创建配置好的SseEmitter实例
     * 
     * 根据服务层配置创建具有适当超时时间的SseEmitter实例，
     * 实现配置与业务逻辑的完全分离。
     * 
     * @return 配置好的SseEmitter实例
     * @see SseEmitter
     */
    @Override
    public SseEmitter createConfiguredEmitter() {
        Long timeout = aiStreamingProperties.getSseEmitterTimeout();
        Long reconnectTime = aiStreamingProperties.getSseReconnectTime();

        // 使用默认值处理 null 配置
        long actualTimeout = timeout != null ? timeout : 30000L; // 默认 30 秒
        long actualReconnectTime = reconnectTime != null ? reconnectTime : 3000L; // 默认 3 秒

        log.debug("创建配置的 SseEmitter，超时时间：{}ms, 重连时间：{}ms",
                actualTimeout,
                actualReconnectTime);
        return streamUtils.createConfiguredEmitterWithLimit(
                actualTimeout,
                actualReconnectTime);
    }

    private void saveStudentReport(Long userId, String sessionId, Long eventId, String reportContent, String evidence) {
        try {
            // 查询学生信息获取真实姓名
            User student = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("学生用户不存在，无法保存报告"));

            // Each completed generation is immutable. Concurrent generations and legacy
            // duplicate scopes are safe, and provenance is never overwritten.
            StudentReport studentReport = new StudentReport();
            studentReport.setUserId(userId);
            studentReport.setRealName(student.getRealName());
            studentReport.setSessionId(sessionId);
            studentReport.setReportContent(reportContent);

            studentReport.setEventId(eventId);
            studentReport.setEvidenceJson(evidence);
            // 保存到数据库
            studentReportRepository.save(studentReport);
            log.info("学生报告持久化保存成功 - 用户ID: {}, 会话ID: {}, 报告长度: {}字符",
                    userId, sessionId, reportContent.length());

        } catch (Exception e) {
            log.error("保存学生报告失败 - 用户ID: {}, 会话ID: {}", userId, sessionId, e);
            throw new IllegalStateException("学习报告保存失败，请重试", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StudentReportResponse getStudentLatestReport(Long studentId) {
        return getStudentLatestReport(studentId, null);
    }
    @Override
    @Transactional(readOnly = true)
    public StudentReportResponse getStudentLatestReport(Long studentId, Long eventId) {

        // 2. 验证学生用户存在
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> {
                    log.warn("学生用户不存在 - 学生ID: {}", studentId);
                    return new UserNotFoundException("学生用户不存在");
                });

        // 3. 获取学生最新报告
        Optional<StudentReport> latestReportOpt = eventId == null ? studentReportRepository.findLatestByUserId(studentId) : studentReportRepository.findFirstByUserIdAndEventIdOrderByUpdatedAtDescIdDesc(studentId, eventId);

        if (latestReportOpt.isEmpty()) {
            log.warn("学生无报告记录 - 学生ID: {}", studentId);
            throw new AnswerRecordNotFoundException("该学生暂无学习报告记录");
        }

        StudentReport latestReport = latestReportOpt.get();

        // 4. 构建响应对象
        StudentReportResponse response = new StudentReportResponse();
        response.setEventId(latestReport.getEventId());
        response.setSessionId(latestReport.getSessionId());
        response.setEvidenceJson(latestReport.getEvidenceJson());
        response.setStudentRealName(student.getRealName());
        response.setLatestReportContent(latestReport.getReportContent());
        response.setReportCreateTime(latestReport.getUpdatedAt() != null ? latestReport.getUpdatedAt() : latestReport.getCreatedAt());

        log.info("成功获取学生最新报告 - 学生ID: {}, 报告创建时间: {}",
                studentId, latestReport.getCreatedAt());

        return response;
    }
}
