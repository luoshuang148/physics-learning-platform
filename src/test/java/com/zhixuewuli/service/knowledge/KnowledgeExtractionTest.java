package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.dto.knowledge.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@Tag("unit")
class KnowledgeExtractionTest {
    @TempDir Path directory;
    KnowledgeProperties properties=new KnowledgeProperties();
    KnowledgeTextExtractor extractor=new KnowledgeTextExtractor(properties);
    @Test void markdownPreservesFormulaAndHeadingWithoutInventingPageNumbers() throws Exception {
        var file=directory.resolve("notes.md");Files.writeString(file,"# 讲义\n## ENERGY 机械能\n公式 $$E_k=mv^2/2$$ 不可被拆坏。\n");
        var sections=extractor.extract(file,"text/plain");assertFalse(sections.isEmpty());
        assertTrue(sections.getFirst().heading().contains("ENERGY"));assertNull(sections.getFirst().page());
        assertTrue(sections.getFirst().text().contains("$$E_k=mv^2/2$$"));
    }
    private Path pdf(int pages,boolean encrypted,boolean text) throws Exception {
        var file=directory.resolve("fixture-"+pages+"-"+encrypted+"-"+text+".pdf");
        try(var document=new PDDocument()) {
            for(int i=1;i<=pages;i++) {
                var page=new PDPage();document.addPage(page);
                if(text)try(var content=new PDPageContentStream(document,page)) {
                    content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                    content.newLineAtOffset(50,700);content.showText("Energy page "+i);content.endText();
                }
            }
            if(encrypted)document.protect(new StandardProtectionPolicy("owner-password","student-password",new AccessPermission()));
            document.save(file.toFile());
        }
        return file;
    }
    @Test void textPdfPreservesOneBasedPageNumbers() throws Exception {
        var sections=extractor.extract(pdf(2,false,true),"application/pdf");assertEquals(2,sections.size());
        assertEquals(1,sections.get(0).page());assertEquals(2,sections.get(1).page());
        assertTrue(sections.get(1).text().contains("Energy page 2"));
    }
    @Test void scannedOrEmptyPdfRequiresOcrInsteadOfIndexingBlankText() throws Exception {
        var path=pdf(1,false,false);
        var error=assertThrows(KnowledgeExtractionException.class,()->extractor.extract(path,"application/pdf"));
        assertEquals(KnowledgeExtractionException.Reason.OCR_REQUIRED,error.reason());
    }
    @Test void encryptedPdfIsRejectedExplicitly() throws Exception {
        var path=pdf(1,true,true);
        var error=assertThrows(KnowledgeExtractionException.class,()->extractor.extract(path,"application/pdf"));
        assertEquals(KnowledgeExtractionException.Reason.ENCRYPTED_PDF,error.reason());
    }
    @Test void tooManyPagesAreRejected() throws Exception {
        properties.setMaxPdfPages(1);var path=pdf(2,false,true);
        var error=assertThrows(KnowledgeExtractionException.class,()->extractor.extract(path,"application/pdf"));
        assertEquals(KnowledgeExtractionException.Reason.TOO_MANY_PAGES,error.reason());
    }
    @Test void configuredPageLimitCannotExceedOneHundred() throws Exception {
        properties.setMaxPdfPages(101);
        var path=pdf(101,false,false);
        var error=assertThrows(KnowledgeExtractionException.class,()->extractor.extract(path,"application/pdf"));
        assertEquals(KnowledgeExtractionException.Reason.TOO_MANY_PAGES,error.reason());
    }
    @Test void extractedTextHasAnIndependentCharacterLimit() throws Exception {
        properties.setMaxExtractedCharacters(20);var path=directory.resolve("large.txt");Files.writeString(path,"a".repeat(21));
        var error=assertThrows(KnowledgeExtractionException.class,()->extractor.extract(path,"text/plain"));
        assertEquals(KnowledgeExtractionException.Reason.INPUT_TOO_LARGE,error.reason());
    }
    @Test void configuredCharacterLimitCannotExceedTwoHundredThousand() throws Exception {
        properties.setMaxExtractedCharacters(200001);
        var path=directory.resolve("hard-limit.txt");Files.writeString(path,"a".repeat(200001));
        var error=assertThrows(KnowledgeExtractionException.class,()->extractor.extract(path,"text/plain"));
        assertEquals(KnowledgeExtractionException.Reason.INPUT_TOO_LARGE,error.reason());
    }
    @Test void chunkKeepsFormulaAndSourceBoundaries() {
        String text="知识".repeat(420)+"$$F=ma+"+"x".repeat(150)+"$$"+"条件".repeat(900);
        var chunks=new KnowledgeChunker().chunk(List.of(new ExtractedSection(4,"受力",text)));
        assertTrue(chunks.size()>1);
        for(var chunk:chunks) {
            assertTrue(chunk.text().length()<=1200);assertEquals(4,chunk.page());assertEquals("受力",chunk.section());
            assertEquals(0,(chunk.text().split("\\$\\$",-1).length-1)%2);
            assertTrue(chunk.sha256().matches("[0-9a-f]{64}"));
        }
        assertTrue(chunks.stream().anyMatch(c->c.text().contains("$$F=ma+"+"x".repeat(150)+"$$")));
    }
    @Test void oversizedFormulaIsReportedForRepair() {
        var sections=List.of(new ExtractedSection(null,"长公式","$$"+"a".repeat(1250)+"$$"));
        var error=assertThrows(KnowledgeExtractionException.class,()->new KnowledgeChunker().chunk(sections));
        assertEquals(KnowledgeExtractionException.Reason.FORMULA_TOO_LONG,error.reason());
    }
}
