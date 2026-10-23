package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.dto.knowledge.*;
import org.apache.commons.codec.digest.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
@Component
public class KnowledgeChunker {
    private static final Pattern MATH=Pattern.compile("(?s)\\$\\$.*?\\$\\$|\\\\\\[.*?\\\\\\]|\\\\\\(.*?\\\\\\)|(?<!\\$)\\$(?!\\$)[^$\\n]*?\\$(?!\\$)");
    private record Span(int start,int end){}
    public List<ChunkDraft> chunk(List<ExtractedSection> sections){
        var result=new ArrayList<ChunkDraft>();
        for(var section:sections){
            String text=section.text();if(text==null || text.isBlank())continue;
            var math=new ArrayList<Span>();var matcher=MATH.matcher(text);
            while(matcher.find()){
                if(matcher.end()-matcher.start()>1200)throw new KnowledgeExtractionException(KnowledgeExtractionException.Reason.FORMULA_TOO_LONG);
                math.add(new Span(matcher.start(),matcher.end()));
            }
            int start=0;
            while(start<text.length()){
                int end=Math.min(start+900,text.length());
                if(end<text.length()){
                    int paragraph=text.lastIndexOf('\n',end-1),sentence=text.lastIndexOf('。',end-1);
                    int preferred=Math.max(paragraph,sentence);
                    if(preferred>=start+600)end=preferred+1;
                    for(var span:math)if(span.start()<end && end<span.end()){
                        end=span.end()-start<=1200?span.end():span.start();break;
                    }
                    if(end<text.length() && Character.isLowSurrogate(text.charAt(end)) && end>start)end--;
                }
                if(end<=start || end-start>1200)throw new KnowledgeExtractionException(KnowledgeExtractionException.Reason.FORMULA_TOO_LONG);
                String content=text.substring(start,end).strip();
                if(!content.isEmpty())result.add(new ChunkDraft(result.size(),section.page(),section.heading(),content,
                        DigestUtils.sha256Hex(content.getBytes(StandardCharsets.UTF_8))));
                if(end==text.length())break;
                int next=end-start<=100?end:end-100;
                for(var span:math)if(span.start()<next && next<span.end()){next=span.end();break;}
                if(next<text.length() && Character.isLowSurrogate(text.charAt(next)))next++;
                start=Math.max(start+1,next);
            }
        }
        return List.copyOf(result);
    }
}
