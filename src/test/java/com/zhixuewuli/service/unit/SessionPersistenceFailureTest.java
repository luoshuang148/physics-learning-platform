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
  var histories=mock(ConversationHistoryRepository.class);var manager=new SessionManager(histories,mock(AnswerRecordRepository.class),new AIBusinessProperties(),mock(TeacherEventRepository.class));
  var context=manager.getOrCreateSession("failure",1L,ConversationType.REAL_TIME_QA,null,()->"physics");
  when(histories.save(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
  assertThatThrownBy(()->manager.addInteraction(context,"question","answer",10)).isInstanceOf(RuntimeException.class);
  assertThat(context.getInteractionRecords()).isEmpty();
 }
}
