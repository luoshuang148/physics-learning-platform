package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.dto.knowledge.KnowledgePassage;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

@Component
public class CitationValidator {
    private static final Pattern CITATION=Pattern.compile("\\[(S[1-9][0-9]*)\\]");
    public CitationValidationResult validate(String answer,List<KnowledgePassage> passages) {
        var allowed=new LinkedHashSet<String>(); passages.forEach(p->allowed.add(p.sourceId()));
        var used=new LinkedHashSet<String>(); var matcher=CITATION.matcher(answer==null?"":answer); var output=new StringBuffer();
        while(matcher.find()) {String id=matcher.group(1);if(allowed.contains(id)){used.add(id);matcher.appendReplacement(output,Matcher.quoteReplacement(matcher.group()));}else matcher.appendReplacement(output,"");}
        matcher.appendTail(output);
        return new CitationValidationResult(output.toString(),List.copyOf(used));
    }
}
