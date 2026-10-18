package com.zhixuewuli.controller;

import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class TaskLifecycleIntegrationTest extends BaseControllerIntegrationTest {
    @Autowired TeacherEventService tasks;
    @Autowired TeacherEventRepository events;
    @Autowired QuestionRepository questions;
    @Autowired AnswerRecordService answers;

    private Question question(String title) {
        Question q = new Question();
        q.setContent(title);
        q.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        q.setCorrectOption("A");
        q.setDifficulty(1);
        return questions.saveAndFlush(q);
    }

    private TeacherEventCreateRequest request(String title, TeacherEvent.Status status, Question q) {
        var request = new TeacherEventCreateRequest();
        request.setEventTitle(title);
        request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        request.setQuestionIds(List.of(q.getId()));
        request.setStatus(status);
        return request;
    }

    @Test
    void draftIsInvisibleToStudentsAndCanBePublished() {
        Question q = question("lifecycle draft");
        var draft = tasks.publishEvent(request("Draft A", TeacherEvent.Status.DRAFT, q), 2001L);
        assertThat(draft.getStatus()).isEqualTo(TeacherEvent.Status.DRAFT);
        assertThat(tasks.listStudentTasks(1001L)).noneMatch(task -> task.getId().equals(draft.getId()));
        assertThatThrownBy(() -> tasks.getEvent(draft.getId(), 1001L)).isInstanceOf(RuntimeException.class);

        tasks.updateDraft(draft.getId(), request("Draft A edited", TeacherEvent.Status.DRAFT, q), 2001L);
        var published = tasks.publishTask(draft.getId(), 2001L);
        assertThat(published.getStatus()).isEqualTo(TeacherEvent.Status.PUBLISHED);
        assertThat(published.getAvailability()).isEqualTo("OPEN");
        assertThat(tasks.listStudentTasks(1001L)).anyMatch(task -> task.getId().equals(draft.getId()));
    }

    @Test
    void multiplePublishedTasksCoexistAndCloseRemovesStudentVisibility() {
        Question q1 = question("parallel one");
        Question q2 = question("parallel two");
        var first = tasks.publishEvent(request("Parallel A", TeacherEvent.Status.PUBLISHED, q1), 2001L);
        var second = tasks.publishEvent(request("Parallel B", TeacherEvent.Status.PUBLISHED, q2), 2001L);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(tasks.listStudentTasks(1001L)).extracting("id").contains(first.getId(), second.getId());
        assertThat(tasks.listTeacherTasks(2001L)).extracting("id").contains(first.getId(), second.getId());

        tasks.closeTask(first.getId(), 2001L);
        assertThat(tasks.getEvent(first.getId()).getStatus()).isEqualTo(TeacherEvent.Status.CLOSED);
        assertThat(tasks.listStudentTasks(1001L)).noneMatch(task -> task.getId().equals(first.getId()));
        assertThat(tasks.listStudentTasks(1001L)).anyMatch(task -> task.getId().equals(second.getId()));
    }

    @Test
    void submissionIsRejectedOutsideTaskWindow() {
        Question q = question("window submit");
        var future = request("Future submit", TeacherEvent.Status.PUBLISHED, q);
        future.setStartAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(2));
        var scheduled = tasks.publishEvent(future, 2001L);
        var submit = new AnswerRecordSubmitRequest();
        submit.setQuestionId(q.getId());
        submit.setUserAnswer("A");
        submit.setIntent("ASSIGNMENT");
        submit.setEventId(scheduled.getId());
        submit.setRequestId("before-start");
        assertThatThrownBy(() -> answers.submitAnswerRecord(submit, 1001L, "lifecycle-window"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("尚未开始");
    }

    @Test
    void futureTaskIsScheduledAndPastDueTaskIsEnded() {
        Question q = question("window");
        var future = request("Future", TeacherEvent.Status.PUBLISHED, q);
        future.setStartAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(2));
        var scheduled = tasks.publishEvent(future, 2001L);
        assertThat(scheduled.getAvailability()).isEqualTo("SCHEDULED");
        assertThat(scheduled.getCanAnswer()).isFalse();
        assertThatThrownBy(() -> tasks.getEventQuestions(scheduled.getId(), 1001L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("尚未开始");

        Question q2 = question("ended");
        var endedRequest = request("Ended", TeacherEvent.Status.PUBLISHED, q2);
        endedRequest.setDueAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        var ended = tasks.publishEvent(endedRequest, 2001L);
        assertThat(ended.getAvailability()).isEqualTo("ENDED");
        assertThat(ended.getCanAnswer()).isFalse();
    }

    @Test
    void emptyDraftCannotBePublishedAndArchiveRemovesItFromActiveList() {
        var request = new TeacherEventCreateRequest();
        request.setEventTitle("Empty draft");
        request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        request.setStatus(TeacherEvent.Status.DRAFT);
        var draft = tasks.publishEvent(request, 2001L);
        assertThatThrownBy(() -> tasks.publishTask(draft.getId(), 2001L))
                .isInstanceOf(IllegalArgumentException.class);

        tasks.archiveEvent(draft.getId(), 2001L);
        assertThat(tasks.listTeacherTasks(2001L)).noneMatch(task -> task.getId().equals(draft.getId()));
        assertThat(events.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(TeacherEvent.Status.ARCHIVED);
    }
}
