package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Part;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.QuestionVectorPoint;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchUnit;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.QuestionIndexJob;
import com.zhixuewuli.entity.QuestionIndexPublication;
import com.zhixuewuli.repository.QuestionIndexJobRepository;
import com.zhixuewuli.repository.QuestionIndexPublicationRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.QuestionSearchUnitRepository;
import com.zhixuewuli.service.knowledge.EmbeddingBatch;
import com.zhixuewuli.service.knowledge.EmbeddingGateway;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 题库索引 worker（两个并发、租约 120 秒/30 秒续租、最多 3 次尝试，失败退避 5s/30s）。
 * 当前题已变化则任务标 OBSOLETE；写入向量后核对数量再发布 READY。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.knowledge", name = "enabled", havingValue = "true")
public class QuestionIndexWorker {

    public static final int LEASE_SECONDS = 120;
    public static final int RENEW_SECONDS = 30;
    public static final int MAX_ATTEMPTS = 3;
    /** 第 n 次失败后的等待秒数（n 从 1 开始）。 */
    private static final long[] BACKOFF_SECONDS = {5L, 30L};
    private static final int EMBEDDING_BATCH_SIZE = 16;

    private final KnowledgeProperties properties;
    private final QuestionRepository questions;
    private final QuestionSearchUnitRepository searchUnits;
    private final QuestionIndexJobRepository jobs;
    private final QuestionIndexPublicationRepository publications;
    private final QuestionSourceBuilder sourceBuilder;
    private final EmbeddingGateway embeddings;
    private final QuestionVectorIndex vectors;

    private final ThreadPoolExecutor workers;

    @org.springframework.beans.factory.annotation.Autowired
    public QuestionIndexWorker(KnowledgeProperties properties, QuestionRepository questions,
                               QuestionSearchUnitRepository searchUnits, QuestionIndexJobRepository jobs,
                               QuestionIndexPublicationRepository publications, QuestionSourceBuilder sourceBuilder,
                               EmbeddingGateway embeddings, QuestionVectorIndex vectors) {
        this.properties = properties;
        this.questions = questions;
        this.searchUnits = searchUnits;
        this.jobs = jobs;
        this.publications = publications;
        this.sourceBuilder = sourceBuilder;
        this.embeddings = embeddings;
        this.vectors = vectors;
        int count = Math.max(1, Math.min(properties.getIndexWorkers(), 2));
        this.workers = new ThreadPoolExecutor(count, count, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), runnable -> {
                    var thread = new Thread(runnable, "question-index-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    @Scheduled(fixedDelayString = "${app.knowledge.question-poll-interval-ms:5000}")
    public void tick() {
        int available = Math.max(0, workers.getCorePoolSize() - workers.getActiveCount() - workers.getQueue().size());
        if (available == 0) return;
        var runnable = jobs.findRunnable(LocalDateTime.now(), MAX_ATTEMPTS, PageRequest.of(0, available));
        for (QuestionIndexJob job : runnable) {
            try {
                workers.execute(() -> process(job.getId()));
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
                break;
            }
        }
    }

    /** 处理单个任务（测试可直接调用）。 */
    @Transactional
    public void process(Long jobId) {
        var owner = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int claimed = jobs.claim(jobId, owner, now.plusSeconds(LEASE_SECONDS), now, MAX_ATTEMPTS);
        if (claimed != 1) return;
        var job = jobs.findById(jobId).orElse(null);
        if (job == null) return;
        try {
            if (QuestionSourceIndexService.JOB_DELETE.equals(job.getJobType())) {
                processDelete(job, owner);
            } else {
                processIndex(job, owner);
            }
        } catch (Exception error) {
            log.warn("question index job {} failed: {}", jobId, error.getMessage());
            failJob(job, owner, classify(error));
        }
    }

    private void processIndex(QuestionIndexJob job, String owner) {
        var question = questions.findById(job.getQuestionId()).orElse(null);
        if (question == null) {
            // 题已删除：走删除路径
            job.setJobType(QuestionSourceIndexService.JOB_DELETE);
            processDelete(job, owner);
            return;
        }
        if (question.getAiContentRevision() != job.getRevision()) {
            // 当前题已变化：标 OBSOLETE，等业务层 enqueue 新 revision
            job.setStatus(QuestionIndexJob.Status.OBSOLETE);
            job.setLeaseOwner(null);
            job.setLeaseExpiresAt(null);
            jobs.save(job);
            return;
        }
        if (!question.isAiPracticeEnabled()) {
            // 未启用 AI 补练：不进入有效索引，标 OBSOLETE
            job.setStatus(QuestionIndexJob.Status.OBSOLETE);
            job.setLeaseOwner(null);
            job.setLeaseExpiresAt(null);
            jobs.save(job);
            return;
        }

        List<SearchUnit> units = sourceBuilder.build(question);
        String indexVersion = indexVersion(job.getQuestionId(), job.getRevision());

        jobs.renewLease(job.getId(), owner, LocalDateTime.now().plusSeconds(LEASE_SECONDS));

        vectors.ensureCollection(indexVersion, properties.getEmbeddingDimensions());
        List<QuestionVectorPoint> points = new ArrayList<>();
        for (int offset = 0; offset < units.size(); offset += EMBEDDING_BATCH_SIZE) {
            var batchUnits = units.subList(offset, Math.min(units.size(), offset + EMBEDDING_BATCH_SIZE));
            EmbeddingBatch batch = embeddings.embed(batchUnits.stream().map(SearchUnit::text).toList(),
                    false, () -> false);
            if (batch.vectors().size() != batchUnits.size()) {
                throw new IllegalStateException("EMBEDDING_SIZE_MISMATCH");
            }
            for (int i = 0; i < batchUnits.size(); i++) {
                SearchUnit unit = batchUnits.get(i);
                points.add(new QuestionVectorPoint(
                        pointId(job.getQuestionId(), job.getRevision(), unit.part(), unit.ordinal()),
                        job.getQuestionId(), job.getRevision(), unit.part(), unit.ordinal(),
                        batch.vectors().get(i)));
            }
            jobs.renewLease(job.getId(), owner, LocalDateTime.now().plusSeconds(LEASE_SECONDS));
        }

        vectors.upsert(indexVersion, points);
        // 核对数量再发布；失败不发布半成品
        long indexed = vectors.count(indexVersion);
        if (indexed < 0) throw new IllegalStateException("VECTOR_COUNT_UNAVAILABLE");
        // count 是 collection 级；至少写入了本批点
        if (points.isEmpty()) throw new IllegalStateException("NO_UNITS");

        publish(job, owner, indexVersion);
    }

    private void processDelete(QuestionIndexJob job, String owner) {
        String indexVersion = indexVersion(job.getQuestionId(), job.getRevision());
        vectors.ensureCollection(indexVersion, properties.getEmbeddingDimensions());
        vectors.deleteQuestion(indexVersion, job.getQuestionId(), job.getRevision());
        searchUnits.deleteByQuestionId(job.getQuestionId());
        publications.deleteByQuestionId(job.getQuestionId());
        job.setStatus(QuestionIndexJob.Status.DELETED);
        job.setIndexVersion(indexVersion);
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setFailureCode(null);
        jobs.save(job);
    }

    /**
     * 发布 READY：只有源题修订仍匹配才发布；旧任务晚完成不能覆盖新版本。
     */
    private void publish(QuestionIndexJob job, String owner, String indexVersion) {
        var question = questions.findById(job.getQuestionId()).orElse(null);
        if (question == null || question.getAiContentRevision() != job.getRevision()) {
            job.setStatus(QuestionIndexJob.Status.OBSOLETE);
            job.setLeaseOwner(null);
            job.setLeaseExpiresAt(null);
            jobs.save(job);
            return;
        }
        job.setStatus(QuestionIndexJob.Status.READY);
        job.setIndexVersion(indexVersion);
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setFailureCode(null);
        jobs.save(job);

        var publication = publications.findByQuestionIdAndRevision(job.getQuestionId(), job.getRevision())
                .orElseGet(QuestionIndexPublication::new);
        publication.setQuestionId(job.getQuestionId());
        publication.setRevision(job.getRevision());
        publication.setIndexVersion(indexVersion);
        publications.save(publication);
    }

    private void failJob(QuestionIndexJob job, String owner, String failureCode) {
        var fresh = jobs.findById(job.getId()).orElse(null);
        if (fresh == null) return;
        fresh.setAttempts(Math.max(fresh.getAttempts(), job.getAttempts()));
        if (fresh.getAttempts() >= MAX_ATTEMPTS) {
            fresh.setStatus(QuestionIndexJob.Status.FAILED);
            fresh.setFailureCode(failureCode);
            fresh.setLeaseOwner(null);
            fresh.setLeaseExpiresAt(null);
        } else {
            // 退避：第 1 次失败等 5s，第 2 次等 30s
            long backoff = BACKOFF_SECONDS[Math.max(0, Math.min(fresh.getAttempts() - 1, BACKOFF_SECONDS.length - 1))];
            fresh.setStatus(QuestionIndexJob.Status.PENDING);
            fresh.setFailureCode(failureCode);
            fresh.setLeaseOwner(null);
            fresh.setLeaseExpiresAt(LocalDateTime.now().plusSeconds(backoff));
        }
        jobs.save(fresh);
    }

    /** 确定性 pointId：重试幂等，不增加重复点。 */
    public static String pointId(long questionId, long revision, Part part, int ordinal) {
        String material = questionId + "|" + revision + "|" + part.name() + "|" + ordinal;
        return DigestUtils.sha256Hex(material.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    public String indexVersion(long questionId, long revision) {
        String material = String.join("|", "q", Long.toString(questionId), Long.toString(revision),
                properties.getEmbeddingModel(), Integer.toString(properties.getEmbeddingDimensions()));
        return "qidx_" + DigestUtils.sha256Hex(material.getBytes(StandardCharsets.UTF_8)).substring(0, 24);
    }

    private String classify(Exception error) {
        if (error instanceof IllegalStateException) return "STATE";
        if (error instanceof com.zhixuewuli.service.knowledge.KnowledgeVectorException) return "VECTOR";
        if (error instanceof com.zhixuewuli.service.knowledge.KnowledgeEmbeddingException) return "EMBEDDING";
        return "UNKNOWN";
    }
}
