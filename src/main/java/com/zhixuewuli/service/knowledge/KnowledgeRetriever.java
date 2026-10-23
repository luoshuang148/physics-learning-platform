package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.common.exception.KnowledgeNotFoundException;
import com.zhixuewuli.dto.knowledge.*;
import com.zhixuewuli.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.BooleanSupplier;

@Service @RequiredArgsConstructor @Slf4j
public class KnowledgeRetriever {
    private final TeacherEventRepository events; private final KnowledgeAccessPolicy access;
    private final KnowledgeDocumentService documents; private final KnowledgeDocumentRepository indexRepository;
    private final KeywordKnowledgeSearch keyword;
    private final EmbeddingGateway embeddings; private final KnowledgeVectorStore vectors;
    private final RetrievalPolicy policy; private final KnowledgeProperties properties;

    public RetrievalResult retrieve(Long viewerId,Long eventId,String query,RetrievalMode mode,BooleanSupplier cancelled) {
        long start=System.nanoTime();
        if(!properties.isEnabled())return result(RetrievalStatus.UNAVAILABLE,List.of(),"disabled",List.of(),start);
        var event=events.findById(eventId).orElseThrow(KnowledgeNotFoundException::new);
        if(event.getClassId()==null)throw new KnowledgeNotFoundException();
        access.requireReader(viewerId,event.getClassId());
        if(query==null||query.isBlank())return result(RetrievalStatus.INSUFFICIENT,List.of(),mode.name().toLowerCase(),List.of(),start);
        var indexes=indexRepository.findActiveIndexesByClassId(event.getClassId());
        var versions=indexes.stream().map(ActiveKnowledgeIndexRow::indexVersion).distinct().toList();
        List<KnowledgeSearchHit> lexical=List.of(),semantic=new ArrayList<>(); boolean vectorUnavailable=false;
        if(mode!=RetrievalMode.VECTOR) try { lexical=keyword.search(event.getClassId(),query,10); }
        catch(RuntimeException failure) { log.warn("知识库词法检索失败，classId={}",event.getClassId(),failure); return result(RetrievalStatus.UNAVAILABLE,List.of(),mode.name().toLowerCase(),versions,start); }
        try {
            if(mode!=RetrievalMode.KEYWORD && !indexes.isEmpty()) {
                if(cancelled.getAsBoolean())return result(RetrievalStatus.UNAVAILABLE,List.of(),"cancelled",versions,start);
                var batch=embeddings.embed(List.of(query),true,cancelled);
                if(batch.vectors().size()!=1 || batch.dimensions()!=properties.getEmbeddingDimensions() || !properties.getEmbeddingModel().equals(batch.model()))throw new KnowledgeEmbeddingException("query_embedding_identity_mismatch");
                float[] vector=batch.vectors().get(0); int rank=0; var all=new ArrayList<KnowledgeSearchHit>();
                for(var index:indexes){if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();
                    for(var hit:vectors.search(index.indexVersion(),event.getClassId(),index.revisionId(),vector,10))if(hit.score()>=properties.getMinVectorScore())
                        all.add(new KnowledgeSearchHit(hit.chunkId(),index.documentId(),hit.revisionId(),hit.classId(),(long)hit.ordinal(),index.documentId().toString(),null,null,null,0,hit.score()));}
                all.sort(Comparator.comparingDouble(KnowledgeSearchHit::rawScore).reversed().thenComparing(KnowledgeSearchHit::chunkId));
                for(var h:all.subList(0,Math.min(10,all.size())))semantic.add(new KnowledgeSearchHit(h.chunkId(),h.documentId(),h.revisionId(),h.classId(),h.ordinal(),h.title(),null,null,null,++rank,h.rawScore()));
            }
        } catch(RuntimeException failure) { vectorUnavailable=mode!=RetrievalMode.KEYWORD; if(!vectorUnavailable)throw failure; log.warn("知识库向量检索失败，classId={}",event.getClassId(),failure); }
        if(vectorUnavailable && !(properties.isAllowLexicalFallback() && mode==RetrievalMode.HYBRID))
            return result(RetrievalStatus.UNAVAILABLE,List.of(),mode.name().toLowerCase(),versions,start);
        String actual=vectorUnavailable?"lexical_only":mode.name().toLowerCase();
        var candidates=policy.fuse(lexical,semantic,5); var passages=new ArrayList<KnowledgePassage>(); int source=0;
        for(var candidate:candidates){
            var fresh=documents.requireActiveReadable(viewerId,candidate.chunkId());
            passages.add(new KnowledgePassage("S"+(++source),fresh.chunkId(),candidate.hit().documentId(),fresh.revisionId(),fresh.title(),fresh.page(),fresh.section(),fresh.text(),candidate.keywordRank(),candidate.keywordRank()==null?null:candidate.hit().rawScore(),candidate.vectorRank(),candidate.vectorRank()==null?null:findScore(semantic,candidate.chunkId()),candidate.rrfScore()));
        }
        return result(passages.isEmpty()?RetrievalStatus.INSUFFICIENT:RetrievalStatus.MATCHED,passages,actual,versions,start);
    }
    private double findScore(List<KnowledgeSearchHit> hits,Long id){return hits.stream().filter(h->h.chunkId().equals(id)).findFirst().map(KnowledgeSearchHit::rawScore).orElse(0d);}
    private RetrievalResult result(RetrievalStatus s,List<KnowledgePassage> p,String mode,List<String> versions,long start){return new RetrievalResult(s,p,mode,versions,(System.nanoTime()-start)/1_000_000);}
}
