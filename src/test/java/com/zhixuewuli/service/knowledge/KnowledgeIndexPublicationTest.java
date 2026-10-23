package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.entity.KnowledgeDocument;
import com.zhixuewuli.entity.KnowledgeRevision;
import com.zhixuewuli.repository.KnowledgeDocumentRepository;
import com.zhixuewuli.repository.KnowledgeRevisionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class KnowledgeIndexPublicationTest {
    @Test
    void atomicallyMarksNewestIndexReadyAndActivatesItsVersion() {
        var documents = mock(KnowledgeDocumentRepository.class);
        var revisions = mock(KnowledgeRevisionRepository.class);
        var document = new KnowledgeDocument();
        document.setId(10L);
        document.setActiveRevisionId(1L);
        var revision = new KnowledgeRevision();
        revision.setId(2L);
        revision.setDocumentId(10L);
        revision.setStatus(KnowledgeRevision.Status.INDEXING);
        revision.setLeaseOwner("worker-1");
        when(revisions.findById(2L)).thenReturn(Optional.of(revision));
        when(documents.lockById(10L)).thenReturn(Optional.of(document));
        when(revisions.findFirstByDocumentIdOrderByIdDesc(10L)).thenReturn(Optional.of(revision));

        new KnowledgeIndexPublication(documents, revisions).publish(2L, "worker-1", "idx_0123456789abcdef01234567");

        assertEquals(KnowledgeRevision.Status.READY, revision.getStatus());
        assertEquals("idx_0123456789abcdef01234567", revision.getIndexVersion());
        assertEquals(2L, document.getActiveRevisionId());
        assertNull(revision.getLeaseOwner());
    }

    @Test
    void supersededRevisionCannotReplaceCurrentActiveRevision() {
        var documents = mock(KnowledgeDocumentRepository.class);
        var revisions = mock(KnowledgeRevisionRepository.class);
        var document = new KnowledgeDocument();
        document.setId(10L);
        document.setActiveRevisionId(3L);
        var revision = new KnowledgeRevision();
        revision.setId(2L);
        revision.setDocumentId(10L);
        revision.setStatus(KnowledgeRevision.Status.INDEXING);
        revision.setLeaseOwner("worker-1");
        var newest = new KnowledgeRevision();
        newest.setId(3L);
        when(revisions.findById(2L)).thenReturn(Optional.of(revision));
        when(documents.lockById(10L)).thenReturn(Optional.of(document));
        when(revisions.findFirstByDocumentIdOrderByIdDesc(10L)).thenReturn(Optional.of(newest));

        assertThrows(IllegalStateException.class,
                () -> new KnowledgeIndexPublication(documents, revisions).publish(2L, "worker-1", "idx_0123456789abcdef01234567"));

        assertEquals(3L, document.getActiveRevisionId());
        assertEquals(KnowledgeRevision.Status.INDEXING, revision.getStatus());
        verify(documents, never()).save(any());
    }
}
