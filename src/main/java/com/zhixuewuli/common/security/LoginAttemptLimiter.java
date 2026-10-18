package com.zhixuewuli.common.security;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;
/** Single-JVM bounded attempt admission; identity is the direct peer, never an untrusted forwarding header. */
@Component
public class LoginAttemptLimiter {
 private record Failures(int count,long first) {}
 private final Clock clock;
 private final Map<String,Failures> accounts=new LinkedHashMap<>(), peers=new LinkedHashMap<>();
 public LoginAttemptLimiter(){this(Clock.systemUTC());}
 public LoginAttemptLimiter(Clock clock){this.clock=clock;}
 private String key(String ip,String username){return ip+":"+username.trim().toLowerCase(Locale.ROOT);}
 public synchronized void check(String ip,String username){
  prune(accounts);prune(peers);
  if(accounts.getOrDefault(key(ip,username),new Failures(0,0)).count()>=5 || peers.getOrDefault(ip,new Failures(0,0)).count()>=200)
   throw new LoginThrottledException();
  add(accounts,key(ip,username));add(peers,ip);
 }
 public synchronized void succeeded(String ip,String username){accounts.remove(key(ip,username));}
 private void prune(Map<String,Failures> map){long now=clock.millis();map.values().removeIf(f->now-f.first()>=60_000);}
 private void add(Map<String,Failures> map,String key){
  prune(map);if(!map.containsKey(key)&&map.size()>=4096)map.remove(map.keySet().iterator().next());
  Failures old=map.get(key);map.put(key,old==null?new Failures(1,clock.millis()):new Failures(old.count()+1,old.first()));
 }
 public static final class LoginThrottledException extends RuntimeException {public LoginThrottledException(){super("登录尝试过多，请一分钟后重试");}}
}
