package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.dto.knowledge.KnowledgeChunkView;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit") class AiRunHistoryServiceTest {
    @Test void withdrawn_sources_are_redacted_from_history_and_future_context() {
        var runs=mock(AiRunRepository.class);var sources=mock(AiMessageSourceRepository.class);var documents=mock(KnowledgeDocumentService.class);
        var run=new AiRun();run.setId(90L);when(runs.findByConversationHistoryId(8L)).thenReturn(Optional.of(run));
        var source=new AiMessageSource();source.setCitationId("S1");source.setChunkId(4L);source.setDocumentId(5L);source.setRevisionId(6L);
        when(sources.findByRunIdOrderById(90L)).thenReturn(List.of(source));
        when(documents.requireActiveReadable(7L,4L)).thenThrow(new BusinessException("withdrawn"));
        var history=new ConversationHistory();history.setId(8L);history.setAnswer("答案依据[S1]");
        new AiRunHistoryService(runs,sources,documents).attachSources(history,7L);
        assertThat(history.getAnswer()).contains("资料已撤回");
        assertThat(history.getSources()).hasSize(1).first().satisfies(view->{assertThat(view.available()).isFalse();assertThat(view.text()).isNull();});
        assertThat(history.getAiRunId()).isEqualTo(90L);
    }

    @Test void active_source_is_reloaded_from_current_authorized_document() {
        var runs=mock(AiRunRepository.class);var sources=mock(AiMessageSourceRepository.class);var documents=mock(KnowledgeDocumentService.class);
        var run=new AiRun();run.setId(90L);when(runs.findByConversationHistoryId(8L)).thenReturn(Optional.of(run));
        var source=new AiMessageSource();source.setCitationId("S1");source.setChunkId(4L);source.setDocumentId(5L);source.setRevisionId(6L);
        when(sources.findByRunIdOrderById(90L)).thenReturn(List.of(source));
        when(documents.requireActiveReadable(7L,4L)).thenReturn(new KnowledgeChunkView(4L,6L,"现行标题",2,"运动学","现行原文"));
        var history=new ConversationHistory();history.setId(8L);history.setAnswer("答案[S1]");
        new AiRunHistoryService(runs,sources,documents).attachSources(history,7L);
        assertThat(history.getSources().getFirst().available()).isTrue();
        assertThat(history.getSources().getFirst().text()).isEqualTo("现行原文");
        assertThat(history.getAnswer()).isEqualTo("答案[S1]");
    }
}
