package com.zhixuewuli.common.metrics;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class AIMetrics {
 private final MeterRegistry registry;
 public AIStreamCallback observe(String provider, AIStreamCallback delegate){
  long start=System.nanoTime();var done=new java.util.concurrent.atomic.AtomicBoolean();var first=new java.util.concurrent.atomic.AtomicBoolean();
  return new AIStreamCallback(){
   private void finish(String outcome){if(done.compareAndSet(false,true)) registry.timer("ai.upstream.duration","provider",provider,"outcome",outcome).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);}
   public void onContent(String text,boolean last){if(first.compareAndSet(false,true)) registry.timer("ai.upstream.first.content","provider",provider).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);delegate.onContent(text,last);}
   public void onComplete(int p,int c,int t){finish("success");delegate.onComplete(p,c,t);}
   public void onError(Exception error){finish("error");delegate.onError(error);}
   public void onProgress(int progress){delegate.onProgress(progress);}
   public void onReasoningContent(String text){delegate.onReasoningContent(text);}
   public boolean isCancelled(){boolean cancelled=delegate.isCancelled();if(cancelled)finish("cancelled");return cancelled;}
   public void onCancel(Runnable action){delegate.onCancel(()->{finish("cancelled");action.run();});}
  };
 }
}
