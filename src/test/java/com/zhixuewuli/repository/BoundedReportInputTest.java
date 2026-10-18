package com.zhixuewuli.repository;
import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.ai.report.ReportInputRepository;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;
@Tag("integration-repository")
@TestPropertySource(properties="spring.jpa.properties.hibernate.generate_statistics=true")
class BoundedReportInputTest extends BaseIntegrationTest {
 @Autowired ReportInputRepository inputs;@Autowired UserRepository users;@Autowired QuestionRepository questions;
 Long userId,questionId;
 @BeforeEach void fixture(){
  User u=new User();u.setUsername("bounded_report");u.setRealName("bounded_report");u.setPasswordHash("unused");u.setUserType(User.UserType.STUDENT);u.setActive(true);userId=users.saveAndFlush(u).getId();
  Question q=new Question();q.setContent("edited bank");q.setOptions("{\"A\":\"a\",\"B\":\"b\"}");q.setCorrectOption("B");q.setDifficulty(1);q.setImageBase64("x".repeat(200000));questionId=questions.saveAndFlush(q).getId();
 }
 void rows(int count,String content){
  jdbcTemplate.update("INSERT INTO answer_records(id,user_id,question_id,session_id,selected_option,correct,answered_at,content_snapshot,options_snapshot,correct_snapshot,question_snapshot) SELECT CONCAT('bounded-',X),?,?,'bounded','B',false,CURRENT_TIMESTAMP,?,'{\"A\":\"a\",\"B\":\"b\"}','A',CONCAT('{\"content\":\"original\",\"imageBase64\":\"',REPEAT('x',200000),'\"}') FROM SYSTEM_RANGE(1,?)",userId,questionId,content,count);
  jpaEntityManager.clear();
 }
 @Test void tooManyRowsRejectBeforeAnyImageBearingEntityIsLoaded(){
  // Count fixture needs no large body.
  jdbcTemplate.update("INSERT INTO answer_records(id,user_id,question_id,session_id,selected_option,correct,answered_at) SELECT CONCAT('many-',X),?,?,'bounded','B',false,CURRENT_TIMESTAMP FROM SYSTEM_RANGE(1,5001)",userId,questionId);jpaEntityManager.clear();
  var stats=jpaEntityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
  assertThatThrownBy(()->inputs.student(userId,"bounded",null)).isInstanceOf(com.zhixuewuli.common.exception.BusinessException.class);
  assertThat(stats.getEntityLoadCount()).isZero();
 }
 @Test void imageHeavyAnswersUseTextSnapshotsWithoutLoadingEntities(){
  rows(10,"recorded text");var stats=jpaEntityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
  var records=inputs.student(userId,"bounded",null);assertThat(records).hasSize(10);
  for(var r:records){assertThat(r.getQuestionSnapshot()).isNull();assertThat(r.getContentSnapshot()).isEqualTo("recorded text");assertThat(r.getCorrectSnapshot()).isEqualTo("A");}
  assertThat(stats.getEntityLoadCount()).isZero();
 }
 @Test void singleFieldAndCombinedPromptBudgetsRejectOversizedInput(){
  rows(1,"x".repeat(10000));assertThatThrownBy(()->inputs.student(userId,"bounded",null)).isInstanceOf(com.zhixuewuli.common.exception.BusinessException.class);
 }
 @Test void combinedTextInputIsBoundedBeforePreparingAProviderPrompt(){
  rows(50,"x".repeat(3000));
  assertThatThrownBy(()->inputs.student(userId,"bounded",null)).isInstanceOf(com.zhixuewuli.common.exception.BusinessException.class);
 }

}
