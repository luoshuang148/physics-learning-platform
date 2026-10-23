package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.common.exception.*;
import com.zhixuewuli.common.result.PageResult;
import com.zhixuewuli.dto.knowledge.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.data.domain.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor @Slf4j
public class KnowledgeDocumentService {
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeRevisionRepository revisions;
    private final KnowledgeChunkRepository chunks;
    private final TeachingClassRepository classes;
    private final KnowledgeAccessPolicy access;
    private final KnowledgeDocumentStore store;
    private final KnowledgeProperties properties;
    @Transactional
    public KnowledgeDocumentStatus upload(Long viewerId,Long classId,String title,MultipartFile file){
        enabled();access.requireTeacher(viewerId,classId);
        if(title==null || title.isBlank() || title.strip().length()>120)throw new BusinessException("资料标题需为1至120字符");
        // Serialize the quota check in this class, without locking unrelated classes.
        classes.lockById(classId);
        if(documents.countByClassIdAndWithdrawnAtIsNull(classId)>=Math.min(properties.getMaxDocumentsPerClass(),50))
            throw new BusinessException("本班资料数量已达上限，请撤回不再使用的资料");
        var saved=store.store(file);
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)cleanup(saved.key());}
            });
        try {
            var document=new KnowledgeDocument();document.setClassId(classId);document.setOwnerId(viewerId);
            document.setTitle(title.strip());document.setStorageKey(saved.key());document.setMediaType(saved.mediaType());
            document.setContentSha256(saved.sha256());document=documents.saveAndFlush(document);
            var revision=new KnowledgeRevision();revision.setDocumentId(document.getId());revision.setContentSha256(saved.sha256());
            revision=revisions.saveAndFlush(revision);return view(document,revision);
        } catch(RuntimeException error){cleanup(saved.key());throw error;}
    }
    @Transactional(readOnly=true)
    public KnowledgeChunkView requireReadable(Long viewerId,Long chunkId){
        enabled();var chunk=chunks.findById(chunkId).orElseThrow(KnowledgeNotFoundException::new);
        var revision=revisions.findById(chunk.getRevisionId()).orElseThrow(KnowledgeNotFoundException::new);
        var document=documents.findById(revision.getDocumentId()).orElseThrow(KnowledgeNotFoundException::new);
        access.requireReader(viewerId,document.getClassId());
        if(document.getWithdrawnAt()!=null || revision.getStatus()!=KnowledgeRevision.Status.READY)throw new KnowledgeNotFoundException();
        return new KnowledgeChunkView(chunk.getId(),revision.getId(),document.getTitle(),chunk.getPageNo(),chunk.getSectionTitle(),chunk.getText());
    }
    @Transactional
    public void withdraw(Long viewerId,Long documentId){
        enabled();var document=documents.lockById(documentId).orElseThrow(KnowledgeNotFoundException::new);
        access.requireTeacher(viewerId,document.getClassId());
        if(document.getWithdrawnAt()==null)document.setWithdrawnAt(LocalDateTime.now());
        documents.save(document); // Revoke reads now; durable index/file cleanup is handled by recovery.
    }
    @Transactional
    public KnowledgeDocumentStatus requestReindex(Long viewerId,Long documentId){
        enabled();var document=documents.lockById(documentId).orElseThrow(KnowledgeNotFoundException::new);
        access.requireTeacher(viewerId,document.getClassId());
        if(document.getWithdrawnAt()!=null)throw new KnowledgeNotFoundException();
        var latest=revisions.findFirstByDocumentIdOrderByIdDesc(documentId);
        if(latest.isPresent() && Set.of(KnowledgeRevision.Status.UPLOADED,KnowledgeRevision.Status.INDEXING).contains(latest.get().getStatus()))
            return view(document,latest.get());
        var revision=new KnowledgeRevision();revision.setDocumentId(documentId);revision.setContentSha256(document.getContentSha256());
        return view(document,revisions.saveAndFlush(revision));
    }
    @Transactional(readOnly=true)
    public PageResult<KnowledgeDocumentStatus> list(Long viewerId,Long classId,int page,int size){
        enabled();access.requireTeacher(viewerId,classId);
        if(page<1 || size<1 || size>50)throw new IllegalArgumentException("分页范围不合法");
        var result=documents.findByClassIdAndWithdrawnAtIsNull(classId,PageRequest.of(page-1,size,Sort.by("id").descending()));
        var ids=result.getContent().stream().map(KnowledgeDocument::getId).toList();
        Map<Long,KnowledgeRevision> latest=ids.isEmpty()?Map.of():revisions.latestForDocuments(ids).stream()
                .collect(Collectors.toMap(KnowledgeRevision::getDocumentId,r->r));
        return PageResult.<KnowledgeDocumentStatus>builder().total(result.getTotalElements()).pages(result.getTotalPages())
                .currentPage(page).pageSize(size).records(result.getContent().stream().map(d->view(d,latest.get(d.getId()))).toList()).build();
    }
    private KnowledgeDocumentStatus view(KnowledgeDocument d,KnowledgeRevision r){
        return new KnowledgeDocumentStatus(d.getId(),r==null?null:r.getId(),d.getClassId(),d.getTitle(),
                d.getWithdrawnAt()!=null?"WITHDRAWN":r==null?"UPLOADED":r.getStatus().name(),r==null?null:r.getErrorCode());
    }
    private void enabled(){if(!properties.isEnabled())throw new BusinessException("知识库功能暂未启用");}
    private void cleanup(String key){try{store.delete(key);}catch(RuntimeException cleanupError){log.warn("知识库未提交文件清理失败，等待人工或恢复任务处理");}}
}
