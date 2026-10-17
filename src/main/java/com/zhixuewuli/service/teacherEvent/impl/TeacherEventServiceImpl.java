package com.zhixuewuli.service.teacherEvent.impl;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.exception.teacherevent.*;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.TeacherEventResponse;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.repository.StudentReportRepository;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 教师事件服务实现类
 * 
 * 实现教师全局事件管理的核心业务逻辑，包括事件发布、删除和查询功能。
 * 全局事件在整个系统中唯一，所有用户共享同一个教师事件。
 * 
 * <p>
 * 核心实现要点：
 * </p>
 * <ul>
 * <li>全局事件唯一性控制</li>
 * <li>完整的事务管理支持</li>
 * <li>详细的日志记录</li>
 * <li>规范的异常处理</li>
 * <li>删除时手动清理关联数据</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TeacherEventService 教师事件服务接口
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherEventServiceImpl implements TeacherEventService {

    private final TeacherEventRepository teacherEventRepository;
    private final UserRepository userRepository;
    private final UserPermissionUtil userPermissionUtil;
    
    // 关联数据 Repository（用于删除时手动清理）

    @Override
    @Transactional(rollbackFor = { Exception.class })
    public TeacherEventResponse publishEvent(TeacherEventCreateRequest request, Long currentUserId) {
        log.info("发布全局事件 - UserId: {}", currentUserId);
    
        try {
            // 1. 权限验证：检查用户是否为教师
            userPermissionUtil.checkTeacherPermission(currentUserId);
    
            // 2. 检查全局事件是否已存在
            if (teacherEventRepository.existsByIsGlobalTrue()) {
                log.warn("全局事件已存在 - UserId: {}", currentUserId);
                throw new EventAlreadyExistsException("全局事件已存在");
            }
    
            // 3. 获取教师信息
            User teacher = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new UserNotFoundException("用户不存在"));
    
            // 4. 创建新事件
            TeacherEvent event = new TeacherEvent();
            event.setTeacherId(currentUserId);
            event.setTeacherName(teacher.getRealName());
            event.setEventTitle(request.getEventTitle());
            event.setEventDescription(request.getEventDescription());
            event.setEventTimestamp(request.getEventTimestamp());
            event.setIsGlobal(true);  // 设置为全局事件
    
            // 5. 保存事件
            TeacherEvent savedEvent = teacherEventRepository.saveAndFlush(event);
            log.info("全局事件发布成功 - EventId: {}, UserId: {}", savedEvent.getId(), currentUserId);
    
            // 6. 转换为响应 DTO
            return convertToResponse(savedEvent);
    
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new EventAlreadyExistsException("已有活动中的教学任务，请先归档");
        } catch (BusinessException e) {
            // 重新抛出业务异常，触发事务回滚
            throw e;
        } catch (Exception e) {
            log.error("发布全局事件失败 - UserId: {}", currentUserId, e);
            throw new RuntimeException("发布事件失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = { Exception.class })
    public void deleteCurrentEvent(Long currentUserId) {
        log.info("删除全局事件 - UserId: {}", currentUserId);
    
        try {
            // 1. 权限验证：检查用户是否为教师
            userPermissionUtil.checkTeacherPermission(currentUserId);
    
            // 2. 检查全局事件是否存在
            if (!teacherEventRepository.existsByIsGlobalTrue()) {
                log.warn("全局事件不存在 - UserId: {}", currentUserId);
                // 删除操作幂等：事件不存在也视为成功
                return;
            }
    
            // Ending a task preserves its learning records and frees the unique active slot.
            teacherEventRepository.findByIsGlobalTrue().ifPresent(event -> {
                event.setIsGlobal(false);
                teacherEventRepository.saveAndFlush(event);
            });

        } catch (BusinessException e) {
            // 重新抛出业务异常，触发事务回滚
            throw e;
        } catch (Exception e) {
            log.error("删除全局事件失败 - UserId: {}", currentUserId, e);
            throw new RuntimeException("删除事件失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TeacherEventResponse getGlobalEvent() {
        log.info("查询全局事件");

        try {
            // 1. 查询全局事件（不再需要权限验证）
            Optional<TeacherEvent> eventOpt = teacherEventRepository.findByIsGlobalTrue();

            if (eventOpt.isPresent()) {
                TeacherEvent event = eventOpt.get();
                log.info("找到全局事件 - EventId: {}", event.getId());
                return convertToResponse(event);
            } else {
                log.info("全局事件不存在");
                return null;
            }

        } catch (Exception e) {
            log.error("查询全局事件失败", e);
            throw new RuntimeException("查询事件失败：" + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public com.zhixuewuli.common.result.PageResult<TeacherEventResponse> getEventHistory(Long userId, int page, int size) {
        userPermissionUtil.checkTeacherPermission(userId);
        if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        var result = teacherEventRepository.findByIsGlobalFalse(
                org.springframework.data.domain.PageRequest.of(page - 1, size,
                        org.springframework.data.domain.Sort.by("updatedAt", "id").descending()));
        return com.zhixuewuli.common.result.PageResult.<TeacherEventResponse>builder()
                .records(result.map(this::convertToResponse).getContent()).total(result.getTotalElements())
                .pages(result.getTotalPages()).currentPage(page).pageSize(size).build();
    }

    /**
     * 将 TeacherEvent 实体转换为 TeacherEventResponse DTO
     * 
     * @param event 教师事件实体
     * @return TeacherEventResponse 响应 DTO
     */
    private TeacherEventResponse convertToResponse(TeacherEvent event) {
        return TeacherEventResponse.builder()
                .id(event.getId())
                .teacherId(event.getTeacherId())
                .teacherName(event.getTeacherName())
                .eventTitle(event.getEventTitle())
                .eventDescription(event.getEventDescription())
                .eventTimestamp(event.getEventTimestamp())
                .isGlobal(event.getIsGlobal())  // 添加 isGlobal 字段
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}