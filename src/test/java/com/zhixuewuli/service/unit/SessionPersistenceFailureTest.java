package com.zhixuewuli.service.unit;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class SessionPersistenceFailureTest extends BaseUnitTest {
 @Test void failedDatabaseSaveCannotBecomeSuccessfulInMemoryHistory(){
  var histories=mock(ConversationHistoryRepository.class);var manager=new SessionManager(histories,mock(AnswerRecordRepository.class),new AIBusinessProperties(),mock(TeacherEventRepository.class),mock(com.zhixuewuli.service.knowledge.AiRunRecorder.class),mock(com.zhixuewuli.service.knowledge.AiRunHistoryService.class));
  var context=manager.getOrCreateSession("failure",1L,ConversationType.REAL_TIME_QA,null,()->"physics");
  when(histories.save(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
  assertThatThrownBy(()->manager.addInteraction(context,"question","answer",10)).isInstanceOf(RuntimeException.class);
  assertThat(context.getInteractionRecords()).isEmpty();
 }
 @Test void failedRagSourceBindingCannotBecomeSuccessfulInMemoryHistory(){
  var histories=mock(ConversationHistoryRepository.class);var recorder=mock(com.zhixuewuli.service.knowledge.AiRunRecorder.class);
  var manager=new SessionManager(histories,mock(AnswerRecordRepository.class),new AIBusinessProperties(),mock(TeacherEventRepository.class),recorder,mock(com.zhixuewuli.service.knowledge.AiRunHistoryService.class));
  var context=manager.getOrCreateSession("rag-failure",1L,ConversationType.WRONG_QUESTION_ANALYSIS,2L,3L,()->"physics");
  when(histories.saveAndFlush(any())).thenAnswer(inv->{var history=(com.zhixuewuli.entity.ConversationHistory)inv.getArgument(0);history.setId(99L);return history;});
  when(recorder.record(any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("source store unavailable"));
  var rag=new com.zhixuewuli.service.knowledge.RagInteractionData("hybrid","ANSWERED","model","system","user","input",1,1,2,3L,4L,java.util.List.of());
  assertThatThrownBy(()->manager.addRagInteraction(context,"question","answer",2,rag)).isInstanceOf(RuntimeException.class);
  assertThat(context.getInteractionRecords()).isEmpty();
 }
}
