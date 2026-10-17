package com.zhixuewuli.service.ai.impl;

import com.zhixuewuli.dto.response.StudentReportResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.ai.*;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.common.util.SessionUtil;
import com.zhixuewuli.common.util.StreamContentFixer;
import com.zhixuewuli.common.util.StreamUtils;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.common.util.AIStreamExecutor;
import com.zhixuewuli.common.util.AIStreamExecutor.StreamOperation;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.StudentReport;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AliyunAIReportServiceImpl implements AliyunAIReportService {

    private final AliyunAIClient aliyunAIClient;
    private final AnswerRecordRepository answerRecordRepository;
    private final QuestionRepository questionRepository;
    private final StudentReportRepository studentReportRepository;
    private final UserRepository userRepository;
    private final AIStreamingProperties aiStreamingProperties;
    private final AIBusinessProperties aiBusinessProperties;
    private final OptionMappingUtil optionMappingUtil;
    private final UserPermissionUtil userPermissionUtil;
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

        // 验证教师权限
        userPermissionUtil.checkTeacherPermission(userId);

        log.debug("教师分析请求验证通过 - 教师 ID: {}", userId);
    }

    // ========== 流式执行模板方法 ==========
    private void executeStreamOperation(String operationName, Long userId,
            SseEmitter emitter, StreamOperation operation) {
        AIStreamExecutor.executeStream(operationName, userId, emitter, streamUtils, operation);
    }

    // ========== 学生报告 ==========
    @Override
    public void streamStudentReport(String sessionId, SseEmitter emitter, Long userId) {
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
                        List<AnswerRecord> records = answerRecordRepository
                                .findByUserIdAndSessionIdOrderByAnsweredAtDesc(userId, sessionId);
                        if (records.isEmpty()) {
                            throw new AnswerRecordNotFoundException("该会话无答题记录，无法生成报告");
                        }

                        var eventIds = records.stream().map(AnswerRecord::getEventId).distinct().toList();
                        Long reportEventId = eventIds.size() == 1 ? eventIds.getFirst() : null;
                        // 批量加载题目
                        Map<Long, Question> questionMap = loadQuestionsFromRecords(records);

                        // 获取学生姓名
                        String studentName = userRepository.findById(userId)
                                .map(User::getRealName)
                                .orElse("该学生");

                        // 构建提示词
                        String prompt = buildStudentReportPrompt(records, questionMap, studentName);
                        log.info("学生报告提示词构建完成，用户: {}, 长度: {}", studentName, prompt.length());

                        // 获取学生报告专用模型
                        String model = aiBusinessProperties.getStudentReportGenerationModel();
                        log.info("使用模型: {} 生成学生报告", model);

                        // 调用阿里AI客户端
                        StringBuilder fullReportContent = new StringBuilder();

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
                                    if (fullReportContent.length() > 0) {
                                        log.info("学生报告流式结束，开始持久化 - 用户ID: {}, 会话ID: {}, 报告长度: {}字符",
                                                userId, sessionId, fullReportContent.length());
                                        transactionTemplate.executeWithoutResult(status -> {
                                            saveStudentReport(userId, sessionId, reportEventId, fullReportContent.toString());
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
                                streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE,
                                        "报告生成失败: " + error.getMessage());
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

                        List<AnswerRecord> allRecords = new ArrayList<>();
                        if (eventId != null) {
                            // Task reports must never fall back to another task's sessions.
                            allRecords.addAll(answerRecordRepository.findByEventIdOrderByAnsweredAtDesc(eventId));
                        } else {
                            // The dashboard's general report keeps its latest-session-per-student semantics.
                            for (Long uid : answerRecordRepository.findDistinctUserIds()) {
                                String latestSessionId = answerRecordRepository.findDistinctSessionIdsByUserId(uid)
                                        .stream().max(Comparator.comparing(SessionUtil::extractTimestamp)).orElse(null);
                                if (latestSessionId != null) allRecords.addAll(answerRecordRepository
                                        .findByUserIdAndSessionIdOrderByAnsweredAtDesc(uid, latestSessionId));
                            }
                        }
                        if (allRecords.isEmpty()) {
                            throw new AnswerRecordNotFoundException("暂无任何学生答题数据，无法生成教师报告");
                        }
                        List<Long> userIds = allRecords.stream().map(AnswerRecord::getUserId).distinct().toList();
                        Map<Long, String> studentNames = new HashMap<>();
                        for (Object[] row : userRepository.findIdsAndRealNamesByUserIds(userIds)) {
                            Long uid = (Long) row[0];
                            studentNames.put(uid, row[1] == null ? "学生" + uid : (String) row[1]);
                        }

                        // 批量加载题目
                        Map<Long, Question> questionMap = loadQuestionsFromRecords(allRecords);

                        // 构建提示词（传入学生姓名Map，避免懒加载问题）
                        String prompt = buildTeacherAnalysisPrompt(allRecords, questionMap, studentNames);
                        log.info("教师分析提示词构建完成，长度: {}", prompt.length());

                        // 获取教师报告专用模型
                        String model = aiBusinessProperties.getTeacherReportGenerationModel();
                        log.info("使用模型: {} 生成教师报告", model);

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
                                streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE,
                                        "分析生成失败: " + error.getMessage());
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

    // === 私有辅助方法 ===

    /**
     * 构建学生报告提示词
     * 
     * 基于会话维度重构，查找最新会话的所有答题记录，
     * 展示完整题目信息和多次答题历史，并包含每次答题的错误原因。
     * 
     */
    private String buildStudentReportPrompt(List<AnswerRecord> records, Map<Long, Question> questionMap, String studentName) {
        StringBuilder promptBuilder = new StringBuilder();

        // 按答题时间升序排列，保持题目出现的自然顺序
        List<AnswerRecord> sortedRecords = records.stream()
                .sorted(Comparator.comparing(AnswerRecord::getAnsweredAt))
                .collect(Collectors.toList());

        promptBuilder.append("请为学生「").append(studentName).append("」在本次学习中的以下答题记录生成一份学习报告：\n\n");

        // 按题目分组
        Map<Long, List<AnswerRecord>> recordsByQuestion = sortedRecords.stream()
                .collect(Collectors.groupingBy(AnswerRecord::getQuestionId));

        // 详细题目分析
        int questionIndex = 1;
        for (Map.Entry<Long, List<AnswerRecord>> entry : recordsByQuestion.entrySet()) {
            List<AnswerRecord> questionRecords = entry.getValue();
            AnswerRecord firstRecord = questionRecords.get(0);
            Question question = questionMap.get(firstRecord.getQuestionId()); // 从传入的Map获取题目
            if (question == null)
                continue;

            promptBuilder.append(questionIndex++).append(". ");
            promptBuilder.append("题目：").append(getSafeContent(question)).append("\n");
            promptBuilder.append("   选项：\n").append(formatOptionsForPrompt(question)).append("\n");
            promptBuilder.append("   正确答案：").append(getSafeCorrectOption(question)).append("\n");
            promptBuilder.append("   解析：").append(getSafeExplanation(question)).append("\n");

            // 该题目的所有答题记录
            promptBuilder.append("   答题历史：\n");
            for (AnswerRecord record : questionRecords) {
                String errorInfo = (record.getErrorReason() != null && !record.getErrorReason().isEmpty())
                        ? "，错误原因：" + record.getErrorReason()
                        : "";
                promptBuilder.append("   - 时间: ").append(formatDateTime(record.getAnsweredAt()))
                        .append(", 答案: ").append(record.getUserAnswer())
                        .append(", 结果: ").append(getAnswerStatus(record))
                        .append(errorInfo)
                        .append("\n");
            }
            promptBuilder.append("\n");
        }

        // 报告要求
        promptBuilder.append("\n请生成一份结构清晰的学习报告，包含以下内容：\n");
        promptBuilder.append("1. 本次学习表现总体评估\n");
        promptBuilder.append(
                "2. 知识掌握情况详细分析（按照各个知识点分别列出，并且重点关注错因和未掌握知识点，可以有依据地结合答题情况与学生自述，适度推理学生错误背后的真相，而不是停留于单纯对学生自述错因的摘要）\n");
        promptBuilder.append("3. 学习过程中的进步和不足（体现过程性和动态变化）\n");
        promptBuilder.append("4. 个性化针对性的学习建议和改进方向\n");
        promptBuilder.append("5. 如果学生表现好，则适当赞赏并提醒戒骄戒躁；如果学生表现一般或者表现差，则适当鼓励，但本项不是重点，不需要大量篇幅\n\n");
        promptBuilder.append("要求：在报告开头和关键建议处请直接使用「").append(studentName).append("」同学来称呼，使报告更具亲和力和针对性。语言亲切自然，对错因、知识漏洞与不足的分析归纳切中要害，建议具体可行，鼓励在回答中体现具体数据支撑。涉及公式的部分，统一使用标准规范的LaTeX格式，例如将所有Unicode箭头替换为LaTeX命令，并且确保公式边界清晰,latex命令和变量之间需要留有空格，以免识别错误，如应该写成\\cdot t而非\\cdott。");

        return promptBuilder.toString();
    }

    private String buildTeacherAnalysisPrompt(List<AnswerRecord> records, Map<Long, Question> questionMap, Map<Long, String> studentNames) {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("请基于以下学生答题数据生成一份教学分析报告：\n\n");

        // 整体统计
        long totalStudents = records.stream().map(AnswerRecord::getUserId).distinct().count();
        long totalQuestions = records.size();
        long correctAnswers = records.stream().filter(this::isAnswerCorrect).count();
        double classAccuracy = totalQuestions > 0 ? (double) correctAnswers / totalQuestions * 100 : 0;

        promptBuilder.append("整体教学统计：\n");
        promptBuilder.append("- 参与学生数：").append(totalStudents).append("人\n");
        promptBuilder.append("- 总答题数：").append(totalQuestions).append("道\n");
        promptBuilder.append("- 整体正确率：").append(String.format("%.1f", classAccuracy)).append("%\n\n");

        // 按题目分组
        Map<Long, List<AnswerRecord>> recordsByQuestion = records.stream()
                .collect(Collectors.groupingBy(AnswerRecord::getQuestionId));

        promptBuilder.append("各题目详细答题情况：\n");

        for (Map.Entry<Long, List<AnswerRecord>> entry : recordsByQuestion.entrySet()) {
            Long questionId = entry.getKey();
            List<AnswerRecord> questionRecords = entry.getValue();

            Question question = questionMap.get(questionId);
            if (question == null) {
                log.warn("题目不存在，ID: {}", questionId);
                continue;
            }

            // 题目信息
            promptBuilder.append("【题目】").append(getSafeContent(question)).append("\n");
            promptBuilder.append("选项：\n").append(formatOptionsForPrompt(question)).append("\n");
            promptBuilder.append("正确答案：").append(getSafeCorrectOption(question)).append("\n");
            promptBuilder.append("解析：").append(getSafeExplanation(question)).append("\n");

            // 该题目的答题统计
            long totalAttempts = questionRecords.size();
            long correctAttempts = questionRecords.stream().filter(this::isAnswerCorrect).count();
            double accuracy = totalAttempts > 0 ? (double) correctAttempts / totalAttempts * 100 : 0;
            promptBuilder.append("答题统计：共").append(totalAttempts).append("人次，正确").append(correctAttempts)
                    .append("人次，正确率").append(String.format("%.1f", accuracy)).append("%\n");

            // 按学生分组，列出每个学生的每次答题（按时间升序）
            Map<Long, List<AnswerRecord>> recordsByUser = questionRecords.stream()
                    .collect(Collectors.groupingBy(AnswerRecord::getUserId));

            promptBuilder.append("学生作答详情：\n");
            for (Map.Entry<Long, List<AnswerRecord>> userEntry : recordsByUser.entrySet()) {
                Long studentId = userEntry.getKey();
                List<AnswerRecord> userRecords = userEntry.getValue();

                // 获取学生姓名（从预加载的Map中获取，避免懒加载问题）
                String studentName = studentNames.getOrDefault(studentId, "学生" + studentId);

                // 对该学生的记录按答题时间升序排序
                userRecords.sort(Comparator.comparing(AnswerRecord::getAnsweredAt));

                // 列出该学生的所有答题记录（答案 + 结果 + 错误原因）
                List<String> attemptDesc = new ArrayList<>();
                for (AnswerRecord rec : userRecords) {
                    String answer = rec.getUserAnswer();
                    String result = isAnswerCorrect(rec) ? "正确" : "错误";
                    String errorInfo = (rec.getErrorReason() != null && !rec.getErrorReason().isEmpty())
                            ? "，错误原因：" + rec.getErrorReason()
                            : "";
                    attemptDesc.add(answer + "(" + result + ")" + errorInfo);
                }
                promptBuilder.append("  - ").append(studentName).append("：")
                        .append(String.join(" → ", attemptDesc)).append("\n");
            }
            promptBuilder.append("\n");
        }

        // 报告要求
        promptBuilder.append("\n请生成一份专业的高中教学分析报告，包含以下内容：\n");
        promptBuilder.append("1. 学生整体学习情况分析\n");
        promptBuilder.append("2. 各知识点掌握情况详细分析（按照各个知识点分别列出，并且重点关注发生错误较多的选项和知识点等）\n");
        promptBuilder.append("3. 学生常见错误思维模式、错误原因等分析（可以有依据地结合答题情况与学生自述，适度推理学生错误背后的真相，而不是停留于单纯对学生自述错因的摘要）\n");
        promptBuilder.append("4. 学生成绩与掌握情况在整体里的分布，以及可能的整体进步趋势\n");
        promptBuilder.append("5. 后续教学建议和针对重点\n\n");
        promptBuilder.append("要求：分析深入透彻，提炼共性问题，关注学生整体学习情况与分布，鼓励在回答中体现具体数据支撑。涉及公式的部分，统一使用标准规范的LaTeX格式，例如将所有Unicode箭头替换为LaTeX命令，并且确保公式边界清晰,latex命令和变量之间需要留有空格，以免识别错误，如应该写成\\cdot t而非\\cdott。输出表格时，分隔行必须使用标准格式，即连续的短横线，如：|---|---|---|，不能使用带空格的短横线，否则无法正确渲染");

        return promptBuilder.toString();
    }

    private Map<Long, Question> loadQuestionsFromRecords(List<AnswerRecord> records) {
        Set<Long> questionIds = records.stream()
                .map(AnswerRecord::getQuestionId)
                .collect(Collectors.toSet());
        if (questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Question> questions = questionRepository.findByIdIn(new ArrayList<>(questionIds));
        return questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
    }

    // ========== 会话处理相关辅助方法 ==========

    /**
     * 按时间排序会话ID
     * 
     * 将会话ID集合按时间戳降序排列。
     * 
     * @param sessionIds 会话ID集合
     * @return 按时间排序的会话ID列表
     */
    private List<String> sortSessionIdsByTime(Set<String> sessionIds) {
        return sessionIds.stream()
                .sorted((s1, s2) -> SessionUtil.extractTimestamp(s2).compareTo(SessionUtil.extractTimestamp(s1)))
                .collect(Collectors.toList());
    }

    // ========== 数据处理相关辅助方法 ==========

    /**
     * 添加题目分析内容
     * 
     * 为指定题目添加详细的分析内容，包括题目信息和相关答题记录。
     * 
     * @param promptBuilder 提示词构建器
     * @param question      题目对象
     * @param records       相关答题记录
     */
    private void addQuestionAnalysis(StringBuilder promptBuilder, Question question, List<AnswerRecord> records) {
        // 过滤出该题目的答题记录
        List<AnswerRecord> questionRecords = records.stream()
                .filter(record -> record.getQuestionId().equals(question.getId()))
                .collect(Collectors.toList());

        if (questionRecords.isEmpty())
            return;

        promptBuilder.append("题目：").append(getSafeContent(question)).append("\n");
        promptBuilder.append("  选项：\n").append(formatOptionsForPrompt(question)).append("\n");
        promptBuilder.append("  正确答案：").append(getSafeCorrectOption(question)).append("\n");
        promptBuilder.append("  解析：").append(getSafeExplanation(question)).append("\n");
        promptBuilder.append("  题目图片：").append(getSafeImageBase64(question)).append("\n");

        // 目前这个教师报告生成的提示词好像有点问题。按照原先的设想，因为不同学生在作答的时候，他们的会话ID是不一样的。所以教师想获取最近一次练习所有学生的作答情况，那就需要获取每个学生的最新会话信息，然后找出这一次会话里面学生错题情况，作为提示词的一部分，需要你注意的是，在一次会话当中学生是会进行多次答题的，他会答不同的题目。而且同一道题目也可能进行多次作答。然后AI上传的话，既需要知道错题的题目信息本身，也需要知道学生的作答情况，然后目前这个提示词构建函数似乎首先觉得一次会话当中的一道题目好像是有不同的学生作答，所以在统计答题统计人次正确率这种，显然是有问题。因为这个是一次会话当中同一个学生的作答情况，这种根本没必要。另外一个就是这种，以学生为单位，一个一个进行分析统计的话，如果要上传题目的详细信息，就会导致大量的题目重复造成资源浪费，因为可能不同的学生都错了相同的一道题，。但是这道题被反复上传。

        // 统计该题目的答题情况
        long totalAttempts = questionRecords.size();
        long correctAttempts = questionRecords.stream().filter(this::isAnswerCorrect).count();
        double questionAccuracy = totalAttempts > 0 ? (double) correctAttempts / totalAttempts * 100 : 0;

        promptBuilder.append("  答题统计：共").append(totalAttempts).append("人次，正确").append(correctAttempts)
                .append("人次，正确率").append(String.format("%.1f", questionAccuracy)).append("%\n\n");
    }

    /**
     * 判断答题是否正确
     * 直接使用AnswerRecord实体中的correct字段进行判断
     */
    private boolean isAnswerCorrect(AnswerRecord record) {
        // 直接使用持久化的 correct 字段
        if (record.getCorrect() != null) {
            return record.getCorrect();
        }
        // 回退逻辑（仅用于防御，实际不应触发）
        if (record.getQuestion() != null && record.getUserAnswer() != null) {
            return record.getUserAnswer().equalsIgnoreCase(record.getQuestion().getCorrectOption());
        }
        return false;
    }

    /**
     * 获取答题正确性状态字符串
     * 基于AnswerRecord实体中的correct字段判断
     */
    private String getAnswerStatus(AnswerRecord record) {
        return Boolean.TRUE.equals(record.getCorrect()) ? "正确" : "错误";
    }

    // ========== 安全处理相关辅助方法 ==========

    /**
     * 安全获取题目内容
     * 
     * 处理可能为空的题目内容，提供默认值。
     * 
     * @param question 题目对象
     * @return 题目内容或默认提示
     */
    private String getSafeContent(Question question) {
        return question != null && question.getContent() != null ? question.getContent() : "题目内容未知";
    }

    /**
     * 安全获取正确答案
     * 
     * 处理可能为空的正确答案，提供默认值。
     * 
     * @param question 题目对象
     * @return 正确答案或默认提示
     */
    private String getSafeCorrectOption(Question question) {
        return question != null && question.getCorrectOption() != null ? question.getCorrectOption() : "答案未知";
    }

    /**
     * 安全获取题目解析
     * 
     * 处理可能为空的题目解析，提供默认值。
     * 
     * @param question 题目对象
     * @return 题目解析或默认提示
     */
    private String getSafeExplanation(Question question) {
        return question != null && question.getExplanation() != null ? question.getExplanation() : "暂无解析";
    }

    /**
     * 将题目选项JSON转换为适合提示词的文本格式
     * 
     * @param question 题目实体
     * @return 格式化后的选项文本，如：
     *         A: 选项内容1
     *         B: 选项内容2
     *         C: 选项内容3
     *         D: 选项内容4
     */
    private String formatOptionsForPrompt(Question question) {
        if (question == null || question.getOptions() == null) {
            return "无选项信息";
        }
        try {
            Map<String, String> optionsMap = optionMappingUtil.jsonToOptions(question.getOptions());
            StringBuilder sb = new StringBuilder();
            // 按A、B、C、D顺序输出（工具类保证Map包含这些键）
            sb.append("A: ").append(optionsMap.get(OptionMappingUtil.OPTION_A)).append("\n");
            sb.append("B: ").append(optionsMap.get(OptionMappingUtil.OPTION_B)).append("\n");
            sb.append("C: ").append(optionsMap.get(OptionMappingUtil.OPTION_C)).append("\n");
            sb.append("D: ").append(optionsMap.get(OptionMappingUtil.OPTION_D));
            return sb.toString();
        } catch (Exception e) {
            log.warn("解析题目选项JSON失败，使用原始字符串: {}", question.getOptions(), e);
            return question.getOptions(); // 降级返回原始JSON（至少AI能看到内容）
        }
    }

    /**
     * 安全获取题目图片Base64数据
     * 
     * 处理可能为空的题目图片，提供默认值。
     * 
     * @param question 题目对象
     * @return 题目图片Base64数据或默认提示
     */
    private String getSafeImageBase64(Question question) {
        return question != null && question.getImageBase64() != null ? question.getImageBase64() : "无图片";
    }

    /**
     * 格式化时间显示
     * 
     * 将LocalDateTime格式化为易读的时间字符串。
     * 
     * @param dateTime 时间对象
     * @return 格式化后的时间字符串
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null)
            return "未知时间";
        return dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    /**
     * 保存学生报告到数据库
     * 
     * 在学生报告生成完成后，将完整的报告内容持久化保存。
     * 
     * @param userId        学生用户ID
     * @param sessionId     会话ID
     * @param reportContent 完整的报告内容
     */
    private void saveStudentReport(Long userId, String sessionId, Long eventId, String reportContent) {
        try {
            // 查询学生信息获取真实姓名
            User student = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("学生用户不存在，无法保存报告"));

            // 检查是否已存在相同会话的报告
            Optional<StudentReport> existingReport = studentReportRepository
                    .findByUserIdAndSessionIdAndEventId(userId, sessionId, eventId);

            StudentReport studentReport;
            if (existingReport.isPresent()) {
                // 如果已存在，则更新现有报告
                studentReport = existingReport.get();
                studentReport.setReportContent(reportContent);
                log.info("更新学生报告 - 用户ID: {}, 会话ID: {}", userId, sessionId);
            } else {
                // 如果不存在，则创建新报告
                studentReport = new StudentReport();
                studentReport.setUserId(userId);
                studentReport.setRealName(student.getRealName());
                studentReport.setSessionId(sessionId);
                studentReport.setReportContent(reportContent);
                log.info("创建新学生报告 - 用户ID: {}, 会话ID: {}", userId, sessionId);
            }

            studentReport.setEventId(eventId);
            // 保存到数据库
            studentReportRepository.save(studentReport);
            log.info("学生报告持久化保存成功 - 用户ID: {}, 会话ID: {}, 报告长度: {}字符",
                    userId, sessionId, reportContent.length());

        } catch (Exception e) {
            log.error("保存学生报告失败 - 用户ID: {}, 会话ID: {}", userId, sessionId, e);
            // 不抛出异常，避免影响正常的SSE流传输
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StudentReportResponse getStudentLatestReport(Long teacherId, Long studentId) {
        log.info("获取学生最新报告 - 教师ID: {}, 学生ID: {}", teacherId, studentId);

        // 1. 教师身份验证
        userPermissionUtil.checkTeacherPermission(teacherId);

        // 2. 验证学生用户存在
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> {
                    log.warn("学生用户不存在 - 学生ID: {}", studentId);
                    return new UserNotFoundException("学生用户不存在");
                });

        // 3. 获取学生最新报告
        Optional<StudentReport> latestReportOpt = studentReportRepository.findLatestByUserId(studentId);

        if (latestReportOpt.isEmpty()) {
            log.warn("学生无报告记录 - 学生ID: {}", studentId);
            throw new AnswerRecordNotFoundException("该学生暂无学习报告记录");
        }

        StudentReport latestReport = latestReportOpt.get();

        // 4. 构建响应对象
        StudentReportResponse response = new StudentReportResponse();
        response.setStudentRealName(student.getRealName());
        response.setLatestReportContent(latestReport.getReportContent());
        response.setReportCreateTime(latestReport.getCreatedAt());

        log.info("成功获取学生最新报告 - 学生ID: {}, 报告创建时间: {}",
                studentId, latestReport.getCreatedAt());

        return response;
    }
}