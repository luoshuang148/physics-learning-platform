package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class AiRunHistoryServiceTest {

    /** 班级资料库下线后：历史文档来源统一标为不可用，不回源原文。 */
    @Test void historical_document_sources_are_marked_unavailable() {
        var runs = mock(AiRunRepository.class);
        var sources = mock(AiMessageSourceRepository.class);
        var run = new AiRun();
        run.setId(90L);
        when(runs.findByConversationHistoryId(8L)).thenReturn(Optional.of(run));
        var source = new AiMessageSource();
        source.setCitationId("S1");
        source.setChunkId(4L);
        source.setDocumentId(5L);
        source.setRevisionId(6L);
        when(sources.findByRunIdOrderById(90L)).thenReturn(List.of(source));

        var history = new ConversationHistory();
        history.setId(8L);
        history.setAnswer("答案依据[S1]");
        new AiRunHistoryService(runs, sources).attachSources(history, 7L);

        assertThat(history.getAnswer()).contains("资料已撤回");
        assertThat(history.getSources()).hasSize(1).first().satisfies(view -> {
            assertThat(view.available()).isFalse();
            assertThat(view.text()).isNull();
        });
        assertThat(history.getAiRunId()).isEqualTo(90L);
    }

    /** 无来源的历史记录保持原答案不动。 */
    @Test void history_without_sources_keeps_answer() {
        var runs = mock(AiRunRepository.class);
        var sources = mock(AiMessageSourceRepository.class);
        var run = new AiRun();
        run.setId(90L);
        when(runs.findByConversationHistoryId(8L)).thenReturn(Optional.of(run));
        when(sources.findByRunIdOrderById(90L)).thenReturn(List.of());

        var history = new ConversationHistory();
        history.setId(8L);
        history.setAnswer("普通回答");
        new AiRunHistoryService(runs, sources).attachSources(history, 7L);

        assertThat(history.getAnswer()).isEqualTo("普通回答");
        assertThat(history.getSources()).isEmpty();
    }
}
