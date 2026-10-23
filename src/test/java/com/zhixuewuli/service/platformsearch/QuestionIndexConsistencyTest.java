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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 题目索引一致性测试：修订递增、重试幂等、旧任务不发布、失败退避、删除清理。
 */
@Tag("unit")
@DisplayName("题库索引一致性")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionIndexConsistencyTest {

    @Mock QuestionRepository questions;
    @Mock QuestionSearchUnitRepository searchUnits;
    @Mock QuestionIndexJobRepository jobs;
    @Mock QuestionIndexPublicationRepository publications;
    @Mock QuestionSourceBuilder sourceBuilder;
    @Mock EmbeddingGateway embeddings;
    @Mock QuestionVectorIndex vectors;

    KnowledgeProperties properties;
    QuestionIndexWorker worker;

    @BeforeEach
    void setUp() {
        properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.setEmbeddingDimensions(4);
        properties.setEmbeddingModel("bge-small-zh-v1.5-quantized");
        worker = new QuestionIndexWorker(properties, questions, searchUnits, jobs, publications,
                sourceBuilder, embeddings, vectors);
    }

    private Question question(long revision, boolean aiEnabled) {
        Question q = new Question();
        q.setId(10L);
        q.setContent("题干");
        q.setOptions("{\"A\":\"a\",\"B\":\"b\"}");
        q.setExplanation("解析");
        q.setAiContentRevision(revision);
        q.setAiPracticeEnabled(aiEnabled);
        return q;
    }

    private QuestionIndexJob job(long revision, String type, QuestionIndexJob.Status status, int attempts) {
        QuestionIndexJob j = new QuestionIndexJob();
        j.setId(1L);
        j.setQuestionId(10L);
        j.setRevision(revision);
        j.setJobType(type);
        j.setStatus(status);
        j.setAttempts(attempts);
        return j;
    }

    private List<SearchUnit> sampleUnits(long revision) {
        return List.of(
                new SearchUnit(10L, revision, Part.QUESTION, 0, "题干文本", "h0", List.of(), 2),
                new SearchUnit(10L, revision, Part.EXPLANATION, 0, "解析文本", "h1", List.of(), 2));
    }

    private void stubHappyPath(long revision) {
        when(questions.findById(10L)).thenReturn(Optional.of(question(revision, true)));
        when(sourceBuilder.build(any())).thenReturn(sampleUnits(revision));
        when(embeddings.embed(anyList(), eq(false), any())).thenReturn(
                new EmbeddingBatch("m", 4, List.of(new float[]{1, 0, 0, 0}, new float[]{0, 1, 0, 0})));
        when(vectors.count(anyString())).thenReturn(2L);
        when(jobs.findById(1L)).thenReturn(Optional.of(job(revision, "INDEX", QuestionIndexJob.Status.PENDING, 0)));
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        when(jobs.renewLease(anyLong(), anyString(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("成功索引后写入向量并发布 READY")
    void publishReadyOnSuccess() {
        stubHappyPath(3L);
        worker.process(1L);

        verify(vectors).ensureCollection(anyString(), eq(4));
        verify(vectors).upsert(anyString(), anyList());
        ArgumentCaptor<QuestionIndexJob> captor = ArgumentCaptor.forClass(QuestionIndexJob.class);
        verify(jobs, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(j -> j.getStatus() == QuestionIndexJob.Status.READY));
        verify(publications).save(any(QuestionIndexPublication.class));
    }

    @Test
    @DisplayName("pointId 确定性生成，重试幂等不产生重复点")
    void pointIdDeterministic() {
        String a = QuestionIndexWorker.pointId(10L, 3L, Part.EXPLANATION, 0);
        String b = QuestionIndexWorker.pointId(10L, 3L, Part.EXPLANATION, 0);
        assertEquals(a, b);
        assertNotEquals(a, QuestionIndexWorker.pointId(10L, 3L, Part.EXPLANATION, 1));
        assertNotEquals(a, QuestionIndexWorker.pointId(10L, 4L, Part.EXPLANATION, 0));
    }

    @Test
    @DisplayName("当前题 revision 已变化：任务标 OBSOLETE，不发布")
    void obsoleteWhenRevisionChanged() {
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        when(jobs.findById(1L)).thenReturn(Optional.of(job(3L, "INDEX", QuestionIndexJob.Status.PENDING, 0)));
        // 题目已变成 revision=4
        when(questions.findById(10L)).thenReturn(Optional.of(question(4L, true)));

        worker.process(1L);

        ArgumentCaptor<QuestionIndexJob> captor = ArgumentCaptor.forClass(QuestionIndexJob.class);
        verify(jobs, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(j -> j.getStatus() == QuestionIndexJob.Status.OBSOLETE));
        verify(vectors, never()).upsert(anyString(), anyList());
        verify(publications, never()).save(any());
    }

    @Test
    @DisplayName("未启用 AI 补练：不进入有效索引")
    void notIndexedWhenDisabled() {
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        when(jobs.findById(1L)).thenReturn(Optional.of(job(3L, "INDEX", QuestionIndexJob.Status.PENDING, 0)));
        when(questions.findById(10L)).thenReturn(Optional.of(question(3L, false)));

        worker.process(1L);

        verify(vectors, never()).upsert(anyString(), anyList());
        verify(publications, never()).save(any());
    }

    @Test
    @DisplayName("向量失败最多 3 次尝试，之后 FAILED 并记录 failureCode")
    void failAfterMaxAttempts() {
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        // claim 会 attempts+1：第 3 次尝试失败 → FAILED
        QuestionIndexJob afterClaim = job(3L, "INDEX", QuestionIndexJob.Status.INDEXING, 3);
        when(jobs.findById(1L)).thenReturn(Optional.of(afterClaim));
        when(questions.findById(10L)).thenReturn(Optional.of(question(3L, true)));
        when(sourceBuilder.build(any())).thenReturn(sampleUnits(3L));
        when(embeddings.embed(anyList(), eq(false), any()))
                .thenThrow(new com.zhixuewuli.service.knowledge.KnowledgeEmbeddingException("boom"));

        worker.process(1L);

        ArgumentCaptor<QuestionIndexJob> captor = ArgumentCaptor.forClass(QuestionIndexJob.class);
        verify(jobs, atLeastOnce()).save(captor.capture());
        QuestionIndexJob last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(QuestionIndexJob.Status.FAILED, last.getStatus());
        assertEquals("EMBEDDING", last.getFailureCode());
        verify(publications, never()).save(any());
    }

    @Test
    @DisplayName("第一次失败退避 5 秒后回到 PENDING")
    void backoffAfterFirstFailure() {
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        // claim 后 attempts=1：首次失败退避
        QuestionIndexJob afterClaim = job(3L, "INDEX", QuestionIndexJob.Status.INDEXING, 1);
        when(jobs.findById(1L)).thenReturn(Optional.of(afterClaim));
        when(questions.findById(10L)).thenReturn(Optional.of(question(3L, true)));
        when(sourceBuilder.build(any())).thenReturn(sampleUnits(3L));
        when(embeddings.embed(anyList(), eq(false), any()))
                .thenThrow(new com.zhixuewuli.service.knowledge.KnowledgeEmbeddingException("boom"));

        worker.process(1L);

        ArgumentCaptor<QuestionIndexJob> captor = ArgumentCaptor.forClass(QuestionIndexJob.class);
        verify(jobs, atLeastOnce()).save(captor.capture());
        QuestionIndexJob last = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(QuestionIndexJob.Status.PENDING, last.getStatus());
        assertNotNull(last.getLeaseExpiresAt());
        assertTrue(last.getLeaseExpiresAt().isAfter(LocalDateTime.now().plusSeconds(2)));
    }

    @Test
    @DisplayName("删除任务清理搜索单元与发布记录")
    void deleteCleansUp() {
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        QuestionIndexJob deleteJob = job(3L, "DELETE", QuestionIndexJob.Status.PENDING, 0);
        when(jobs.findById(1L)).thenReturn(Optional.of(deleteJob));

        worker.process(1L);

        verify(searchUnits).deleteByQuestionId(10L);
        verify(publications).deleteByQuestionId(10L);
        verify(vectors).deleteQuestion(anyString(), eq(10L), eq(3L));
        ArgumentCaptor<QuestionIndexJob> captor = ArgumentCaptor.forClass(QuestionIndexJob.class);
        verify(jobs, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(j -> j.getStatus() == QuestionIndexJob.Status.DELETED));
    }

    @Test
    @DisplayName("发布前再次校验 revision；晚完成的旧任务不覆盖新版本")
    void lateJobDoesNotPublish() {
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        QuestionIndexJob staleJob = job(3L, "INDEX", QuestionIndexJob.Status.PENDING, 0);
        when(jobs.findById(1L)).thenReturn(Optional.of(staleJob));
        // 构建与向量写成功，但发布时题目已变
        AtomicBoolean flip = new AtomicBoolean(false);
        when(questions.findById(10L)).thenAnswer(inv -> {
            if (flip.get()) return Optional.of(question(5L, true)); // 已变更新
            return Optional.of(question(3L, true));
        });
        when(sourceBuilder.build(any())).thenReturn(sampleUnits(3L));
        when(embeddings.embed(anyList(), eq(false), any())).thenReturn(
                new EmbeddingBatch("m", 4, List.of(new float[]{1, 0, 0, 0}, new float[]{0, 1, 0, 0})));
        when(vectors.count(anyString())).thenReturn(2L);
        doAnswer(inv -> {
            flip.set(true);
            return null;
        }).when(vectors).upsert(anyString(), anyList());

        worker.process(1L);

        verify(publications, never()).save(any());
        ArgumentCaptor<QuestionIndexJob> captor = ArgumentCaptor.forClass(QuestionIndexJob.class);
        verify(jobs, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(j -> j.getStatus() == QuestionIndexJob.Status.OBSOLETE));
    }

    @Test
    @DisplayName("enqueue 幂等：同 questionId+revision+type 只保留一行")
    void enqueueIdempotent() {
        QuestionSourceIndexService service = new QuestionSourceIndexService(jobs, searchUnits);
        when(jobs.findByQuestionIdAndRevisionAndJobType(10L, 3L, "INDEX"))
                .thenReturn(Optional.empty());
        service.enqueueCurrent(10L, 3L);
        verify(jobs, times(1)).save(any());
    }

    @Test
    @DisplayName("Embedding 批大小不匹配时不发布")
    void embeddingSizeMismatchRejected() {
        when(jobs.claim(anyLong(), anyString(), any(), any(), anyInt())).thenReturn(1);
        when(jobs.findById(1L)).thenReturn(Optional.of(job(3L, "INDEX", QuestionIndexJob.Status.PENDING, 0)));
        when(questions.findById(10L)).thenReturn(Optional.of(question(3L, true)));
        when(sourceBuilder.build(any())).thenReturn(sampleUnits(3L));
        when(embeddings.embed(anyList(), eq(false), any())).thenReturn(
                new EmbeddingBatch("m", 4, List.of(new float[]{1, 0, 0, 0}))); // 少一个

        worker.process(1L);

        verify(vectors, never()).upsert(anyString(), anyList());
        verify(publications, never()).save(any());
    }
}
