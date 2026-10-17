package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.common.factory.IntegrationTestDataFactory;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 教师事件仓库集成测试
 * 验证 TeacherEventRepository 的数据库操作和自定义查询方法
 */
@Tag("integration-repository")
class TeacherEventRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TeacherEventRepository teacherEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    // ==================== 基础 CRUD 测试 ====================

    @Test
    @Rollback
    void testSaveAndFindTeacherEvent() {
        // Given - 创建教师用户
        User teacher = createTeacherUser("teacher_event_test", "测试教师");

        TeacherEvent event = new TeacherEvent();
        event.setTeacherId(teacher.getId());
        event.setTeacherName(teacher.getRealName());
        event.setEventTitle("发布新课");
        event.setEventDescription("第三章：机械能守恒定律");
        event.setEventTimestamp(LocalDateTime.now());

        // When
        TeacherEvent savedEvent = teacherEventRepository.save(event);
        Optional<TeacherEvent> foundEvent = teacherEventRepository.findById(savedEvent.getId());

        // Then
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getTeacherId()).isEqualTo(teacher.getId());
        assertThat(foundEvent.get().getTeacherName()).isEqualTo("测试教师");
        assertThat(foundEvent.get().getEventTitle()).isEqualTo("发布新课");
        assertThat(foundEvent.get().getEventDescription()).contains("机械能守恒定律");
    }

    // ==================== 基于教师 ID 的查询测试 ====================

    @Test
    @Rollback
    void testFindByTeacherId() {
        // Given - 创建两个教师，每个都有事件
        cleanTable("TEACHER_EVENTS");
        cleanTable("USERS");

        User teacher1 = createTeacherUser("teacher1_find", "教师 1");
        User teacher2 = createTeacherUser("teacher2_find", "教师 2");

        TeacherEvent event1 = createTeacherEvent(teacher1, "事件 1", "事件描述 1");
        createTeacherEvent(teacher2, "事件 2", "事件描述 2");

        // When - 查找教师 1 的事件
        Optional<TeacherEvent> foundEvent = teacherEventRepository.findByTeacherId(teacher1.getId());

        // Then - 验证只返回教师 1 的事件
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getId()).isEqualTo(event1.getId());
        assertThat(foundEvent.get().getTeacherId()).isEqualTo(teacher1.getId());
        assertThat(foundEvent.get().getEventTitle()).isEqualTo("事件 1");
    }

    @Test
    @Rollback
    void testFindByTeacherIdWhenNotExists() {
        // Given - 创建教师但不创建事件
        User teacher = createTeacherUser("teacher_no_event", "无事件教师");

        // When
        Optional<TeacherEvent> foundEvent = teacherEventRepository.findByTeacherId(teacher.getId());

        // Then - 验证返回空
        assertThat(foundEvent).isEmpty();
    }

    @Test
    @Rollback
    void testFindByTeacherIdReturnsLatest() {
        // Given - 创建教师并创建单个事件（业务规则：每个教师只能有一个事件）
        cleanTable("TEACHER_EVENTS");
        cleanTable("USERS");

        User teacher = createTeacherUser("teacher_single_event", "单事件教师");

        // 创建唯一的事件
        TeacherEvent event = new TeacherEvent();
        event.setTeacherId(teacher.getId());
        event.setTeacherName(teacher.getRealName());
        event.setEventTitle("测试事件");
        event.setEventDescription("测试描述");
        event.setEventTimestamp(LocalDateTime.now());
        teacherEventRepository.save(event);

        // When - 查找该教师的事件
        Optional<TeacherEvent> foundEvent = teacherEventRepository.findByTeacherId(teacher.getId());

        // Then - 验证能找到该事件
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getTeacherId()).isEqualTo(teacher.getId());
        assertThat(foundEvent.get().getEventTitle()).isEqualTo("测试事件");
    }

    // ==================== 存在性检查测试 ====================

    @Test
    @Rollback
    void testExistsByTeacherId() {
        // Given - 创建有事件的教师
        User teacher = createTeacherUser("teacher_exists", "存在事件教师");
        createTeacherEvent(teacher, "测试事件", "测试描述");

        // When
        boolean exists = teacherEventRepository.existsByTeacherId(teacher.getId());
        boolean notExists = teacherEventRepository.existsByTeacherId(99999L);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testExistsByTeacherIdAfterDelete() {
        // Given - 创建有事件的教师
        User teacher = createTeacherUser("teacher_delete_exists", "删除后检查教师");
        TeacherEvent event = createTeacherEvent(teacher, "待删除事件", "待删除描述");

        // When - 删除事件
        teacherEventRepository.deleteById(event.getId());

        // Then - 验证不再存在
        boolean exists = teacherEventRepository.existsByTeacherId(teacher.getId());
        assertThat(exists).isFalse();
    }

    // ==================== 删除操作测试 ====================

    @Test
    @Rollback
    void testDeleteByTeacherId() {
        // Given - 创建有事件的教师
        cleanTable("TEACHER_EVENTS");
        cleanTable("USERS");

        User teacher1 = createTeacherUser("teacher_delete1", "删除教师 1");
        User teacher2 = createTeacherUser("teacher_delete2", "删除教师 2");

        // 验证两个教师有不同的 ID
        assertThat(teacher1.getId()).isNotEqualTo(teacher2.getId());

        TeacherEvent event1 = createTeacherEvent(teacher1, "教师 1 事件", "描述 1");
        TeacherEvent event2 = createTeacherEvent(teacher2, "教师 2 事件", "描述 2");

        // When - 删除教师 1 的事件
        int deletedCount = teacherEventRepository.deleteByTeacherId(teacher1.getId());

        // 刷新持久化上下文，确保删除操作生效
        entityManager.flush();
        entityManager.clear();

        // Then - 验证只删除了教师 1 的事件
        assertThat(deletedCount).isEqualTo(1);
        // 使用 findByTeacherId 验证（避免 findById 从缓存获取）
        assertThat(teacherEventRepository.findByTeacherId(teacher1.getId())).isEmpty();
        assertThat(teacherEventRepository.findByTeacherId(teacher2.getId())).isPresent();
        assertThat(teacherEventRepository.existsByTeacherId(teacher1.getId())).isFalse();
        assertThat(teacherEventRepository.existsByTeacherId(teacher2.getId())).isTrue();
    }

    @Test
    @Rollback
    void testDeleteByTeacherIdWhenSingleEvent() {
        // Given - 创建有单个事件的教师（符合业务规则）
        cleanTable("TEACHER_EVENTS");
        cleanTable("USERS");

        User teacher = createTeacherUser("teacher_single_delete", "单事件删除教师");
        TeacherEvent event = createTeacherEvent(teacher, "待删除事件", "待删除描述");

        // When - 删除该教师的事件
        int deletedCount = teacherEventRepository.deleteByTeacherId(teacher.getId());

        // Then - 验证删除成功
        assertThat(deletedCount).isEqualTo(1);
        assertThat(teacherEventRepository.existsByTeacherId(teacher.getId())).isFalse();
        assertThat(teacherEventRepository.findByTeacherId(teacher.getId())).isEmpty();
    }

    @Test
    @Rollback
    void testDeleteByTeacherIdWhenNoEvents() {
        // Given - 创建没有事件的教师
        User teacher = createTeacherUser("teacher_no_event_delete", "无事件删除教师");

        // When
        int deletedCount = teacherEventRepository.deleteByTeacherId(teacher.getId());

        // Then - 验证删除 0 条记录
        assertThat(deletedCount).isEqualTo(0);
    }

    // ==================== 更新操作测试 ====================

    @Test
    @Rollback
    void testUpdateTeacherEvent() {
        // Given - 创建事件
        User teacher = createTeacherUser("teacher_update", "更新测试教师");
        TeacherEvent event = createTeacherEvent(teacher, "原始标题", "原始描述");

        // When - 更新事件
        event.setEventTitle("更新后的标题");
        event.setEventDescription("更新后的描述");
        TeacherEvent updatedEvent = teacherEventRepository.save(event);

        // Then - 验证更新成功
        assertThat(updatedEvent.getEventTitle()).isEqualTo("更新后的标题");
        assertThat(updatedEvent.getEventDescription()).isEqualTo("更新后的描述");

        // 验证数据库中已更新
        Optional<TeacherEvent> foundEvent = teacherEventRepository.findById(event.getId());
        assertThat(foundEvent).isPresent();
        assertThat(foundEvent.get().getEventTitle()).isEqualTo("更新后的标题");
    }

    // ==================== 全局事件相关方法测试 ====================

    @Test
    @Rollback
    void testFindByIsGlobalTrue() {
        // Given - 创建全局事件
        cleanTable("TEACHER_EVENTS");
        User teacher = createTeacherUser("teacher_global", "全局事件教师");
        TeacherEvent globalEvent = createTeacherEvent(teacher, "全局事件", "全局描述");
        globalEvent.setIsGlobal(true);
        teacherEventRepository.save(globalEvent);

        // When
        Optional<TeacherEvent> found = teacherEventRepository.findByIsGlobalTrue();

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getIsGlobal()).isTrue();
        assertThat(found.get().getEventTitle()).isEqualTo("全局事件");
    }

    @Test
    @Rollback
    void testFindByIsGlobalTrueWhenNotExists() {
        // Given - 不创建任何事件
        cleanTable("TEACHER_EVENTS");

        // When
        Optional<TeacherEvent> found = teacherEventRepository.findByIsGlobalTrue();

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @Rollback
    void testExistsByIsGlobalTrue() {
        // Given - 创建全局事件
        cleanTable("TEACHER_EVENTS");
        User teacher = createTeacherUser("teacher_exists_global", "存在全局事件教师");
        TeacherEvent globalEvent = createTeacherEvent(teacher, "全局事件", "描述");
        globalEvent.setIsGlobal(true);
        teacherEventRepository.save(globalEvent);

        // When & Then
        assertThat(teacherEventRepository.existsByIsGlobalTrue()).isTrue();
    }

    @Test
    @Rollback
    void testExistsByIsGlobalTrueWhenNotExists() {
        // Given - 不创建任何事件
        cleanTable("TEACHER_EVENTS");

        // When & Then
        assertThat(teacherEventRepository.existsByIsGlobalTrue()).isFalse();
    }

    @Test
    @Rollback
    void testDeleteAllEvents() {
        // Given - 创建多个事件
        cleanTable("TEACHER_EVENTS");
        User teacher1 = createTeacherUser("teacher_del1", "删除教师 1");
        User teacher2 = createTeacherUser("teacher_del2", "删除教师 2");
        createTeacherEvent(teacher1, "事件 1", "描述 1");
        createTeacherEvent(teacher2, "事件 2", "描述 2");

        // When
        int deletedCount = teacherEventRepository.deleteAllEvents();

        // Then
        assertThat(deletedCount).isEqualTo(2);
        assertThat(teacherEventRepository.count()).isZero();
    }

    @Test
    @Rollback
    void testDeleteAllEventsWhenEmpty() {
        // Given - 空表
        cleanTable("TEACHER_EVENTS");

        // When
        int deletedCount = teacherEventRepository.deleteAllEvents();

        // Then
        assertThat(deletedCount).isEqualTo(0);
    }

    // ==================== 更新操作测试 ====================

    /**
     * 创建教师用户
     */
    private User createTeacherUser(String username, String realName) {
        User teacher = new User();
        teacher.setUsername(username + "_" + System.nanoTime());
        teacher.setPasswordHash("$2a$10$test_hash_for_teacher");
        teacher.setUserType(User.UserType.TEACHER);
        teacher.setRealName(realName);
        teacher.setActive(true);
        return userRepository.save(teacher);
    }

    /**
     * 创建教师事件并保存
     */
    private TeacherEvent createTeacherEvent(User teacher, String title, String description) {
        TeacherEvent event = new TeacherEvent();
        event.setTeacherId(teacher.getId());
        event.setTeacherName(teacher.getRealName());
        event.setEventTitle(title);
        event.setEventDescription(description);
        event.setEventTimestamp(LocalDateTime.now());
        event.setIsGlobal(!teacherEventRepository.existsByIsGlobalTrue());
        return teacherEventRepository.save(event);
    }
}
