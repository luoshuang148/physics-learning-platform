package com.zhixuewuli.controller;
import com.zhixuewuli.common.exception.KnowledgeNotFoundException;
import com.zhixuewuli.common.exception.handler.GlobalExceptionHandler;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.dto.knowledge.KnowledgeDocumentStatus;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.service.knowledge.KnowledgeDocumentService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
@Tag("unit")
class KnowledgeControllerContractTest {
    KnowledgeDocumentService service=mock(KnowledgeDocumentService.class);
    MockMvc mvc;
    @BeforeEach void setup(){
        var user=new AuthenticatedUser(17L,"fixture",User.UserType.TEACHER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.authorities()));
        mvc=standaloneSetup(new KnowledgeController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void uploadUsesAuthenticatedIdentityAndReturnsAcceptedStatus() throws Exception {
        when(service.upload(eq(17L),eq(10L),eq("讲义"),any())).thenReturn(new KnowledgeDocumentStatus(1L,2L,10L,"讲义","UPLOADED",null));
        mvc.perform(multipart("/knowledge/documents").file(new MockMultipartFile("file","notes.md","text/plain","text".getBytes()))
                .param("classId","10").param("title","讲义").param("ownerId","999"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.documentId").value(1));
    }
    @Test void missingSourceHasNotFoundContract() throws Exception {
        when(service.requireReadable(17L,123L)).thenThrow(new KnowledgeNotFoundException());
        mvc.perform(get("/knowledge/chunks/123")).andExpect(status().isNotFound());
    }
}
