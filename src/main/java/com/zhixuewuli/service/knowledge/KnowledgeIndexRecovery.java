package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.entity.KnowledgeRevision;
import com.zhixuewuli.repository.KnowledgeRevisionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "app.knowledge", name = "enabled", havingValue = "true")
public class KnowledgeIndexRecovery {
    private final KnowledgeRevisionRepository revisions;
    private final KnowledgeIndexService indexing;
    private final ThreadPoolExecutor workers;

    public KnowledgeIndexRecovery(KnowledgeProperties properties, KnowledgeRevisionRepository revisions,
                                  KnowledgeIndexService indexing) {
        this.revisions = revisions;
        this.indexing = indexing;
        int count = Math.max(1, Math.min(properties.getIndexWorkers(), 2));
        this.workers = new ThreadPoolExecutor(count, count, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), runnable -> {
                    var thread = new Thread(runnable, "knowledge-index-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Scheduled(fixedDelayString = "${app.knowledge.poll-interval-ms:5000}")
    public void tick() {
        int available = Math.max(0, workers.getCorePoolSize() - workers.getActiveCount() - workers.getQueue().size());
        if (available == 0) return;
        var now = LocalDateTime.now();
        var ids = revisions.findRunnableRevisionIds(KnowledgeRevision.Status.UPLOADED,
                KnowledgeRevision.Status.INDEXING, now, PageRequest.of(0, available));
        for (Long id : ids) {
            try {
                workers.execute(() -> indexing.indexRevision(id));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                break;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        workers.shutdown();
    }
}
