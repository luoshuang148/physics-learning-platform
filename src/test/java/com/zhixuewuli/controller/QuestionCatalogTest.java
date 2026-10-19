package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
@TestPropertySource(properties="spring.jpa.properties.hibernate.generate_statistics=true")
class QuestionCatalogTest extends BaseControllerIntegrationTest {
 @Autowired QuestionRepository bank;
 @Autowired EntityManager em;
 @Test void catalogPagesAndFiltersWithoutLoadingQuestionEntitiesOrImages() throws Exception {
  for(int i=0;i<3;i++) {
   var q=new Question();q.setContent("catalog-token-"+i+"x".repeat(200));q.setOptions("{\"A\":\"a\"}");
   q.setCorrectOption("A");q.setDifficulty(i==0?1:2);q.setImageBase64("data:image/png;base64,"+"A".repeat(10000));bank.save(q);
  }
  em.flush();em.clear();
  var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.clear();
  String teacher=createAuthHeader(2001L,"teacher_user");
  mockMvc.perform(get("/questions/teacher/page").param("search","catalog-token").param("page","2").param("size","2").header("Authorization",teacher))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
   .andExpect(jsonPath("$.data.records.length()").value(1)).andExpect(jsonPath("$.data.records[0].hasImage").value(true))
   .andExpect(jsonPath("$.data.records[0].imageBase64").doesNotExist()).andExpect(jsonPath("$.data.records[0].options").doesNotExist());
  org.assertj.core.api.Assertions.assertThat(stats.getEntityStatistics(Question.class.getName()).getLoadCount()).isZero();
  mockMvc.perform(get("/questions/teacher/page").param("search","catalog-token").param("difficulty","2").header("Authorization",teacher))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
  mockMvc.perform(get("/questions/teacher/page").param("search","catalog_%").header("Authorization",teacher))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
  mockMvc.perform(get("/questions/teacher/page").param("size","101").header("Authorization",teacher)).andExpect(status().isBadRequest());
  mockMvc.perform(get("/questions/teacher/page").header("Authorization",createAuthHeader(1001L,"student_user"))).andExpect(status().isForbidden());
 }
}
