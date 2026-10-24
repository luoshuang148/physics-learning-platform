package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.common.config.properties.PlatformSearchProperties;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.*;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.QuestionSearchUnit;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.QuestionSearchUnitRepository;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.TeacherEventQuestionRepository;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.service.knowledge.EmbeddingBatch;
import com.zhixuewuli.service.knowledge.EmbeddingGateway;
import com.zhixuewuli.service.question.AnswerAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 平台题库授权检索测试：候选约束、同题去重、权限拒绝、降级与状态。
 */
@Tag("unit")
@DisplayName("平台题库授权检索")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformQuestionRetrievalTest {

    @Mock QuestionRepository questions;
    @Mock AnswerRecordRepository answers;
    @Mock TeacherEventQuestionRepository taskQuestions;
    @Mock TeacherEventRepository tasks;
    @Mock AnswerAccessPolicy answerAccess;
    @Mock QuestionSearchUnitRepository searchUnits;
    @Mock QuestionVectorIndex vectorIndex;
    @Mock EmbeddingGateway embeddings;

    QuestionRetrievalAccessPolicy accessPolicy;
    QuestionKeywordSearch keywordSearch;
    PlatformQuestionRetriever retriever;

    @BeforeEach
    void setUp() {
        accessPolicy = new QuestionRetrievalAccessPolicy(questions, answers, taskQuestions, tasks, answerAccess);
        keywordSearch = new QuestionKeywordSearch(searchUnits);
        var properties = new PlatformSearchProperties();
        properties.setAllowLexicalFallback(false);
        retriever = new PlatformQuestionRetriever(accessPolicy, keywordSearch, vectorIndex,
                embeddings, questions, searchUnits, properties,
                new com.zhixuewuli.common.config.properties.KnowledgeProperties());
        when(answerAccess.isTeacher(anyLong())).thenReturn(false);
        when(tasks.findPublishedTasks()).thenReturn(List.of());
    }

    private Question question(long id, long revision, boolean aiEnabled) {
        Question q = new Question();
        q.setId(id);
        q.setContent("关于作用力与反作用力的题目 " + id);
        q.setOptions("{\"A\":\"a\",\"B\":\"b\"}");
        q.setCorrectOption("A");
        q.setExplanation("解析");
        q.setDifficulty(2);
        q.setAiPracticeEnabled(aiEnabled);
        q.setAiContentRevision(revision);
        return q;
    }

    private QuestionSearchUnit unit(long qid, long rev, String part, int ordinal, String text) {
        QuestionSearchUnit u = new QuestionSearchUnit();
        u.setQuestionId(qid);
        u.setRevision(rev);
        u.setPart(part);
        u.setOrdinalNo(ordinal);
        u.setUnitText(text);
        u.setContentHash("h" + qid + ordinal);
        return u;
    }

    private SearchRequest request(String query) {
        return new SearchRequest(query, null, List.of(), null, false, 5, RetrievalMode.HYBRID);
    }

    @Test
    @DisplayName("未启用 AI 的题不进入候选")
    void disabledQuestionExcluded() {
        when(questions.findAll()).thenReturn(List.of(question(1L, 1L, false)));
        var eligible = accessPolicy.eligible(100L, request("力"));
        assertTrue(eligible.isEmpty());
    }

    @Test
    @DisplayName("excludeAttempted 过滤本人已作答题")
    void excludeAttemptedFilters() {
        when(questions.findAll()).thenReturn(List.of(question(1L, 1L, true), question(2L, 1L, true)));
        when(answers.findByUserIdOrderByAnsweredAtDesc(100L)).thenReturn(List.of());
        var eligible = accessPolicy.eligible(100L,
                new SearchRequest("力", null, List.of(), null, true, 5, RetrievalMode.HYBRID));
        assertEquals(2, eligible.size());

        var record = new com.zhixuewuli.entity.AnswerRecord();
        record.setQuestionId(1L);
        when(answers.findByUserIdOrderByAnsweredAtDesc(100L)).thenReturn(List.of(record));
        eligible = accessPolicy.eligible(100L,
                new SearchRequest("力", null, List.of(), null, true, 5, RetrievalMode.HYBRID));
        assertEquals(1, eligible.size());
        assertEquals(2L, eligible.get(0).questionId());
    }

    @Test
    @DisplayName("待考题保护：未首次作答的作业题不进入候选")
    void pendingAssignmentProtected() {
        when(questions.findAll()).thenReturn(List.of(question(1L, 1L, true)));
        // 模拟进行中作业包含题目 1，本人未完成
        var task = new com.zhixuewuli.entity.TeacherEvent();
        task.setId(9L);
        when(tasks.findPublishedTasks()).thenReturn(List.of(task));
        when(taskQuestions.findQuestionIdsByEventId(9L)).thenReturn(List.of(1L));
        when(answers.findCompletedAssignmentQuestionIds(100L, 9L)).thenReturn(Set.of());

        assertTrue(accessPolicy.eligible(100L, request("力")).isEmpty());
        assertFalse(accessPolicy.isEligible(100L, 1L, 1L));
    }

    @Test
    @DisplayName("同题多段只计一道题")
    void multiSegmentCountsOnce() {
        when(questions.findAll()).thenReturn(List.of(question(1L, 1L, true)));
        when(questions.findById(1L)).thenReturn(Optional.of(question(1L, 1L, true)));
        when(searchUnits.searchByFullText(anyString(), anyList(), anyInt())).thenReturn(List.of(
                unit(1L, 1L, "QUESTION", 0, "作用力与反作用力"),
                unit(1L, 1L, "EXPLANATION", 0, "牛顿第三定律解析")));
        when(embeddings.embed(anyList(), eq(true), any())).thenReturn(
                new EmbeddingBatch("m", 4, List.of(new float[]{1, 0, 0, 0})));
        when(vectorIndex.search(anyString(), anyList(), any(), anyInt(), anyDouble())).thenReturn(List.of());

        var result = retriever.retrieve(100L, request("作用力与反作用力"), () -> false);
        assertEquals(RetrievalStatus.MATCHED, result.status());
        assertEquals(1, result.questions().size(), "同题多段只应返回 1 道题");
        assertTrue(result.sources().size() <= 5);
    }

    @Test
    @DisplayName("向量故障且不允许降级 → UNAVAILABLE，不假装无资料")
    void vectorFailureUnavailable() {
        when(questions.findAll()).thenReturn(List.of(question(1L, 1L, true)));
        when(searchUnits.searchByFullText(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(embeddings.embed(anyList(), eq(true), any()))
                .thenThrow(new IllegalStateException("embedding down"));

        var result = retriever.retrieve(100L, request("作用力"), () -> false);
        assertEquals(RetrievalStatus.UNAVAILABLE, result.status());
        assertTrue(result.questions().isEmpty());
    }

    @Test
    @DisplayName("向量故障但允许降级 → lexical_only")
    void vectorFailureLexicalFallback() {
        var properties = new PlatformSearchProperties();
        properties.setAllowLexicalFallback(true);
        retriever = new PlatformQuestionRetriever(accessPolicy, keywordSearch, vectorIndex,
                embeddings, questions, searchUnits, properties,
                new com.zhixuewuli.common.config.properties.KnowledgeProperties());
        when(questions.findAll()).thenReturn(List.of(question(1L, 1L, true)));
        when(questions.findById(1L)).thenReturn(Optional.of(question(1L, 1L, true)));
        // FULLTEXT 不可用 → 走 LIKE 适配器
        when(searchUnits.searchByFullText(anyString(), anyList(), anyInt()))
                .thenThrow(new RuntimeException("fulltext unsupported"));
        when(searchUnits.searchByTokenLike(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(unit(1L, 1L, "QUESTION", 0, "作用力与反作用力")));
        when(embeddings.embed(anyList(), eq(true), any()))
                .thenThrow(new IllegalStateException("embedding down"));

        var result = retriever.retrieve(100L, request("作用力与反作用力"), () -> false);
        assertEquals("lexical_only", result.actualMode());
        assertEquals(RetrievalStatus.MATCHED, result.status());
    }

    @Test
    @DisplayName("无匹配内容 → INSUFFICIENT 且返回真实数量 0")
    void noMatchInsufficient() {
        when(questions.findAll()).thenReturn(List.of(question(1L, 1L, true)));
        when(searchUnits.searchByFullText(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(embeddings.embed(anyList(), eq(true), any())).thenReturn(
                new EmbeddingBatch("m", 4, List.of(new float[]{1, 0, 0, 0})));
        when(vectorIndex.search(anyString(), anyList(), any(), anyInt(), anyDouble())).thenReturn(List.of());

        var result = retriever.retrieve(100L, request("完全无关的查询"), () -> false);
        assertEquals(RetrievalStatus.INSUFFICIENT, result.status());
        assertTrue(result.questions().isEmpty());
    }

    @Test
    @DisplayName("RRF 融合按 k=60，两路命中分数更高")
    void rrfFusionBoostsBothHits() {
        var shared = unit(1L, 1L, "QUESTION", 0, "共同命中");
        var onlyKeyword = unit(2L, 1L, "QUESTION", 0, "仅关键词");
        var fused = PlatformQuestionRetriever.rrfFuse(
                List.of(shared, onlyKeyword), List.of(shared), 60, 5);
        assertEquals(2, fused.size());
        assertEquals("Q:1:1:QUESTION:0", fused.get(0).sourceId(), "双路命中应排前面");
    }

    @Test
    @DisplayName("来源 ID 解析：非法 ID 不泄露")
    void sourceIdParse() {
        assertTrue(PlatformQuestionRetriever.parseSourceId("Q:1:1:QUESTION:0").isPresent());
        assertTrue(PlatformQuestionRetriever.parseSourceId("Q:1:1:BOGUS:0").isEmpty());
        assertTrue(PlatformQuestionRetriever.parseSourceId("evil").isEmpty());
        assertTrue(PlatformQuestionRetriever.parseSourceId(null).isEmpty());
    }

    @Test
    @DisplayName("来源查看跨用户/未授权被拒绝且不泄露内容")
    void sourceAuthorizationDenied() {
        var sourceService = new PlatformSourceService(searchUnits, accessPolicy);
        when(questions.findById(1L)).thenReturn(Optional.of(question(1L, 1L, true)));
        // 用户 200 因待考题保护无权
        var task = new com.zhixuewuli.entity.TeacherEvent();
        task.setId(9L);
        when(tasks.findPublishedTasks()).thenReturn(List.of(task));
        when(taskQuestions.findQuestionIdsByEventId(9L)).thenReturn(List.of(1L));
        when(answers.findCompletedAssignmentQuestionIds(200L, 9L)).thenReturn(Set.of());

        var error = assertThrows(com.zhixuewuli.common.exception.BusinessException.class,
                () -> sourceService.requireReadable(200L, "Q:1:1:QUESTION:0"));
        assertFalse(error.getMessage().contains("作用力"), "错误信息不得泄露题干");
    }
}
