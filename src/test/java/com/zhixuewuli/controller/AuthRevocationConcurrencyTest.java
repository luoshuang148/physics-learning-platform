package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.common.util.PasswordUtil;
import com.zhixuewuli.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class AuthRevocationConcurrencyTest extends BaseControllerIntegrationTest {
    @Autowired AuthService auth;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @SpyBean PasswordUtil passwords;

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
    @Sql(scripts="/sql/cleanup-all.sql",executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void loginCannotRestoreVersionReadBeforeLogout() throws Exception {
        var read=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(invocation -> {read.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return true;})
            .when(passwords).matches(eq("concurrent-fixture-password"),anyString());
        String old=createAuthHeader(1001L,"student_user");
        try(var worker=Executors.newSingleThreadExecutor()) {
            Future<?> login=worker.submit(()-> {try{auth.authenticate("student_user","concurrent-fixture-password");}
                catch(com.zhixuewuli.common.exception.auth.InvalidCredentialsException expected){}});
            try {
                assertThat(read.await(10,TimeUnit.SECONDS)).isTrue();
                auth.logout(1001L);
                assertThat(userRepository.findById(1001L).orElseThrow().getTokenVersion()).isEqualTo(1L);
            } finally {release.countDown();}
            login.get(10,TimeUnit.SECONDS);
        }
        assertThat(userRepository.findById(1001L).orElseThrow().getTokenVersion()).isEqualTo(1L);
        mockMvc.perform(get("/user/me").header("Authorization",old)).andExpect(status().isUnauthorized());
    }
    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    @SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
    @Sql(scripts="/sql/cleanup-all.sql",executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void profileWriteCannotRestoreAnUnchangedStaleSecurityVersion() throws Exception {
        var read=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var worker=Executors.newSingleThreadExecutor()) {
            var saved=worker.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                var user=userRepository.findById(1001L).orElseThrow();read.countDown();
                try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("barrier timed out");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}
                user.setRealName("updated profile");return userRepository.saveAndFlush(user);
            }));
            try {assertThat(read.await(10,TimeUnit.SECONDS)).isTrue();auth.logout(1001L);}finally{release.countDown();}
            saved.get(10,TimeUnit.SECONDS);
        }
        assertThat(userRepository.findById(1001L).orElseThrow().getTokenVersion()).isEqualTo(1L);
    }

}
