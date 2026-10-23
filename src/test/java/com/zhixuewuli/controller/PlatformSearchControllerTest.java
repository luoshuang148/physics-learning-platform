package com.zhixuewuli.controller;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.*;
import com.zhixuewuli.dto.request.PlatformSearchRequest;
import com.zhixuewuli.service.platformsearch.PlatformQuestionRetriever;
import com.zhixuewuli.service.platformsearch.PlatformSourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 平台检索控制器契约：参数映射、来源回源、不暴露向量分数。
 */
@Tag("unit")
@DisplayName("PlatformSearchController 契约")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformSearchControllerTest {

    @Mock PlatformQuestionRetriever retriever;
    @Mock PlatformSourceService sources;
    @InjectMocks PlatformSearchController controller;

    private com.zhixuewuli.common.security.AuthenticatedUser student() {
        return new com.zhixuewuli.common.security.AuthenticatedUser(
                100L, "student", com.zhixuewuli.entity.User.UserType.STUDENT);
    }

    @Test
    @DisplayName("search 将请求映射为 HYBRID SearchRequest 且不泄露向量分数")
    void searchMapsRequest() {
        when(retriever.retrieve(eq(100L), any(), any())).thenReturn(new SearchResult(
                RetrievalStatus.MATCHED, "hybrid",
                List.of(new QuestionHit(1L, 1L, "题干", 2, List.of("kp"), List.of("Q:1:1:QUESTION:0"))),
                List.of(new Evidence("Q:1:1:QUESTION:0", 1L, 1L, "QUESTION", 0, "原文")),
                12L));

        PlatformSearchRequest request = new PlatformSearchRequest();
        request.setQuery("作用力与反作用力");
        request.setLimit(5);
        request.setExcludeAttempted(true);

        var result = controller.search(student(), request);
        assertEquals(200, result.getCode());
        Map<String, Object> body = result.getData();
        assertEquals("MATCHED", body.get("status"));
        assertEquals("hybrid", body.get("actualMode"));
        assertFalse(body.toString().contains("vectorScore"));
        assertFalse(body.toString().contains("rrfScore"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(retriever).retrieve(eq(100L), captor.capture(), any());
        assertEquals(RetrievalMode.HYBRID, captor.getValue().mode());
        assertTrue(captor.getValue().excludeAttempted());
    }

    @Test
    @DisplayName("source 接口返回证据并再次鉴权")
    void sourceReturnsEvidence() {
        when(sources.requireReadable(100L, "Q:1:1:QUESTION:0"))
                .thenReturn(new Evidence("Q:1:1:QUESTION:0", 1L, 1L, "QUESTION", 0, "原文"));
        var result = controller.source(student(), "Q:1:1:QUESTION:0");
        assertEquals("原文", result.getData().get("text"));
        verify(sources).requireReadable(100L, "Q:1:1:QUESTION:0");
    }

    @Test
    @DisplayName("非法来源 ID 返回业务错误而非 500")
    void invalidSourceRejected() {
        when(sources.requireReadable(100L, "bad"))
                .thenThrow(new com.zhixuewuli.common.exception.BusinessException("来源不存在或已失效"));
        assertThrows(com.zhixuewuli.common.exception.BusinessException.class,
                () -> controller.source(student(), "bad"));
    }
}
