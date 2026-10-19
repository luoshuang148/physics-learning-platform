package com.zhixuewuli.common.security;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.controller.TeacherEventController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 认证由过滤器链统一完成、角色由 @TeacherOnly 统一限制的端到端约束。 */
@Sql("/sql/scenario-teacher-event-test-data.sql")
class SecurityAuthorizationIntegrationTest extends BaseControllerIntegrationTest {

    private static final long STUDENT_ID = 1001L;
    private static final long TEACHER_ID = 2001L;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("业务接口默认需要登录，未携带令牌返回统一 401")
    void apiRequiresAuthenticationByDefault() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"));
        mockMvc.perform(get("/teacher/events/all")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("无效令牌返回 401 并说明原因")
    void invalidTokenIsRejectedWithReason() throws Exception {
        mockMvc.perform(get("/user/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("认证失败: 令牌无效或已过期"));
    }

    @Test
    @DisplayName("学生调用教师接口在进入控制器前被拒绝")
    void studentCannotCallTeacherOnlyEndpoints() throws Exception {
        String student = createAuthHeader(STUDENT_ID, "student_user");
        mockMvc.perform(get("/user/teacher/all").header("Authorization", student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4801))
                .andExpect(jsonPath("$.message").value("权限不足"));
        mockMvc.perform(get("/teacher/events/all").header("Authorization", student))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/answer-records/teacher/page").header("Authorization", student))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("教师可以调用教师接口，学生仍可调用学生接口")
    void rolesReachTheirOwnEndpoints() throws Exception {
        mockMvc.perform(get("/user/teacher/all").header("Authorization", createAuthHeader(TEACHER_ID, "teacher_user")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/teacher/events/available").header("Authorization", createAuthHeader(STUDENT_ID, "student_user")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("公开接口不受过期或伪造令牌影响")
    void publicEndpointsIgnoreBrokenTokens() throws Exception {
        // 校验失败返回 400 说明请求已越过安全层到达控制器
        mockMvc.perform(post("/auth/register").header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("前端路由无需令牌即可加载单页应用")
    void spaRoutesAreServedWithoutToken() throws Exception {
        mockMvc.perform(get("/teacher/dashboard")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/student/home")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("教师任务的写操作全部声明为教师专属")
    void teacherTaskMutationsAreTeacherOnly() {
        var unguarded = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType() == TeacherEventController.class)
                .filter(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                        .anyMatch(method -> method.name().matches("POST|PUT|DELETE")))
                .map(entry -> entry.getValue())
                .filter(method -> !method.hasMethodAnnotation(TeacherOnly.class))
                .map(HandlerMethod::getShortLogMessage)
                .toList();
        assertThat(unguarded).isEmpty();
    }
}
