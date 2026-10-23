package com.zhixuewuli.dto.knowledge;
import com.zhixuewuli.service.knowledge.RetrievalStatus;
import java.util.List;
public record RetrievalResult(RetrievalStatus status, List<KnowledgePassage> passages, String actualMode,
                              List<String> indexVersions, long elapsedMillis) {
    public RetrievalResult { passages=List.copyOf(passages); indexVersions=List.copyOf(indexVersions); }
}
