package com.zhixuewuli.service.teacherEvent;

import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.TeacherEventResponse;

/**
 * 教师事件服务接口
 * 
 * 定义教师全局事件管理的核心业务操作，包括事件发布、删除和查询功能。
 * 全局事件在整个系统中唯一，所有用户共享同一个教师事件。
 * 
 * <p>
 * 核心业务规则：
 * </p>
 * <ul>
 * <li>整个系统只能有一个全局事件（is_global=true）</li>
 * <li>发布新事件前必须删除已有事件</li>
 * <li>事件一旦发布不可修改，只能删除后重新发布</li>
 * <li>只有教师可以创建/删除事件，所有认证用户可查询事件</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TeacherEventCreateRequest 事件创建请求 DTO
 * @see TeacherEventResponse 事件响应 DTO
 */
public interface TeacherEventService {

    /**
     * 发布教师事件
     * 
     * 教师发布新的学习事件作为时间基准点。
     * 如果全局事件已存在，则抛出 EventAlreadyExistsException 异常。
     * 
     * @param request       事件创建请求
     * @param currentUserId 当前用户 ID（从 JWT 获取，用于验证教师身份）
     * @return TeacherEventResponse 创建的事件响应
     * @throws EventAlreadyExistsException            当全局事件已存在时抛出
     * @throws InsufficientTeacherPermissionException 当用户不是教师时抛出
     * 
     * @permission 教师权限
     * @transactional 需要事务支持
     */
    TeacherEventResponse publishEvent(TeacherEventCreateRequest request, Long currentUserId);

    /**
     * 删除当前教师的事件
     * 
     * 删除全局事件记录并清理关联数据（答案记录、对话历史、学生报告）。
     * 如果事件不存在，也视为成功（幂等操作）。
     * 
     * @param currentUserId 当前用户 ID（从 JWT 获取，用于验证教师身份）
     * @throws InsufficientTeacherPermissionException 当用户不是教师时抛出
     * 
     * @permission 教师权限
     * @transactional 需要事务支持
     */
    void deleteCurrentEvent(Long currentUserId);

    /**
     * 查询全局事件
     * 
     * 查询系统中的全局事件信息。
     * 如果事件不存在，则返回 null。
     * 
     * @return TeacherEventResponse 事件响应，如果不存在则返回 null
     */
    TeacherEventResponse getGlobalEvent();
}