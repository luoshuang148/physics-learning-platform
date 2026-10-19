package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.common.security.AuthUserCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real commits: the inherited transactional tests also exercise read-your-writes semantics. */
@Sql("/sql/scenario-teacher-event-test-data.sql")
@Sql(scripts="/sql/cleanup-all.sql", executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AuthCacheLifecycleTest extends BaseControllerIntegrationTest {
    @Autowired AuthUserCache cache;

    @Test void committedLogoutEvictsAWarmSnapshot() throws Exception {
        String token = createAuthHeader(1001L, "student_user");
        mockMvc.perform(get("/user/me").header("Authorization", token)).andExpect(status().isOk());
        assertThat(cache.get(1001L).tokenVersion()).isZero();
        mockMvc.perform(post("/auth/logout").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get("/user/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        assertThat(cache.get(1001L).tokenVersion()).isEqualTo(1);
    }
    @Test void committedPasswordChangeEvictsAWarmSnapshot() throws Exception {
        String token = createAuthHeader(1001L, "student_user");
        cache.get(1001L);
        mockMvc.perform(put("/user/me").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"changed-local-password-123\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/user/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }
    @Test void committedRoleAndActiveChangesEvictAWarmSnapshot() throws Exception {
        String teacher = createAuthHeader(2001L, "teacher_user");
        String old = createAuthHeader(1001L, "student_user");
        cache.get(1001L);
        mockMvc.perform(put("/user/teacher/update").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1001,\"userType\":\"TEACHER\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/user/teacher/all").header("Authorization", old)).andExpect(status().isUnauthorized());
        String promoted = "Bearer " + jwtUtil.generateToken(1001L, "student_user", 1L);
        mockMvc.perform(get("/user/teacher/all").header("Authorization", promoted)).andExpect(status().isOk());
        mockMvc.perform(put("/user/teacher/update").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1001,\"active\":false}")).andExpect(status().isOk());
        mockMvc.perform(get("/user/me").header("Authorization", promoted)).andExpect(status().isUnauthorized());
        assertThat(cache.get(1001L).active()).isFalse();
        mockMvc.perform(put("/user/teacher/update").header("Authorization", teacher).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1001,\"active\":true}")).andExpect(status().isOk());
        assertThat(cache.get(1001L).active()).isTrue();
        assertThat(cache.get(1001L).tokenVersion()).isEqualTo(3);
    }
}
