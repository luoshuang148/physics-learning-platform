package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.entity.KnowledgeRevision;
import com.zhixuewuli.repository.KnowledgeRevisionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class KnowledgeIndexRecoveryTest {
    @Test
    void schedulesNoMoreThanTwoWorkersAndSelectsExpiredLeases() throws Exception {
        var properties = new KnowledgeProperties();
        properties.setIndexWorkers(12);
        var revisions = mock(KnowledgeRevisionRepository.class);
        var indexing = mock(KnowledgeIndexService.class);
        when(revisions.findRunnableRevisionIds(eq(KnowledgeRevision.Status.UPLOADED),
                eq(KnowledgeRevision.Status.INDEXING), any(), any(PageRequest.class))).thenReturn(List.of(1L, 2L));
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            active.decrementAndGet();
            return null;
        }).when(indexing).indexRevision(anyLong());

        var recovery = new KnowledgeIndexRecovery(properties, revisions, indexing);
        try {
            recovery.tick();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(2, maxActive.get());
            verify(revisions).findRunnableRevisionIds(eq(KnowledgeRevision.Status.UPLOADED),
                    eq(KnowledgeRevision.Status.INDEXING), any(), argThat(page -> page.getPageSize() == 2));
        } finally {
            release.countDown();
            recovery.shutdown();
        }
    }
}
