package com.zhixuewuli.service.knowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.common.exception.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("unit")
class KnowledgeDocumentServiceTest {
    @TempDir Path directory;
    KnowledgeDocumentRepository documents=mock(KnowledgeDocumentRepository.class);
    KnowledgeRevisionRepository revisions=mock(KnowledgeRevisionRepository.class);
    KnowledgeChunkRepository chunks=mock(KnowledgeChunkRepository.class);
    TeachingClassRepository classes=mock(TeachingClassRepository.class);
    KnowledgeAccessPolicy access=mock(KnowledgeAccessPolicy.class);
    KnowledgeProperties properties=new KnowledgeProperties();
    KnowledgeDocumentStore store;
    KnowledgeDocumentService service;
    KnowledgeDocument document;
    KnowledgeRevision revision;
    @BeforeEach void setup(){
        properties.setEnabled(true);properties.setStoragePath(directory.toString());store=new KnowledgeDocumentStore(properties);
        service=new KnowledgeDocumentService(documents,revisions,chunks,classes,access,store,properties);
        document=new KnowledgeDocument();document.setId(1L);document.setClassId(10L);document.setTitle("讲义");
        revision=new KnowledgeRevision();revision.setId(2L);revision.setDocumentId(1L);revision.setStatus(KnowledgeRevision.Status.READY);
        var chunk=new KnowledgeChunk();chunk.setId(3L);chunk.setRevisionId(2L);chunk.setText("授权原文");
        when(documents.findById(1L)).thenReturn(Optional.of(document));
        when(documents.lockById(1L)).thenReturn(Optional.of(document));
        when(revisions.findById(2L)).thenReturn(Optional.of(revision));
        when(chunks.findById(3L)).thenReturn(Optional.of(chunk));
        when(documents.saveAndFlush(any())).thenAnswer(i->{KnowledgeDocument value=i.getArgument(0);value.setId(1L);return value;});
        when(revisions.saveAndFlush(any())).thenAnswer(i->{KnowledgeRevision value=i.getArgument(0);value.setId(2L);return value;});
    }
    private MockMultipartFile file(){return new MockMultipartFile("file","notes.md","text/markdown","讲义原文".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Test void uploadReturnsStatusWithoutLeakingStorageKey() throws Exception {
        var result=service.upload(5L,10L,"牛顿定律",file());assertNotNull(result);
        assertEquals("UPLOADED",result.status());assertEquals(1L,result.documentId());
        String json=new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("storageKey"));assertFalse(json.contains(directory.toString()));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void deniedUploadDoesNotWriteAnyFile() throws Exception {
        doThrow(new InsufficientPermissionException("denied")).when(access).requireTeacher(5L,10L);
        assertThrows(InsufficientPermissionException.class,()->service.upload(5L,10L,"讲义",file()));
        try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
    @Test void withdrawnDocumentCannotRevealChunkText() {
        document.setWithdrawnAt(LocalDateTime.now());
        assertThrows(BusinessException.class,()->service.requireReadable(5L,3L));
    }
    @Test void authorizedReadyChunkIncludesOriginalSourceText() {
        var view=service.requireReadable(5L,3L);assertNotNull(view);
        assertEquals("授权原文",view.text());assertEquals(2L,view.revisionId());
    }
    @Test void activeReadableRequiresCurrentActiveRevisionAfterAuthorization() {
        document.setActiveRevisionId(2L);
        assertEquals("授权原文",service.requireActiveReadable(5L,3L).text());
        document.setActiveRevisionId(99L);
        assertThrows(KnowledgeNotFoundException.class,()->service.requireActiveReadable(5L,3L));
    }
    @Test void indexingVersionIsNotReadable() {
        revision.setStatus(KnowledgeRevision.Status.INDEXING);
        assertThrows(BusinessException.class,()->service.requireReadable(5L,3L));
    }
    @Test void disabledFeatureAndClassQuotaRejectBeforeWriting() {
        properties.setEnabled(false);
        assertThrows(BusinessException.class,()->service.upload(5L,10L,"讲义",file()));
        properties.setEnabled(true);when(documents.countByClassIdAndWithdrawnAtIsNull(10L)).thenReturn(50L);
        assertThrows(BusinessException.class,()->service.upload(5L,10L,"讲义",file()));
    }
    @Test void withdrawImmediatelyRevokesReadAccess() {
        assertDoesNotThrow(()->service.withdraw(5L,1L));
        assertNotNull(document.getWithdrawnAt());
        assertThrows(BusinessException.class,()->service.requireReadable(5L,3L));
    }
    @Test void repeatedReindexReusesPendingRevision() {
        revision.setStatus(KnowledgeRevision.Status.INDEXING);
        when(revisions.findFirstByDocumentIdOrderByIdDesc(1L)).thenReturn(Optional.of(revision));
        var result=service.requestReindex(5L,1L);assertNotNull(result);
        assertEquals(2L,result.revisionId());assertEquals("INDEXING",result.status());
    }
    @Test void databaseFailureCleansNewlyWrittenFile() throws Exception {
        doThrow(new IllegalStateException("fixture-db-failure")).when(documents).saveAndFlush(any());
        assertThrows(IllegalStateException.class,()->service.upload(5L,10L,"讲义",file()));
        try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
}
