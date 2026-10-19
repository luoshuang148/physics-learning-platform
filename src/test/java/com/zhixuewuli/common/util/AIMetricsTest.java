package com.zhixuewuli.common.util;
import com.zhixuewuli.common.metrics.AIMetrics;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
@Tag("unit")
class AIMetricsTest {
 @Test void availabilityComesFromObservedCallsRatherThanKeyPresence(){
  var metrics=new AIMetrics(new SimpleMeterRegistry());
  assertThat(metrics.lastStatus("aliyun").state()).isEqualTo("UNKNOWN");
  AIStreamCallback delegate=new AIStreamCallback(){public void onContent(String c,boolean l){} public void onComplete(int p,int c,int t){} public void onError(Exception error){}};
  metrics.observe("aliyun",delegate).onError(new com.zhixuewuli.common.exception.ai.AIUpstreamException(429));
  assertThat(metrics.lastStatus("aliyun").state()).isEqualTo("BUSY");
  metrics.observe("aliyun",delegate).onError(new com.zhixuewuli.common.exception.ai.AIUpstreamException(401));
  assertThat(metrics.lastStatus("aliyun").state()).isEqualTo("AUTH_ERROR");
  metrics.observe("aliyun",delegate).onComplete(1,1,2);
  assertThat(metrics.lastStatus("aliyun").state()).isEqualTo("AVAILABLE");
  assertThat(metrics.lastStatus("zhipu").state()).isEqualTo("UNKNOWN");
 }
 @Test void upstreamFailureIsRecordedOnceAndCallbackStillReceivesIt(){
  var registry=new SimpleMeterRegistry();var errors=new java.util.concurrent.atomic.AtomicInteger();
  var callback=new AIMetrics(registry).observe("aliyun",new AIStreamCallback(){public void onContent(String c,boolean l){}public void onComplete(int p,int c,int t){}public void onError(Exception error){errors.incrementAndGet();}});
  callback.onError(new RuntimeException("offline"));callback.onError(new RuntimeException("late duplicate"));
  assertThat(registry.find("ai.upstream.duration").tag("outcome","error").timer()).isNotNull();
  assertThat(registry.get("ai.upstream.duration").tag("outcome","error").timer().count()).isEqualTo(1);
  assertThat(errors.get()).isEqualTo(2);
 }
}
