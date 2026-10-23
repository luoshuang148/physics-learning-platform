package com.zhixuewuli.controller;
import com.zhixuewuli.service.knowledge.KnowledgeDocumentService;
import com.zhixuewuli.common.result.*;
import com.zhixuewuli.common.security.*;
import com.zhixuewuli.dto.knowledge.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/knowledge") @RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeDocumentService service;
    @TeacherOnly @PostMapping("/documents") @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<KnowledgeDocumentStatus> upload(@AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam Long classId,@RequestParam String title,@RequestParam MultipartFile file){
        return Result.success(service.upload(viewer.id(),classId,title,file));
    }
    @TeacherOnly @GetMapping("/documents")
    public Result<PageResult<KnowledgeDocumentStatus>> list(@AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam Long classId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
        return Result.success(service.list(viewer.id(),classId,page,size));
    }
    @TeacherOnly @PostMapping("/documents/{id}/reindex") @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<KnowledgeDocumentStatus> reindex(@AuthenticationPrincipal AuthenticatedUser viewer,@PathVariable Long id){
        return Result.success(service.requestReindex(viewer.id(),id));
    }
    @TeacherOnly @DeleteMapping("/documents/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthenticatedUser viewer,@PathVariable Long id){service.withdraw(viewer.id(),id);}
    @GetMapping("/chunks/{id}")
    public Result<KnowledgeChunkView> chunk(@AuthenticationPrincipal AuthenticatedUser viewer,@PathVariable Long id){
        return Result.success(service.requireReadable(viewer.id(),id));
    }
}
