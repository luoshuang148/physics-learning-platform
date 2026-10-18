package com.zhixuewuli.controller;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Sql("/sql/scenario-teacher-event-test-data.sql")
class AuthLifecycleSecurityTest extends BaseControllerIntegrationTest {
 @Test void logoutInvalidatesExistingBearer() throws Exception {
  String token=createAuthHeader(1001L,"student_user");
  mockMvc.perform(post("/auth/logout").header("Authorization",token)).andExpect(status().isOk());
  mockMvc.perform(get("/user/me").header("Authorization",token)).andExpect(status().isUnauthorized());
 }
 @Test void passwordChangeInvalidatesExistingBearer() throws Exception {
  String token=createAuthHeader(1001L,"student_user");
  mockMvc.perform(put("/user/me").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
   .content("{\"password\":\"changed-local-password-123\"}")).andExpect(status().isOk());
  mockMvc.perform(get("/user/me").header("Authorization",token)).andExpect(status().isUnauthorized());
 }
 @Test void repeatedLoginFailuresAreThrottled() throws Exception {
  for(int n=0;n<5;n++) mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
   .content("{\"username\":\"throttle_fixture\",\"password\":\"bad-password\"}")).andExpect(status().isUnauthorized());
  mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
   .content("{\"username\":\"throttle_fixture\",\"password\":\"bad-password\"}")).andExpect(status().isTooManyRequests());
 }
 @Test void publicHealthDoesNotMakeMetricsPublic() throws Exception {
  mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  mockMvc.perform(get("/actuator/metrics").header("Authorization",createAuthHeader(1001L,"student_user"))).andExpect(status().isForbidden());
  mockMvc.perform(get("/actuator/metrics").header("Authorization",createAuthHeader(2001L,"teacher_user"))).andExpect(status().isOk());
 }
}
