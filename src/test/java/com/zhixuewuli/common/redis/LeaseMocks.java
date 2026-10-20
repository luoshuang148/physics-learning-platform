package com.zhixuewuli.common.redis;

import com.zhixuewuli.common.exception.BusinessException;
import java.util.HashSet;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Stateful mock for local emitter lifecycle tests; Lua correctness is tested against Redis. */
public final class LeaseMocks {
    private LeaseMocks() { }
    public static RedisConnectionLeases create() {
        var leases = mock(RedisConnectionLeases.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
        var active = new HashSet<String>();
        when(leases.acquire(anyInt())).thenAnswer(call -> {
            synchronized (active) {
                if (active.size() >= (int) call.getArgument(0))
                    throw new BusinessException("系统繁忙，连接数已达上限，请稍后重试");
                String id = UUID.randomUUID().toString(); active.add(id); return id;
            }
        });
        when(leases.count()).thenAnswer(call -> { synchronized (active) { return active.size(); } });
        when(leases.claimUser(anyString(), anyLong(), anyInt())).thenReturn(true);
        when(leases.renew(anyString(), nullable(Long.class))).thenReturn(true);
        doAnswer(call -> { synchronized (active) { active.remove(call.getArgument(0)); } return null; })
                .when(leases).release(anyString(), nullable(Long.class));
        return leases;
    }
}
