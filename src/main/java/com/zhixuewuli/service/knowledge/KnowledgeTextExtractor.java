package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.dto.knowledge.ExtractedSection;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.zhixuewuli.service.knowledge.KnowledgeExtractionException.Reason.*;
@Component @RequiredArgsConstructor
public class KnowledgeTextExtractor {
    private static final Pattern HEADING=Pattern.compile("^#{1,6}\\s+(.+)$");
    private final KnowledgeProperties properties;
    public List<ExtractedSection> extract(Path path,String mediaType){
        int limit=Math.min(properties.getMaxExtractedCharacters(),200000);
        if(limit<1)throw new KnowledgeExtractionException(INPUT_TOO_LARGE);
        try {
            if("application/pdf".equals(mediaType))return pdf(path,limit);
            if(!"text/plain".equals(mediaType) && !"text/markdown".equals(mediaType))throw new KnowledgeExtractionException(INVALID_DOCUMENT);
            var writer=new LimitedWriter(limit);
            try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){reader.transferTo(writer);}
            String text=writer.text().replace("\r\n","\n").replace('\r','\n');
            if(text.startsWith("\uFEFF"))text=text.substring(1);
            var result=new ArrayList<ExtractedSection>();String heading=null;var body=new StringBuilder();
            for(String line:text.split("\n",-1)){
                var match=HEADING.matcher(line);
                if(match.matches()){
                    add(result,null,heading,body.toString());body.setLength(0);heading=match.group(1).strip();
                } else body.append(line).append('\n');
            }
            add(result,null,heading,body.toString());
            if(result.isEmpty())throw new KnowledgeExtractionException(INVALID_DOCUMENT);
            return List.copyOf(result);
        } catch(LimitReached error){throw new KnowledgeExtractionException(INPUT_TOO_LARGE);}
        catch(InvalidPasswordException error){throw new KnowledgeExtractionException(ENCRYPTED_PDF);}
        catch(IOException error){throw new KnowledgeExtractionException(INVALID_DOCUMENT);}
    }
    private List<ExtractedSection> pdf(Path path,int limit)throws IOException {
        try(var document=Loader.loadPDF(path.toFile())){
            if(document.isEncrypted())throw new KnowledgeExtractionException(ENCRYPTED_PDF);
            int pages=document.getNumberOfPages();
            if(pages>Math.min(properties.getMaxPdfPages(),100))throw new KnowledgeExtractionException(TOO_MANY_PAGES);
            var result=new ArrayList<ExtractedSection>();int used=0;
            for(int page=1;page<=pages;page++){
                var stripper=new PDFTextStripper();stripper.setSortByPosition(true);stripper.setStartPage(page);stripper.setEndPage(page);
                var writer=new LimitedWriter(limit-used);stripper.writeText(document,writer);used+=writer.length();
                add(result,page,"第"+page+"页",writer.text());
            }
            if(result.isEmpty())throw new KnowledgeExtractionException(OCR_REQUIRED);
            return List.copyOf(result);
        }
    }
    private void add(List<ExtractedSection> sections,Integer page,String heading,String text){
        if(!text.isBlank())sections.add(new ExtractedSection(page,heading==null?null:heading.substring(0,Math.min(heading.length(),200)),text.strip()));
    }
    private static final class LimitReached extends IOException {}
    private static final class LimitedWriter extends Writer {
        private final int limit;private final StringBuilder content=new StringBuilder();
        LimitedWriter(int limit){this.limit=limit;}
        @Override public void write(char[] chars,int offset,int length)throws IOException {
            if((long)content.length()+length>limit)throw new LimitReached();content.append(chars,offset,length);
        }
        String text(){return content.toString();}int length(){return content.length();}
        @Override public void flush(){} @Override public void close(){}
    }
}
