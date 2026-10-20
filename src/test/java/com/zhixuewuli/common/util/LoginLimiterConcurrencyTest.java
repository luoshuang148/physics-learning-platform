package com.zhixuewuli.common.util;
import com.zhixuewuli.common.security.LoginAttemptLimiter;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
@Tag("unit")
class LoginLimiterConcurrencyTest {
 @Test void concurrentAttemptsConsumeAdmissionBeforeAuthenticationCompletes() throws Exception {
  var redis=org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
  var attempts=new AtomicInteger();
  org.mockito.Mockito.when(redis.execute(org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.script.RedisScript<Long>>any(),
      org.mockito.ArgumentMatchers.anyList())).thenAnswer(call -> attempts.incrementAndGet() <= 5 ? 1L : 0L);
  var limiter=new LoginAttemptLimiter(redis, "test:");var admitted=new AtomicInteger();
  try(var workers=Executors.newFixedThreadPool(12)) {
   var start=new CountDownLatch(1);var pending=new java.util.ArrayList<Future<?>>();
   for(int n=0;n<12;n++) pending.add(workers.submit(()->{try{start.await();limiter.check("peer","same-user");admitted.incrementAndGet();}catch(LoginAttemptLimiter.LoginThrottledException expected){}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
   start.countDown();for(var job:pending)job.get(5,TimeUnit.SECONDS);
  }
  assertThat(admitted.get()).isEqualTo(5);
 }
}
