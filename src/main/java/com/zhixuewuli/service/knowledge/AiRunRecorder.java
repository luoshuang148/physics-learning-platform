package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @RequiredArgsConstructor
public class AiRunRecorder {
    private final AiRunRepository runs; private final AiRunStepRepository steps;
    private final AiMessageSourceRepository sources;

    @Transactional
    public AiRun record(ConversationHistory history,RagInteractionData data) {
        if(history.getId()==null || !Boolean.TRUE.equals(history.getIsActive()))throw new IllegalArgumentException("必须绑定已保存的活动历史记录");
        var run=new AiRun();run.setConversationHistoryId(history.getId());run.setUserId(history.getUserId());run.setEventId(history.getEventId());
        run.setMode(data.mode());run.setStatus("CANCELLED".equals(data.outcome())?AiRun.Status.CANCELLED:AiRun.Status.SUCCEEDED);run.setOutcome(data.outcome());run.setModelName(data.model());
        run.setPromptSha256(sha256(data.systemPrompt()+"\n"+data.userPrompt()));run.setInputSha256(sha256(data.originalInput()));
        run.setPromptTokens(data.promptTokens());run.setCompletionTokens(data.completionTokens());run.setTotalTokens(data.totalTokens());
        run.setRetrievalMs(data.retrievalMs());run.setModelMs(data.modelMs());run=runs.saveAndFlush(run);
        int order=0;
        for(var passage:data.passages()) {var source=new AiMessageSource();source.setRunId(run.getId());source.setCitationId(passage.sourceId());source.setChunkId(passage.chunkId());source.setDocumentId(passage.documentId());source.setRevisionId(passage.revisionId());source.setTitleSnapshot(passage.title());source.setPageNo(passage.page());source.setSectionTitle(passage.section());source.setSourceText(passage.text());source.setSourceSha256(sha256(passage.text()));sources.save(source);}
        for(String stepName:new String[]{"retrieval","prompt","model"}){var step=new AiRunStep();step.setRunId(run.getId());step.setStepOrder(++order);step.setStepName(stepName);step.setStatus("SUCCEEDED");steps.save(step);}
        return run;
    }
    private String sha256(String input){try{var digest=MessageDigest.getInstance("SHA-256").digest((input==null?"":input).getBytes(StandardCharsets.UTF_8));return java.util.HexFormat.of().formatHex(digest);}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable",e);}}
}
