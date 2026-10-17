package com.zhixuewuli.service.ai.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.util.StreamUtils;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI服务同步测试基类
 * 
 * 为所有AI服务的同步逻辑测试提供统一的基础框架。
 * 确保在同步测试中AI客户端绝不会被实际调用。
 * 
 * <p>
 * 核心职责：
 * </p>
 * <ul>
 * <li>提供公共的Mock对象声明</li>
 * <li>确保AI客户端在同步测试中零调用</li>
 * <li>提供通用的测试数据构建方法</li>
 * <li>维护测试一致性和可维护性</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("ai-sync")
public abstract class AIServiceSyncTestBase extends BaseUnitTest {

    // ========== 公共Mock对象 ==========

    @Mock
    protected ZhipuAIClient zhipuAIClient;

    @Mock
    protected AliyunAIClient aliyunAIClient;

    @Mock
    protected SessionManager sessionManager;

    @Mock
    protected AIBusinessProperties aiBusinessProperties;

    @Mock
    protected AIStreamingProperties aiStreamingProperties;

    @Mock
    protected StreamUtils streamUtils;

    // ========== 测试数据 ==========

    protected static final Long TEST_USER_ID = 1001L;
    protected static final String TEST_SESSION_ID = "test-session-123";
    protected static final Long TEST_QUESTION_ID = 2001L;
    protected static final String TEST_QUESTION_CONTENT = "什么是牛顿第二定律？";
    protected static final String TEST_IMAGE_BASE64 = "data:image/png;base64,test-image-data";

    protected AIAskRequest validRequest;

    @BeforeEach
    void setUpAIServiceSyncTestBase() {
        // 初始化标准测试请求
        validRequest = new AIAskRequest();
        validRequest.setQuestion(TEST_QUESTION_CONTENT);
        validRequest.setImageBase64(TEST_IMAGE_BASE64);

        // 配置默认的AI属性
        setupDefaultAIProperties();
    }

    /**
     * 配置默认的AI业务属性
     */
    private void setupDefaultAIProperties() {
        // 默认配置，可在具体测试中覆盖
        // 这些配置主要用于测试数据构建，不是为了触发AI调用
    }

    // ========== 关键验证方法 ==========

    /**
     * 确保AI客户端在同步测试中绝对不被调用
     * 这是同步测试的核心原则
     */
    protected void ensureAIClientNeverCalled() {
        verify(zhipuAIClient, never()).streamChat(any(), any(), any());
        verify(aliyunAIClient, never()).streamGenerate(any(), any(), any());
    }

    // ========== 公共测试数据构建方法 ==========

    /**
     * 创建基本的AI请求对象
     * 
     * @param question    问题内容
     * @param questionId  题目ID（可选）
     * @param imageBase64 图片数据（可选）
     * @return AIAskRequest对象
     */
    protected AIAskRequest createBasicAIRequest(String question, Long questionId, String imageBase64) {
        AIAskRequest request = new AIAskRequest();
        request.setQuestion(question);
        request.setQuestionId(questionId);
        if (imageBase64 != null) {
            request.setImageBase64(imageBase64);
        }
        return request;
    }

    /**
     * 创建会话上下文对象
     * 
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @param context   上下文内容
     * @return SessionContextInternalDTO对象
     */
    protected SessionContextInternalDTO createSessionContext(String sessionId, Long userId, String context) {
        SessionContextInternalDTO dto = SessionContextInternalDTO.builder()
                .sessionId(sessionId)
                .userId(userId)
                .context(context)
                .maxTokens(1000000)
                .usedTokens(new java.util.concurrent.atomic.AtomicInteger(0))
                .isActive(true)
                .maxInteractionCount(15)
                .interactionRecords(new java.util.concurrent.ConcurrentSkipListMap<>())
                .totalInteractionCount(0)
                .build();

        // 确保lock字段不为null（@Builder.Default可能不工作）
        try {
            java.lang.reflect.Field lockField = com.zhixuewuli.dto.internal.SessionContextInternalDTO.class
                    .getDeclaredField("lock");
            lockField.setAccessible(true);
            if (lockField.get(dto) == null) {
                lockField.set(dto, new java.util.concurrent.locks.ReentrantReadWriteLock());
            }
        } catch (Exception e) {
            // 忽略反射异常
        }

        return dto;
    }

    /**
     * 创建测试用的答题记录列表
     * 
     * @param count     记录数量
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 答题记录列表
     */
    protected List<Object[]> createTestAnswerRecords(int count, String sessionId, Long userId) {
        // 这里返回Object[]数组是为了模拟JPA查询结果
        // 实际使用时需要根据具体查询结构调整
        return java.util.Collections.nCopies(count, new Object[] {
                sessionId, // session_id
                userId, // user_id
                TEST_QUESTION_ID, // question_id
                "学生答案A", // student_answer
                true, // is_correct
                java.time.LocalDateTime.now() // answered_at
        });
    }

    /**
     * 创建带错题分析上下文的AI请求
     * 
     * @param question   问题内容
     * @param questionId 题目ID
     * @return AIAskRequest对象
     */
    protected AIAskRequest createWrongQuestionAnalysisRequest(String question, Long questionId) {
        return createBasicAIRequest(question, questionId, null);
    }

    /**
     * 创建实时问答的AI请求
     * 
     * @param question 问题内容
     * @return AIAskRequest对象
     */
    protected AIAskRequest createRealTimeQARequest(String question) {
        return createBasicAIRequest(question, null, TEST_IMAGE_BASE64);
    }
}