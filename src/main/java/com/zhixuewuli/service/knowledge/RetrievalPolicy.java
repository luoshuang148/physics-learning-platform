package com.zhixuewuli.service.knowledge;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class RetrievalPolicy {
    public static final int RRF_K=60;
    public List<FusedCandidate> fuse(List<KnowledgeSearchHit> keyword, List<KnowledgeSearchHit> vector, int limit) {
        Map<Long,KnowledgeSearchHit> hits=new HashMap<>(); Map<Long,Integer> kr=new HashMap<>(), vr=new HashMap<>();
        for(var h:keyword){hits.put(h.chunkId(),h);kr.put(h.chunkId(),h.rank());}
        for(var h:vector){hits.putIfAbsent(h.chunkId(),h);vr.put(h.chunkId(),h.rank());}
        return hits.keySet().stream().map(id->{double score=(kr.containsKey(id)?1d/(RRF_K+kr.get(id)):0)+(vr.containsKey(id)?1d/(RRF_K+vr.get(id)):0);
            return new FusedCandidate(hits.get(id),kr.get(id),vr.get(id),score);})
                .sorted(Comparator.comparingDouble(FusedCandidate::rrfScore).reversed().thenComparing(FusedCandidate::chunkId))
                .limit(Math.min(5,Math.max(0,limit))).toList();
    }
}
