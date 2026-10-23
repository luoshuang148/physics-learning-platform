package com.zhixuewuli.service.knowledge;
import java.util.List;
public record RagPrompt(String systemMessage,String userMessage,List<String> sourceIds) {
    public RagPrompt { sourceIds=List.copyOf(sourceIds); }
}
