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
    private final com.zhixuewuli.service.ai.report.ReportInputRepository inputs=mock(com.zhixuewuli.service.ai.report.ReportInputRepository.class);
    private final AliyunAIReportServiceImpl service = new AliyunAIReportServiceImpl(inputs,client, answers,
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
        when(inputs.teacher(null)).thenThrow(new IllegalStateException("database unavailable"));
        var emitter = streams.createConfiguredEmitterWithLimit(60000L, 3000L);
        assertThatCode(() -> service.streamTeacherAnalysis(emitter, 1L)).doesNotThrowAnyException();
        assertThat(streams.getActiveConnectionCount()).isZero();
    }

    @Test void taskWithNoAnswersDoesNotFallBackToArchivedAnswers() {
        when(inputs.teacher(11L)).thenReturn(List.of());
        var emitter = streams.createConfiguredEmitterWithLimit(60000L, 3000L);
        service.streamTeacherAnalysis(emitter, 1L, 11L);
        verify(answers, never()).findDistinctUserIds();
        verifyNoInteractions(client);
        assertThat(streams.getActiveConnectionCount()).isZero();
    }
}
