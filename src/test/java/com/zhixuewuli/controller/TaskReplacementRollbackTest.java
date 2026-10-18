package com.zhixuewuli.controller;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.jdbc.*;
import org.springframework.transaction.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Sql("/sql/scenario-teacher-event-test-data.sql")
class TaskReplacementRollbackTest extends BaseControllerIntegrationTest {
 @Autowired TeacherEventService teaching;@SpyBean TeacherEventRepository events;
 @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
 @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
 @Sql(scripts="/sql/cleanup-all.sql",executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
 void databaseFailureAfterArchiveFlushRestoresOriginalActiveTask(){
  doAnswer(invocation->{TeacherEvent row=invocation.getArgument(0);
   if(row.getId()==null)throw new org.springframework.dao.DataAccessResourceFailureException("injected new-task write failure");
   return invocation.callRealMethod();
  }).when(events).saveAndFlush(any(TeacherEvent.class));
  var request=new TeacherEventCreateRequest();request.setEventTitle("failed replacement");request.setEventTimestamp(java.time.LocalDateTime.now());
  assertThatThrownBy(()->teaching.replaceEvent(1L,request,2001L)).isInstanceOf(RuntimeException.class);
  assertThat(events.findById(1L).orElseThrow().getIsGlobal()).isTrue();
  assertThat(events.findByIsGlobalTrue().orElseThrow().getId()).isEqualTo(1L);
  assertThat(events.count()).isEqualTo(1);
 }
}
