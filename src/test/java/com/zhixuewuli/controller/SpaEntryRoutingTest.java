package com.zhixuewuli.controller;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class SpaEntryRoutingTest extends BaseControllerIntegrationTest {
 @Test void directLoginAndRegisterLinksReachTheSpaBeforeAuthentication() throws Exception {
  mockMvc.perform(get("/auth/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
  mockMvc.perform(get("/auth/register")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
 }
}
