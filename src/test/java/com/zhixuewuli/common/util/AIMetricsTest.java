package com.zhixuewuli.common.util;
import com.zhixuewuli.common.metrics.AIMetrics;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
@Tag("unit")
class AIMetricsTest {
 @Test void upstreamFailureIsRecordedOnceAndCallbackStillReceivesIt(){
  var registry=new SimpleMeterRegistry();var errors=new java.util.concurrent.atomic.AtomicInteger();
  var callback=new AIMetrics(registry).observe("aliyun",new AIStreamCallback(){public void onContent(String c,boolean l){}public void onComplete(int p,int c,int t){}public void onError(Exception error){errors.incrementAndGet();}});
  callback.onError(new RuntimeException("offline"));callback.onError(new RuntimeException("late duplicate"));
  assertThat(registry.find("ai.upstream.duration").tag("outcome","error").timer()).isNotNull();
  assertThat(registry.get("ai.upstream.duration").tag("outcome","error").timer().count()).isEqualTo(1);
  assertThat(errors.get()).isEqualTo(2);
 }
}
