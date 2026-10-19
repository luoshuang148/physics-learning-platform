package com.zhixuewuli.controller;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.UserAdminUpdateRequest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import static org.assertj.core.api.Assertions.*;
@Sql("/sql/scenario-teacher-event-test-data.sql")
class LastTeacherProtectionTest extends BaseControllerIntegrationTest {
 @Autowired UserService users;
 @org.junit.jupiter.api.BeforeEach void soleTeacher(){var other=userRepository.findById(2002L).orElseThrow();other.setActive(false);userRepository.saveAndFlush(other);}
 @Test void lastTeacherCannotBeDisabledOrDemoted(){
  var request=new UserAdminUpdateRequest();request.setUserId(2001L);request.setActive(false);
  assertThatThrownBy(()->users.updateUserByAdmin(request,2001L)).hasMessageContaining("最后一位");
  request.setActive(true);request.setUserType(User.UserType.STUDENT);
  assertThatThrownBy(()->users.updateUserByAdmin(request,2001L)).hasMessageContaining("最后一位");
 }
 @Test void anotherActiveTeacherAllowsDemotion(){
  var second=new User();second.setUsername("backup_teacher");second.setRealName("Backup teacher");second.setPasswordHash("x");second.setUserType(User.UserType.TEACHER);second.setActive(true);userRepository.saveAndFlush(second);
  var request=new UserAdminUpdateRequest();request.setUserId(2001L);request.setUserType(User.UserType.STUDENT);
  assertThat(users.updateUserByAdmin(request,second.getId()).getUserType()).isEqualTo(User.UserType.STUDENT);
 }
 @Test
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 @Sql("/sql/scenario-teacher-event-test-data.sql")
 @Sql(scripts="/sql/cleanup-all.sql",executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
 void concurrentSelfDeactivationKeepsOneActiveTeacher() throws Exception {
  var second=userRepository.findById(2002L).orElseThrow();second.setActive(true);userRepository.saveAndFlush(second);
  var gate=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
   for(Long id:java.util.List.of(2001L,2002L)) futures.add(pool.submit(()->{
    gate.await();var update=new UserAdminUpdateRequest();update.setUserId(id);update.setActive(false);
    try {users.updateUserByAdmin(update,id);return true;}
    catch(com.zhixuewuli.common.exception.BusinessException expected){assertThat(expected.getMessage()).contains("最后一位");return false;}
   }));
   gate.countDown();int succeeded=0;for(var future:futures)if(future.get(10,java.util.concurrent.TimeUnit.SECONDS))succeeded++;
   assertThat(succeeded).isEqualTo(1);
   assertThat(userRepository.countByUserTypeAndActiveTrue(User.UserType.TEACHER)).isEqualTo(1);
  }
 }
}
