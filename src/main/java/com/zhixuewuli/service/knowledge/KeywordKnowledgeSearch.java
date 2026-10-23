package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.repository.KnowledgeChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class KeywordKnowledgeSearch {
    private final KnowledgeChunkRepository chunks;
    public List<KnowledgeSearchHit> search(Long classId,String query,int limit) {
        var rows=chunks.searchActiveByClassId(classId,query,Math.min(10,limit)); var hits=new ArrayList<KnowledgeSearchHit>(); int rank=0;
        for(var row:rows)hits.add(new KnowledgeSearchHit(row.getChunkId(),row.getDocumentId(),row.getRevisionId(),row.getClassId(),row.getOrdinalNo(),row.getTitle(),row.getPageNo(),row.getSectionTitle(),row.getChunkText(),++rank,row.getScore()));
        return List.copyOf(hits);
    }
}
