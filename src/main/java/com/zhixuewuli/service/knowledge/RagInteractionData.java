package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.dto.knowledge.KnowledgePassage;
import java.util.List;
public record RagInteractionData(String mode,String outcome,String model,String systemPrompt,String userPrompt,
                                 String originalInput,Integer promptTokens,Integer completionTokens,Integer totalTokens,
                                 Long retrievalMs,Long modelMs,List<KnowledgePassage> passages) {
    public RagInteractionData { passages=List.copyOf(passages); }
}
