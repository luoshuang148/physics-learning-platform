package com.zhixuewuli.service.ai.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.*;
import com.zhixuewuli.common.util.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.ai.impl.AliyunAIReportServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class TeacherReportResourceRegressionTest {
    private final AIStreamingProperties config = new AIStreamingProperties();
    private final StreamUtils streams = new StreamUtils(config);
    private final AnswerRecordRepository answers = mock(AnswerRecordRepository.class);
    private final AliyunAIClient client = mock(AliyunAIClient.class);
    private final AliyunAIReportServiceImpl service = new AliyunAIReportServiceImpl(client, answers,
            mock(QuestionRepository.class), mock(StudentReportRepository.class), mock(UserRepository.class),
            config, new AIBusinessProperties(), mock(OptionMappingUtil.class), mock(UserPermissionUtil.class),
            streams, new ObjectMapper(), mock(TransactionTemplate.class), Runnable::run);

    @AfterEach void close() { streams.destroy(); }

    @Test void emptyDatasetReleasesEveryEmitter() {
        for (int i = 0; i < 40; i++) {
            var emitter = streams.createConfiguredEmitterWithLimit(60000L, 3000L);
            assertThatCode(() -> service.streamTeacherAnalysis(emitter, 1L)).doesNotThrowAnyException();
            assertThat(streams.getActiveConnectionCount()).isZero();
        }
        verifyNoInteractions(client);
    }

    @Test void preQueryFailureReleasesEmitter() {
        when(answers.findDistinctUserIds()).thenThrow(new IllegalStateException("database unavailable"));
        var emitter = streams.createConfiguredEmitterWithLimit(60000L, 3000L);
        assertThatCode(() -> service.streamTeacherAnalysis(emitter, 1L)).doesNotThrowAnyException();
        assertThat(streams.getActiveConnectionCount()).isZero();
    }

    @Test void taskWithNoAnswersDoesNotFallBackToArchivedAnswers() {
        when(answers.findDistinctUserIds()).thenReturn(List.of(7L));
        when(answers.findDistinctSessionIdsByUserId(7L)).thenReturn(List.of("20260101000000_aabbccddeeff0011"));
        AnswerRecord archived = new AnswerRecord(); archived.setUserId(7L); archived.setQuestionId(9L); archived.setEventId(10L);
        when(answers.findByUserIdAndSessionIdOrderByAnsweredAtDesc(eq(7L), anyString())).thenReturn(List.of(archived));
        var emitter = streams.createConfiguredEmitterWithLimit(60000L, 3000L);
        service.streamTeacherAnalysis(emitter, 1L, 11L);
        verify(answers, never()).findDistinctUserIds();
        verifyNoInteractions(client);
        assertThat(streams.getActiveConnectionCount()).isZero();
    }
}
