package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.dto.knowledge.ChunkDraft;
import com.zhixuewuli.entity.KnowledgeChunk;
import com.zhixuewuli.entity.KnowledgeDocument;
import com.zhixuewuli.entity.KnowledgeRevision;
import com.zhixuewuli.repository.KnowledgeChunkRepository;
import com.zhixuewuli.repository.KnowledgeDocumentRepository;
import com.zhixuewuli.repository.KnowledgeRevisionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@Slf4j
public class KnowledgeIndexService {
    private static final int LEASE_MINUTES = 15;
    private static final int EMBEDDING_BATCH_SIZE = 16;
    private final KnowledgeProperties properties;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeRevisionRepository revisions;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeDocumentStore documentStore;
    private final KnowledgeTextExtractor extractor;
    private final KnowledgeChunker chunker;
    private final EmbeddingGateway embeddings;
    private final KnowledgeVectorStore vectors;
    private final KnowledgeIndexPublication publication;
    private final Supplier<String> workerId;

    @Autowired
    public KnowledgeIndexService(KnowledgeProperties properties, KnowledgeDocumentRepository documents,
                                 KnowledgeRevisionRepository revisions, KnowledgeChunkRepository chunks,
                                 KnowledgeDocumentStore documentStore, KnowledgeTextExtractor extractor,
                                 KnowledgeChunker chunker, EmbeddingGateway embeddings,
                                 KnowledgeVectorStore vectors, KnowledgeIndexPublication publication) {
        this(properties, documents, revisions, chunks, documentStore, extractor, chunker, embeddings, vectors,
                publication, () -> UUID.randomUUID().toString());
    }

    KnowledgeIndexService(KnowledgeProperties properties, KnowledgeDocumentRepository documents,
                          KnowledgeRevisionRepository revisions, KnowledgeChunkRepository chunks,
                          KnowledgeDocumentStore documentStore, KnowledgeTextExtractor extractor,
                          KnowledgeChunker chunker, EmbeddingGateway embeddings, KnowledgeVectorStore vectors,
                          KnowledgeIndexPublication publication, Supplier<String> workerId) {
        this.properties = properties;
        this.documents = documents;
        this.revisions = revisions;
        this.chunks = chunks;
        this.documentStore = documentStore;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.publication = publication;
        this.workerId = workerId;
    }

    public void indexRevision(Long revisionId) {
        if (!properties.isEnabled() || revisionId == null) return;
        var owner = workerId.get();
        var now = LocalDateTime.now();
        int claimed = revisions.claimForIndexing(revisionId, owner, now, now.plusMinutes(LEASE_MINUTES),
                KnowledgeRevision.Status.UPLOADED, KnowledgeRevision.Status.INDEXING);
        if (claimed != 1) return;

        String indexVersion = null;
        try {
            var revision = revisions.findById(revisionId).orElseThrow(() -> new IllegalStateException("REVISION_NOT_FOUND"));
            var document = documents.findById(revision.getDocumentId()).orElseThrow(() -> new IllegalStateException("DOCUMENT_NOT_FOUND"));
            if (document.getWithdrawnAt() != null) throw new IllegalStateException("DOCUMENT_WITHDRAWN");
            var version = KnowledgeIndexVersion.forRevision(revisionId, properties);
            indexVersion = version.id();
            var stored = chunks.findByRevisionIdOrderByOrdinalNo(revisionId);
            if (stored.isEmpty()) {
                var sections = extractor.extract(documentStore.path(document.getStorageKey()), document.getMediaType());
                var drafts = chunker.chunk(sections);
                if (drafts.isEmpty()) throw new KnowledgeExtractionException(KnowledgeExtractionException.Reason.OCR_REQUIRED);
                stored = chunks.saveAll(toEntities(revisionId, drafts));
            }
            revision.setExtractorVersion(properties.getExtractorVersion());
            revision.setChunkerVersion(properties.getChunkerVersion());
            revision.setEmbeddingModel(properties.getEmbeddingModel());
            revision.setDimensions(properties.getEmbeddingDimensions());
            revisions.save(revision);

            vectors.ensureCollection(indexVersion, properties.getEmbeddingDimensions());
            for (int offset = 0; offset < stored.size(); offset += EMBEDDING_BATCH_SIZE) {
                var batch = stored.subList(offset, Math.min(stored.size(), offset + EMBEDDING_BATCH_SIZE));
                var response = embeddings.embed(batch.stream().map(KnowledgeChunk::getText).toList(), false, () -> false);
                validateEmbeddingBatch(response, batch.size());
                var points = new ArrayList<KnowledgeVectorPoint>(batch.size());
                for (int i = 0; i < batch.size(); i++) {
                    var chunk = batch.get(i);
                    points.add(new KnowledgeVectorPoint(pointId(version, chunk.getOrdinalNo()),
                            response.vectors().get(i), chunk.getId(), revisionId, document.getClassId(), chunk.getOrdinalNo()));
                }
                vectors.upsert(indexVersion, points);
                renewLease(revisionId, owner);
            }
            if (vectors.count(indexVersion) != stored.size()) throw new KnowledgeVectorException("INDEX_POINT_COUNT_MISMATCH");
            publication.publish(revisionId, owner, indexVersion);
        } catch (RuntimeException error) {
            cleanupFailedIndex(revisionId, owner, indexVersion, error);
        }
    }

    private List<KnowledgeChunk> toEntities(Long revisionId, List<ChunkDraft> drafts) {
        var result = new ArrayList<KnowledgeChunk>(drafts.size());
        for (var draft : drafts) {
            var chunk = new KnowledgeChunk();
            chunk.setRevisionId(revisionId);
            chunk.setOrdinalNo(draft.ordinal());
            chunk.setPageNo(draft.page());
            chunk.setSectionTitle(draft.section());
            chunk.setText(draft.text());
            chunk.setContentSha256(draft.sha256());
            result.add(chunk);
        }
        return result;
    }

    private void validateEmbeddingBatch(EmbeddingBatch batch, int expectedCount) {
        if (batch == null || batch.vectors().size() != expectedCount) {
            throw new KnowledgeEmbeddingException("INVALID_EMBEDDING_BATCH");
        }
        if (!properties.getEmbeddingModel().equals(batch.model())) {
            throw new KnowledgeEmbeddingException("MODEL_MISMATCH");
        }
        if (batch.dimensions() != properties.getEmbeddingDimensions()) {
            throw new KnowledgeEmbeddingException("DIMENSION_MISMATCH");
        }
        for (float[] vector : batch.vectors()) {
            if (vector == null || vector.length != properties.getEmbeddingDimensions()) {
                throw new KnowledgeEmbeddingException("DIMENSION_MISMATCH");
            }
            for (float value : vector) if (!Float.isFinite(value)) throw new KnowledgeEmbeddingException("INVALID_VECTOR");
        }
    }

    private UUID pointId(KnowledgeIndexVersion version, int ordinal) {
        return UUID.nameUUIDFromBytes((version.id() + ":" + ordinal).getBytes(StandardCharsets.UTF_8));
    }

    private void renewLease(Long revisionId, String owner) {
        int renewed = revisions.renewIndexingLease(revisionId, owner, LocalDateTime.now().plusMinutes(LEASE_MINUTES),
                KnowledgeRevision.Status.INDEXING);
        if (renewed != 1) throw new IllegalStateException("INDEX_LEASE_LOST");
    }

    private void cleanupFailedIndex(Long revisionId, String owner, String indexVersion, RuntimeException error) {
        int failed = revisions.failClaimedRevision(revisionId, owner, KnowledgeRevision.Status.INDEXING,
                KnowledgeRevision.Status.FAILED, errorCode(error));
        if (failed != 1) return;
        if (indexVersion != null) {
            try {
                vectors.deleteRevision(indexVersion);
            } catch (RuntimeException cleanupError) {
                log.warn("Knowledge vector cleanup deferred for revision {}", revisionId);
            }
        }
        try {
            chunks.deleteByRevisionId(revisionId);
        } catch (RuntimeException cleanupError) {
            log.warn("Knowledge chunk cleanup deferred for revision {}", revisionId);
        }
    }

    private String errorCode(RuntimeException error) {
        if (error instanceof KnowledgeExtractionException extraction) return extraction.reason().name();
        if (error instanceof KnowledgeEmbeddingException embedding && "DIMENSION_MISMATCH".equals(embedding.getMessage())) return "DIMENSION_MISMATCH";
        if (error instanceof KnowledgeEmbeddingException embedding && "MODEL_MISMATCH".equals(embedding.getMessage())) return "MODEL_MISMATCH";
        if (error instanceof KnowledgeEmbeddingException) return "EMBEDDING_FAILED";
        if (error instanceof KnowledgeVectorException && error.getMessage() != null && error.getMessage().contains("POINT_COUNT")) return "INDEX_POINT_COUNT_MISMATCH";
        if (error instanceof IllegalStateException && error.getMessage() != null) return error.getMessage().replaceAll("[^A-Z0-9_]", "_").substring(0, Math.min(80, error.getMessage().length()));
        return "INDEX_FAILED";
    }
}
