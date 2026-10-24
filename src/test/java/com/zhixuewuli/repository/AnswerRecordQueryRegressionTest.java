package com.zhixuewuli.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.util.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.answerRecord.impl.AnswerRecordServiceImpl;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true", showSql = false)
@ActiveProfiles("test")
@Tag("integration-repository")
@Import({com.zhixuewuli.service.teacherEvent.TaskRosterService.class, com.zhixuewuli.service.classroom.ClassAccessService.class, com.zhixuewuli.service.teacherEvent.TaskQuestionService.class, AnswerRecordServiceImpl.class, UserPermissionUtil.class, QuestionResponseConverter.class,
        OptionMappingUtil.class, ObjectMapper.class, com.zhixuewuli.service.question.AnswerAccessPolicy.class,
        com.zhixuewuli.service.question.QuestionImageStore.class})
class AnswerRecordQueryRegressionTest {
    @Autowired EntityManager em;
    @Autowired AnswerRecordServiceImpl service;

    @Test
    void coldListQueryMustNotIssueOneQuestionQueryPerRecord() {
        User teacher = user("teacher", User.UserType.TEACHER);
        User student = user("student", User.UserType.STUDENT);
        for (int i = 0; i < 25; i++) {
            Question question = new Question();
            question.setContent("Query regression question " + i);
            question.setOptions("{\"A\":\"one\",\"B\":\"two\",\"C\":\"three\",\"D\":\"four\"}");
            question.setCorrectOption("A");
            question.setDifficulty(1);
            question.setKnowledgePointCodes(java.util.List.of("mechanics.newton"));
            em.persist(question);
            AnswerRecord answer = new AnswerRecord();
            answer.setUserId(student.getId());
            answer.setQuestionId(question.getId());
            answer.setSessionId("query-fixture");
            answer.setUserAnswer("B");
            answer.setCorrect(false);
            answer.setAnsweredAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            em.persist(answer);
        }
        em.flush();
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = service.getAllUsersAnswerRecords(teacher.getId());
        long count = statistics.getPrepareStatementCount();
        System.out.println("cold-list: 25 distinct questions, SQL statements=" + count);
        assertThat(result.getRecords()).hasSize(25);
        assertThat(result.getRecords()).allSatisfy(record ->
                assertThat(record.getKnowledgePointCodes()).containsExactly("mechanics.newton"));
        assertThat(count).isLessThanOrEqualTo(3);
    }

    private User user(String name, User.UserType type) {
        User user = new User();
        user.setUsername(name);
        user.setRealName(name);
        user.setPasswordHash("test-hash");
        user.setUserType(type);
        user.setActive(true);
        em.persist(user);
        return user;
    }
}
