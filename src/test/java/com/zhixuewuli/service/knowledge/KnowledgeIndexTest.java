package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.entity.KnowledgeDocument;
import com.zhixuewuli.entity.KnowledgeRevision;
import com.zhixuewuli.repository.KnowledgeChunkRepository;
import com.zhixuewuli.repository.KnowledgeDocumentRepository;
import com.zhixuewuli.repository.KnowledgeRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class KnowledgeIndexTest {
    @TempDir Path directory;

    KnowledgeProperties properties;
    KnowledgeDocumentRepository documents;
    KnowledgeRevisionRepository revisions;
    KnowledgeChunkRepository chunks;
    EmbeddingGateway embeddings;
    KnowledgeVectorStore vectors;
    KnowledgeIndexPublication publication;
    KnowledgeIndexService service;
    KnowledgeRevision revision;
    KnowledgeDocument document;

    @BeforeEach
    void setUp() throws Exception {
        properties = new KnowledgeProperties();
        properties.setEnabled(true);
        properties.setStoragePath(directory.toString());
        properties.setEmbeddingDimensions(512);
        properties.setEmbeddingModel("test-bge-512");
        properties.setQdrantUrl("http://localhost:6333");
        documents = mock(KnowledgeDocumentRepository.class);
        revisions = mock(KnowledgeRevisionRepository.class);
        chunks = mock(KnowledgeChunkRepository.class);
        embeddings = mock(EmbeddingGateway.class);
        vectors = mock(KnowledgeVectorStore.class);
        publication = mock(KnowledgeIndexPublication.class);
        service = new KnowledgeIndexService(properties, documents, revisions, chunks,
                new KnowledgeDocumentStore(properties), new KnowledgeTextExtractor(properties),
                new KnowledgeChunker(), embeddings, vectors, publication, () -> "worker-test");

        String key = UUID.randomUUID() + ".bin";
        Files.writeString(new KnowledgeDocumentStore(properties).path(key), "# 受力分析\n合外力为零时物体保持静止或匀速直线运动。\n");
        document = new KnowledgeDocument();
        document.setId(10L);
        document.setClassId(20L);
        document.setStorageKey(key);
        document.setMediaType("text/markdown");
        document.setActiveRevisionId(1L);
        revision = new KnowledgeRevision();
        revision.setId(2L);
        revision.setDocumentId(10L);
        revision.setContentSha256("a".repeat(64));
        revision.setStatus(KnowledgeRevision.Status.UPLOADED);
        when(documents.findById(10L)).thenReturn(Optional.of(document));
        when(documents.lockById(10L)).thenReturn(Optional.of(document));
        when(revisions.findById(2L)).thenReturn(Optional.of(revision));
        when(revisions.claimForIndexing(eq(2L), anyString(), any(java.time.LocalDateTime.class),
                any(java.time.LocalDateTime.class), eq(KnowledgeRevision.Status.UPLOADED),
                eq(KnowledgeRevision.Status.INDEXING))).thenAnswer(invocation -> {
            if (revision.getStatus() == KnowledgeRevision.Status.READY) return 0;
            revision.setStatus(KnowledgeRevision.Status.INDEXING);
            revision.setLeaseOwner(invocation.getArgument(1));
            revision.setLeaseExpiresAt(invocation.getArgument(3));
            return 1;
        });
        when(revisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.renewIndexingLease(eq(2L), eq("worker-test"), any(java.time.LocalDateTime.class),
                eq(KnowledgeRevision.Status.INDEXING))).thenReturn(1);
        when(revisions.failClaimedRevision(eq(2L), eq("worker-test"), eq(KnowledgeRevision.Status.INDEXING),
                eq(KnowledgeRevision.Status.FAILED), anyString())).thenAnswer(invocation -> {
            revision.setStatus(KnowledgeRevision.Status.FAILED);
            revision.setLeaseOwner(null);
            revision.setLeaseExpiresAt(null);
            revision.setErrorCode(invocation.getArgument(4));
            return 1;
        });
        when(chunks.findByRevisionIdOrderByOrdinalNo(2L)).thenReturn(java.util.List.of());
        when(chunks.saveAll(anyIterable())).thenAnswer(invocation -> {
            var saved = new java.util.ArrayList<com.zhixuewuli.entity.KnowledgeChunk>();
            for (Object item : invocation.<Iterable<?>>getArgument(0)) {
                var chunk = (com.zhixuewuli.entity.KnowledgeChunk) item;
                chunk.setId((long) saved.size() + 101L);
                saved.add(chunk);
            }
            return saved;
        });
    }

    @Test
    void duplicateReadyImportDoesNotReembedOrDuplicateChunks() {
        revision.setStatus(KnowledgeRevision.Status.READY);
        assertDoesNotThrow(() -> service.indexRevision(2L));
        verifyNoInteractions(embeddings, vectors, publication);
        verify(chunks, never()).saveAll(anyIterable());
    }

    @Test
    void dimensionMismatchFailsRevisionWithoutReplacingActiveRevision() {
        when(embeddings.embed(anyList(), eq(false), any())).thenReturn(
                new EmbeddingBatch("test-bge-512", 384, java.util.List.of(new float[384])));

        service.indexRevision(2L);

        assertEquals(KnowledgeRevision.Status.FAILED, revision.getStatus());
        assertEquals(1L, document.getActiveRevisionId());
        assertEquals("DIMENSION_MISMATCH", revision.getErrorCode());
        verify(vectors, never()).upsert(anyString(), anyList());
        verify(publication, never()).publish(eq(2L), eq("worker-test"), anyString());
    }

    @Test
    void vectorFailureKeepsPreviousRevisionActiveAndRemovesPartialPoints() {
        when(embeddings.embed(anyList(), eq(false), any())).thenAnswer(invocation -> {
            var texts = invocation.<java.util.List<String>>getArgument(0);
            return new EmbeddingBatch("test-bge-512", 512,
                    texts.stream().map(ignored -> new float[512]).toList());
        });
        doThrow(new KnowledgeVectorException("fixture unavailable"))
                .when(vectors).upsert(anyString(), anyList());

        service.indexRevision(2L);

        assertEquals(KnowledgeRevision.Status.FAILED, revision.getStatus());
        assertEquals(1L, document.getActiveRevisionId());
        verify(vectors).deleteRevision(anyString());
        verify(publication, never()).publish(eq(2L), eq("worker-test"), anyString());
    }

    @Test
    void successfulIndexPublishesOnlyAfterPointCountMatches() {
        when(embeddings.embed(anyList(), eq(false), any())).thenAnswer(invocation -> {
            var texts = invocation.<java.util.List<String>>getArgument(0);
            return new EmbeddingBatch("test-bge-512", 512,
                    texts.stream().map(ignored -> new float[512]).toList());
        });
        when(vectors.count(anyString())).thenReturn(1L);

        service.indexRevision(2L);

        verify(vectors).ensureCollection(anyString(), eq(512));
        verify(vectors).upsert(anyString(), anyList());
        verify(vectors).count(anyString());
        verify(publication).publish(eq(2L), eq("worker-test"), anyString());
    }

    @Test
    void scannedPdfIsMarkedOcrRequiredAndNeverCreatesVectors() throws Exception {
        try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            pdf.save(new KnowledgeDocumentStore(properties).path(document.getStorageKey()).toFile());
        }
        document.setMediaType("application/pdf");

        service.indexRevision(2L);

        assertEquals(KnowledgeRevision.Status.FAILED, revision.getStatus());
        assertEquals("OCR_REQUIRED", revision.getErrorCode());
        assertEquals(1L, document.getActiveRevisionId());
        verify(embeddings, never()).embed(anyList(), anyBoolean(), any());
        verify(vectors, never()).upsert(anyString(), anyList());
    }
}
