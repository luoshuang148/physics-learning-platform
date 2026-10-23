package com.zhixuewuli.repository;
import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.session.SessionManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;
@Tag("integration-repository")
@TestPropertySource(properties="spring.jpa.properties.hibernate.generate_statistics=true")
class BoundedHistoryRecoveryTest extends BaseIntegrationTest {
 @Autowired ConversationHistoryRepository histories;@Autowired AnswerRecordRepository answers;@Autowired TeacherEventRepository events;@Autowired AIBusinessProperties properties;
 @Test void recoveryReadsOnlyRecentWindowAndKeepsAllTimeSequenceAndTokenAccounting(){
  User u=new User();u.setUsername("history_window");u.setRealName("history_window");u.setPasswordHash("hash");u.setUserType(User.UserType.STUDENT);u.setActive(true);jpaEntityManager.persist(u);jpaEntityManager.flush();
  for(int n=1;n<=40;n++){var row=new ConversationHistory();row.setUserId(u.getId());row.setSessionId("history-window");row.setConversationType(ConversationType.REAL_TIME_QA);row.setQuestion("q"+n);row.setAnswer("a"+n);row.setSequenceNumber(n);row.setTokenCount(10);row.setIsActive(true);histories.save(row);}
  jpaEntityManager.flush();jpaEntityManager.clear();properties.setMaxHistoryCount(3);
  var stats=jpaEntityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
  var manager=new SessionManager(histories,answers,properties,events,org.mockito.Mockito.mock(com.zhixuewuli.service.knowledge.AiRunRecorder.class),org.mockito.Mockito.mock(com.zhixuewuli.service.knowledge.AiRunHistoryService.class));var context=manager.getOrCreateSession("history-window",u.getId(),ConversationType.REAL_TIME_QA,null,()->"physics");
  assertThat(context.getInteractionRecords()).hasSize(3);assertThat(context.getUsedTokens()).isEqualTo(400);assertThat(context.generateNextSequence()).isEqualTo(41);
  assertThat(stats.getEntityLoadCount()).isLessThanOrEqualTo(3);
 }
}
