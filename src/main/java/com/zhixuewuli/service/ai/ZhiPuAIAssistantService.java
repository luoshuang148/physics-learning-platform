package com.zhixuewuli.service.ai;

import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.entity.ConversationHistory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智谱AI助手服务接口
 * 
 * 专门处理基于智谱AI的实时问答和错题分析功能，
 * 统一管理会话上下文和流式响应处理。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>实时问答流式处理</li>
 * <li>错题深度分析</li>
 * <li>会话上下文管理</li>
 * <li>对话历史维护</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用SessionContextInternalDTO统一管理会话状态</li>
 * <li>所有AI调用均采用流式输出模式</li>
 * <li>与ConversationHistory实体紧密集成</li>
 * <li>支持会话生命周期管理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see SessionContextInternalDTO 会话上下文管理DTO
 * @see ConversationHistory 对话历史实体
 */
public interface ZhiPuAIAssistantService {

        /**
         * 验证AI问答请求参数
         * 
         * @param request           AI问答请求对象
         * @param userId            用户 ID
         * @param sessionId         会话 ID
         * @param requireQuestionId 是否要求 questionId 非空（错题分析需要，实时问答不需要）
         * @throws IllegalArgumentException        当参数不合法时抛出
         * @throws InsufficientPermissionException 当权限不足时抛出
         */
        void validateAIAskRequest(AIAskRequest request, Long userId, String sessionId, boolean requireQuestionId);

        /**
         * 实时问答流式响应
         * 
         * 处理用户的实时问答请求，通过 SSE 流式方式返回 AI 响应，
         * 自动管理会话上下文和对话历史。
         * 
         * @param request   AI问答请求对象，包含问题内容和可选的图片信息
         * @param emitter   SSE 发射器
         * @param userId    用户 ID（从拦截器获取）
         * @param sessionId 会话 ID（从拦截器获取）
         * @throws IllegalArgumentException 当参数不合法时抛出
         * @throws RuntimeException         当 AI 服务调用失败时抛出
         */
        void streamQAResponse(AIAskRequest request, SseEmitter emitter, Long userId, String sessionId);

        /**
         * 错题分析流式响应
         * 
         * 对学生的错题进行深度分析，提供错误原因解释、
         * 知识点梳理和学习建议，通过流式方式返回。
         * 
         * @param request   AI问答请求对象，包含问题内容、题目ID和可选的图片信息
         * @param emitter   SSE发射器
         * @param userId    用户ID（从拦截器获取）
         * @param sessionId 会话ID（从拦截器获取）
         * @throws IllegalArgumentException 当参数不合法时抛出
         * @throws RuntimeException         当AI服务调用失败时抛出
         */
        void streamWrongQuestionAnalysis(AIAskRequest request, SseEmitter emitter,
                        Long userId, String sessionId);

        /**
         * 获取会话历史记录，需验证会话所有权
         * 
         * @param sessionId 会话ID（已由拦截器处理）
         * @param userId    当前用户ID（已由拦截器设置）
         */
        List<ConversationHistory> getSessionHistory(String sessionId, Long userId);

        /**
         * 清除会话，需验证会话所有权
         * 
         * @param sessionId 会话ID
         * @param userId    当前用户ID
         */
        void clearSession(String sessionId, Long userId);

        /**
         * 创建配置好的SseEmitter实例
         * 
         * 根据服务层配置创建具有适当超时时间的SseEmitter实例，
         * 实现配置与业务逻辑的完全分离。
         * 
         * @return 配置好的SseEmitter实例
         * @see SseEmitter
         */
        SseEmitter createConfiguredEmitter();

        /**
         * 定时清理过期会话
         * 
         * 定期扫描并清理过期的会话数据，释放内存资源。
         * 该方法由Spring定时任务框架自动调用。
         */
        void cleanupExpiredSessions();
}