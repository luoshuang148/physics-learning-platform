package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.repository.KnowledgeDocumentRepository;
import com.zhixuewuli.repository.KnowledgeChunkRepository;
import com.zhixuewuli.repository.ActiveKnowledgeIndexRow;
import com.zhixuewuli.dto.knowledge.KnowledgeChunkView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit") class KnowledgeRetrievalTest {
    private final TeacherEventRepository events = mock(TeacherEventRepository.class);
    private final KnowledgeAccessPolicy access = mock(KnowledgeAccessPolicy.class);
    private final KnowledgeDocumentService documents = mock(KnowledgeDocumentService.class);
    private final KnowledgeDocumentRepository documentsRepo = mock(KnowledgeDocumentRepository.class);
    private final KeywordKnowledgeSearch keyword = mock(KeywordKnowledgeSearch.class);
    private final EmbeddingGateway embeddings = mock(EmbeddingGateway.class);
    private final KnowledgeVectorStore vectors = mock(KnowledgeVectorStore.class);
    private final KnowledgeProperties properties = enabledProperties();
    private final KnowledgeRetriever retriever = new KnowledgeRetriever(events, access, documents,
            documentsRepo, keyword, embeddings, vectors, new RetrievalPolicy(), properties);

    @Test void scope_is_applied_to_both_branches() {
        when(events.findById(9L)).thenReturn(Optional.of(event(30L)));
        when(keyword.search(30L, "牛顿第二定律", 10)).thenReturn(List.of(hit(1, 10, 20, 30, 40, 1, .8)));
        when(documentsRepo.findActiveIndexesByClassId(30L)).thenReturn(List.of(new ActiveKnowledgeIndexRow(10L,30L,20L,"v1")));
        when(embeddings.embed(anyList(),anyBoolean(),any())).thenReturn(new EmbeddingBatch(properties.getEmbeddingModel(),512,List.of(new float[512])));
        when(vectors.search(eq("v1"),eq(30L),eq(20L),any(),eq(10))).thenReturn(List.of(new KnowledgeVectorHit(java.util.UUID.randomUUID(),1L,20L,30L,1,.7)));
        when(documents.requireActiveReadable(7L,1L)).thenReturn(new KnowledgeChunkView(1L,20L,"题目",1,"力学","正文"));
        var result = retriever.retrieve(7L,9L,"牛顿第二定律",RetrievalMode.HYBRID,()->false);
        verify(access).requireReader(7L,30L);
        verify(keyword).search(30L,"牛顿第二定律",10);
        verify(vectors).search(eq("v1"),eq(30L),eq(20L),any(),eq(10));
        assertThat(result.status()).isEqualTo(RetrievalStatus.MATCHED);
        assertThat(result.passages()).hasSize(1);
    }

    @Test void withdrawn_document_is_filtered_before_prompt() {
        when(events.findById(9L)).thenReturn(Optional.of(event(30L)));
        when(keyword.search(30L,"问题",10)).thenReturn(List.of());
        when(documentsRepo.findActiveIndexesByClassId(30L)).thenReturn(List.of());
        var result = retriever.retrieve(7L,9L,"问题",RetrievalMode.HYBRID,()->false);
        assertThat(result.status()).isEqualTo(RetrievalStatus.INSUFFICIENT);
        assertThat(result.passages()).isEmpty();
        verify(documents,never()).requireActiveReadable(anyLong(),anyLong());
        verify(embeddings,never()).embed(anyList(),anyBoolean(),any());
    }

    @Test void vector_failure_is_unavailable_unless_lexical_fallback_is_explicit() {
        when(events.findById(9L)).thenReturn(Optional.of(event(30L)));
        when(keyword.search(30L,"问题",10)).thenReturn(List.of(hit(1,10,20,30,40,1,.8)));
        when(documentsRepo.findActiveIndexesByClassId(30L)).thenReturn(List.of(new ActiveKnowledgeIndexRow(10L,30L,20L,"v1")));
        when(embeddings.embed(anyList(),anyBoolean(),any())).thenReturn(new EmbeddingBatch(properties.getEmbeddingModel(),512,List.of(new float[512])));
        when(vectors.search(eq("v1"),eq(30L),eq(20L),any(),eq(10))).thenThrow(new RuntimeException("qdrant down"));
        when(documents.requireActiveReadable(7L,1L)).thenReturn(new KnowledgeChunkView(1L,20L,"题目",1,"力学","正文"));
        var unavailable = retriever.retrieve(7L,9L,"问题",RetrievalMode.HYBRID,()->false);
        assertThat(unavailable.status()).isEqualTo(RetrievalStatus.UNAVAILABLE);
        properties.setAllowLexicalFallback(true);
        var fallback = retriever.retrieve(7L,9L,"问题",RetrievalMode.HYBRID,()->false);
        assertThat(fallback.status()).isEqualTo(RetrievalStatus.MATCHED);
        assertThat(fallback.actualMode()).isEqualTo("lexical_only");
    }

    private TeacherEvent event(Long classId) { var e=new TeacherEvent();e.setId(9L);e.setClassId(classId);return e; }
    private KnowledgeSearchHit hit(long id,long doc,long rev,long cls,long ordinal,int rank,double score) {
        return new KnowledgeSearchHit(id,doc,rev,cls,ordinal,"题目",1,"力学","正文",rank,score);
    }
    private static KnowledgeProperties enabledProperties(){var p=new KnowledgeProperties();p.setEnabled(true);return p;}
}
