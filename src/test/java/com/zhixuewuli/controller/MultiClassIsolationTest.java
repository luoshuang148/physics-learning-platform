package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.ClassCreateRequest;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.entity.TeachingClass;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.classroom.ClassAccessService;
import com.zhixuewuli.service.classroom.ClassroomService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class MultiClassIsolationTest extends BaseControllerIntegrationTest {
    @Autowired ClassroomService classrooms;
    @Autowired ClassAccessService access;
    @Autowired TeacherEventService tasks;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;

    private User student(String username, String name) {
        User user = new User();
        user.setUsername(username); user.setRealName(name); user.setPasswordHash("x");
        user.setUserType(User.UserType.STUDENT); user.setActive(true);
        return users.saveAndFlush(user);
    }

    private Question question(String title) {
        Question q = new Question();
        q.setContent(title); q.setOptions("{\"A\":\"a\",\"B\":\"b\"}"); q.setCorrectOption("A"); q.setDifficulty(1);
        return questions.saveAndFlush(q);
    }

    private TeacherEventCreateRequest task(String title, Long classId, Question q) {
        var request = new TeacherEventCreateRequest();
        request.setEventTitle(title); request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        request.setQuestionIds(List.of(q.getId())); request.setStatus(TeacherEvent.Status.PUBLISHED);
        request.setClassId(classId);
        return request;
    }

    @Test
    void classesIsolateTeacherAndStudentTaskVisibility() {
        var classA = classrooms.create(new ClassCreateRequest() {{ setName("高一 A 班"); }}, 2001L);
        var classB = classrooms.create(new ClassCreateRequest() {{ setName("高一 B 班"); }}, 2002L);
        var s2 = student("student_two", "学生二");
        access.ensureStudent(classA.getId(), 1001L);
        access.ensureStudent(classB.getId(), s2.getId());

        var a = tasks.publishEvent(task("A 作业", classA.getId(), question("A only")), 2001L);
        var b = tasks.publishEvent(task("B 作业", classB.getId(), question("B only")), 2002L);

        assertThat(tasks.listTeacherTasks(2001L)).extracting("id").contains(a.getId()).doesNotContain(b.getId());
        assertThat(tasks.listTeacherTasks(2002L)).extracting("id").contains(b.getId()).doesNotContain(a.getId());
        assertThat(tasks.listStudentTasks(1001L)).extracting("id").contains(a.getId()).doesNotContain(b.getId());
        assertThat(tasks.listStudentTasks(s2.getId())).extracting("id").contains(b.getId()).doesNotContain(a.getId());
        assertThatThrownBy(() -> tasks.getTaskDetail(a.getId(), 2002L)).hasMessageContaining("无权访问");
        assertThatThrownBy(() -> tasks.getEvent(a.getId(), s2.getId())).hasMessageContaining("无权访问");
    }

    @Test
    void teacherWithoutMembershipReceivesDefaultClassOnListing() {
        // Bootstrap teachers never touch class_teachers; listing must still return a usable class
        // so the class switcher and publish flow are not stuck on an empty selection.
        var listed = classrooms.listForTeacher(2002L);

        assertThat(listed).isNotEmpty();
        var fallback = listed.get(0);
        assertThat(fallback.getName()).isEqualTo("默认班级");
        assertThat(fallback.getRole()).isEqualTo("OWNER");
        assertThat(access.classesForTeacher(2002L)).extracting(TeachingClass::getName)
                .contains("默认班级");
    }
}
