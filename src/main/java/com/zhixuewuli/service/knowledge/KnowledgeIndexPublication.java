package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.entity.KnowledgeDocument;
import com.zhixuewuli.entity.KnowledgeRevision;
import com.zhixuewuli.repository.KnowledgeDocumentRepository;
import com.zhixuewuli.repository.KnowledgeRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeIndexPublication {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeRevisionRepository revisions;

    @Transactional
    public void publish(Long revisionId, String workerId, String indexVersion) {
        var revision = revisions.findById(revisionId).orElseThrow(() -> new IllegalStateException("revision missing"));
        var document = documents.lockById(revision.getDocumentId()).orElseThrow(() -> new IllegalStateException("document missing"));
        if (document.getWithdrawnAt() != null) throw new IllegalStateException("DOCUMENT_WITHDRAWN");
        if (revision.getStatus() != KnowledgeRevision.Status.INDEXING || !workerId.equals(revision.getLeaseOwner())) {
            throw new IllegalStateException("INDEX_LEASE_LOST");
        }
        var latest = revisions.findFirstByDocumentIdOrderByIdDesc(document.getId()).orElseThrow();
        if (!latest.getId().equals(revisionId)) throw new IllegalStateException("SUPERSEDED_REVISION");
        revision.setStatus(KnowledgeRevision.Status.READY);
        revision.setIndexVersion(indexVersion);
        revision.setLeaseOwner(null);
        revision.setLeaseExpiresAt(null);
        revision.setErrorCode(null);
        document.setActiveRevisionId(revisionId);
        revisions.save(revision);
        documents.save(document);
    }
}
