package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@Tag("unit")
class KnowledgeDocumentStoreTest {
    @TempDir Path directory;
    KnowledgeProperties properties;
    KnowledgeDocumentStore store;
    @BeforeEach void setup(){properties=new KnowledgeProperties();properties.setStoragePath(directory.toString());store=new KnowledgeDocumentStore(properties);}
    @Test void legitimateTextGetsRandomStorageNameAndContentDigest() throws Exception {
        var file=new MockMultipartFile("file","../../notes.md","text/markdown","牛顿第二定律".getBytes(StandardCharsets.UTF_8));
        var saved=assertDoesNotThrow(()->store.store(file));
        assertTrue(saved.key().matches("[0-9a-f-]{36}\\.bin"));
        assertEquals("牛顿第二定律",Files.readString(store.path(saved.key())));
        assertTrue(saved.sha256().matches("[0-9a-f]{64}"));
    }
    @Test void forgedPdfAndBinaryTextAreRejected() {
        assertThrows(BusinessException.class,()->store.store(new MockMultipartFile("file","x.pdf","application/pdf","not-a-pdf".getBytes())));
        assertThrows(BusinessException.class,()->store.store(new MockMultipartFile("file","x.txt","text/plain",new byte[]{0,1,2})));
    }
    @Test void advertisedAndActualSizeAreBothBounded() {
        properties.setMaxFileBytes(4);
        var lyingFile=new MockMultipartFile("file","notes.txt","text/plain","abcdef".getBytes()) {
            @Override public long getSize(){return 1;}
        };
        assertThrows(BusinessException.class,()->store.store(lyingFile));
    }
    @Test void storageKeysCannotTraverseOutOfRoot() {
        assertThrows(IllegalArgumentException.class,()->store.path("../../outside"));
    }
}
