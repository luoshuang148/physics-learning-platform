package com.zhixuewuli.controller;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.QuestionIndexStatus;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.QuestionIndexJob;
import com.zhixuewuli.repository.QuestionIndexJobRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.platformsearch.QuestionSourceIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("QuestionIndexController 契约")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionIndexControllerTest {

    @Mock QuestionRepository questions;
    @Mock QuestionIndexJobRepository jobs;
    @Mock QuestionSourceIndexService indexService;
    @InjectMocks QuestionIndexController controller;

    private com.zhixuewuli.common.security.AuthenticatedUser teacher() {
        return new com.zhixuewuli.common.security.AuthenticatedUser(
                1L, "teacher", com.zhixuewuli.entity.User.UserType.TEACHER);
    }

    private Question question() {
        Question q = new Question();
        q.setId(10L);
        q.setAiPracticeEnabled(true);
        q.setAiContentRevision(3L);
        return q;
    }

    @Test
    @DisplayName("返回当前 revision 的索引状态")
    void statusMatchesCurrentRevision() {
        when(questions.findById(10L)).thenReturn(Optional.of(question()));
        var job = new QuestionIndexJob();
        job.setQuestionId(10L);
        job.setRevision(3L);
        job.setJobType("INDEX");
        job.setStatus(QuestionIndexJob.Status.READY);
        when(jobs.findByQuestionIdOrderByUpdatedAtDesc(10L)).thenReturn(List.of(job));

        var status = controller.indexStatus(teacher(), 10L).getData();
        assertEquals("READY", status.status());
        assertEquals(3L, status.revision());
    }

    @Test
    @DisplayName("未启用 AI 的题索引状态为 DISABLED")
    void disabledWhenNotAiEnabled() {
        Question q = question();
        q.setAiPracticeEnabled(false);
        when(questions.findById(10L)).thenReturn(Optional.of(q));
        when(jobs.findByQuestionIdOrderByUpdatedAtDesc(10L)).thenReturn(List.of());
        var status = controller.indexStatus(teacher(), 10L).getData();
        assertEquals("DISABLED", status.status());
    }

    @Test
    @DisplayName("reindex 幂等排队")
    void reindexEnqueues() {
        when(questions.findById(10L)).thenReturn(Optional.of(question()));
        var job = new QuestionIndexJob();
        job.setRevision(3L);
        job.setStatus(QuestionIndexJob.Status.PENDING);
        when(indexService.enqueueCurrent(10L, 3L)).thenReturn(job);
        var status = controller.reindex(teacher(), 10L).getData();
        assertEquals("PENDING", status.status());
        verify(indexService).enqueueCurrent(10L, 3L);
    }
}
