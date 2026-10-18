package com.zhixuewuli.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionImageRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class AnswerHistoryPaginationTest extends BaseControllerIntegrationTest {
    @Autowired AnswerRecordService records;
    @Autowired AnswerRecordRepository answerRecords;
    @Autowired QuestionRepository questions;
    @Autowired QuestionImageRepository images;
    @Autowired ObjectMapper json;

    @Test
    void historyPageIsBoundedOrderedAndDoesNotCarryLargeContent() throws Exception {
        Question question = question("image-same", "解析原文");
        LocalDateTime base = LocalDateTime.now().minusHours(5);
        for (int i = 0; i < 5; i++) {
            AnswerRecord record = new AnswerRecord();
            record.setUserId(1001L); record.setQuestionId(question.getId()); record.setSessionId("page-session");
            record.setUserAnswer("A"); record.setCorrect(true); record.setAnsweredAt(base.plusHours(i));
            record.setContentSnapshot(question.getContent()); record.setOptionsSnapshot(question.getOptions());
            record.setCorrectSnapshot("A"); record.setAnalysisSnapshot("解析原文"); record.setDifficultySnapshot(1);
            record.setQuestionSnapshot(json.writeValueAsString(question));
            answerRecords.saveAndFlush(record);
        }

        mockMvc.perform(get("/answer-records/student/history").param("page", "1").param("size", "2")
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.pages").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].questionPreview").value("image-same"))
                .andExpect(jsonPath("$.data.records[0].questionAnalysis").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].imageBase64").doesNotExist());

        mockMvc.perform(get("/answer-records/student/history").param("page", "3").param("size", "2")
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(1));
        mockMvc.perform(get("/answer-records/student/history").param("page", "0").param("size", "2")
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/answer-records/student/history").param("size", "101")
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageIsStoredOnceAndOldFullSnapshotStillReads() throws Exception {
        Question first = question("image-same", "旧解析");
        Question second = question("image-same-two", "另一解析");
        submitFree("image-a", first.getId());
        submitFree("image-b", second.getId());

        assertThat(images.count()).isEqualTo(1);
        List<AnswerRecord> saved = answerRecords.findByUserIdOrderByAnsweredAtDesc(1001L);
        assertThat(saved.get(0).getImageRef()).isNotBlank().isEqualTo(saved.get(1).getImageRef());
        assertThat(saved.get(0).getQuestionSnapshot()).doesNotContain("base64-image").doesNotContain("imageBase64");

        AnswerRecord newest = saved.get(0);
        var detail = records.getAnswerRecordById(newest.getId(), 1001L);
        assertThat(detail.getImageBase64()).isEqualTo("data:image/png;base64,base64-image");
        assertThat(detail.getQuestionAnalysis()).isEqualTo("另一解析");

        first.setExplanation("修改后的解析"); first.setImageBase64("data:image/png;base64,changed");
        questions.saveAndFlush(first);
        assertThat(records.getAnswerRecordById(saved.get(1).getId(), 1001L).getQuestionAnalysis()).isEqualTo("旧解析");
        assertThat(records.getAnswerRecordById(saved.get(1).getId(), 1001L).getImageBase64())
                .isEqualTo("data:image/png;base64,base64-image");

        AnswerRecord legacy = new AnswerRecord();
        legacy.setUserId(1001L); legacy.setQuestionId(first.getId()); legacy.setSessionId("legacy-session");
        legacy.setUserAnswer("A"); legacy.setCorrect(true); legacy.setAnsweredAt(LocalDateTime.now());
        legacy.setQuestionSnapshot(json.writeValueAsString(first));
        answerRecords.saveAndFlush(legacy);
        assertThat(records.getAnswerRecordById(legacy.getId(), 1001L).getImageBase64())
                .isEqualTo("data:image/png;base64,changed");
    }

    @Test
    void legacyFullHistoryEndpointRejectsOverLimitInsteadOfTruncating() throws Exception {
        Long questionId = question("legacy-limit-question", "解析").getId();
        for (int i = 0; i < 101; i++) {
            jdbcTemplate.update("INSERT INTO answer_records(id,user_id,question_id,session_id,selected_option,correct,answered_at,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    "legacy-" + i, 1001L, questionId, "legacy-" + i, "B", true);
        }
        mockMvc.perform(get("/answer-records/student/my")
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/answer-records/teacher/all")
                .header("Authorization", createAuthHeader(2001L, "teacher_user")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherStudentCannotReadOrUnlockHistory() throws Exception {
        User other = new User(); other.setUsername("other_student"); other.setRealName("另一学生");
        other.setUserType(User.UserType.STUDENT); other.setActive(true); other.setPasswordHash("x");
        other = userRepository.saveAndFlush(other);
        mockMvc.perform(get("/answer-records/user/1001/history")
                .header("Authorization", createAuthHeader(other.getId(), other.getUsername())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/answer-records/user/1001/history")
                .header("Authorization", createAuthHeader(2001L, "teacher_user")))
                .andExpect(status().isOk());
    }

    private void submitFree(String requestId, Long questionId) {
        AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
        request.setQuestionId(questionId); request.setUserAnswer("A"); request.setRequestId(requestId);
        records.submitAnswerRecord(request, 1001L, "image-session");
    }

    private Question question(String content, String explanation) {
        Question question = new Question(); question.setContent(content);
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        question.setCorrectOption("A"); question.setDifficulty(1); question.setExplanation(explanation);
        question.setImageBase64("data:image/png;base64,base64-image");
        return questions.saveAndFlush(question);
    }
}
