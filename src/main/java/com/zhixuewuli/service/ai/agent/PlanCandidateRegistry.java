package com.zhixuewuli.service.ai.agent;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Evidence;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.EligibleQuestion;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本次 Agent 运行的候选与来源积累。
 * 跨多次检索取并集并按 questionId+revision 去重，不仅保留最后一次工具结果。
 * 成功预览保存 registry 快照供后续保存/激活核对。
 */
@Component
@RequiredArgsConstructor
public class PlanCandidateRegistry {

    private final Map<String, EligibleQuestion> candidates = new LinkedHashMap<>();
    private final Map<String, Evidence> sources = new LinkedHashMap<>();

    /** 合并一次检索结果（并集 + questionId+revision 去重）。 */
    public synchronized void add(SearchResult result) {
        if (result == null) return;
        for (var hit : result.questions()) {
            candidates.put(hit.questionId() + ":" + hit.revision(),
                    new EligibleQuestion(hit.questionId(), hit.revision()));
        }
        for (Evidence source : result.sources()) {
            sources.put(source.sourceId(), source);
            candidates.putIfAbsent(source.questionId() + ":" + source.revision(),
                    new EligibleQuestion(source.questionId(), source.revision()));
        }
    }

    public synchronized void addSource(Evidence evidence) {
        if (evidence == null) return;
        sources.put(evidence.sourceId(), evidence);
        candidates.putIfAbsent(evidence.questionId() + ":" + evidence.revision(),
                new EligibleQuestion(evidence.questionId(), evidence.revision()));
    }

    public synchronized boolean contains(Long questionId, long revision) {
        return candidates.containsKey(questionId + ":" + revision);
    }

    public synchronized boolean containsSource(String sourceId) {
        return sources.containsKey(sourceId);
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("candidates", new ArrayList<>(candidates.values()));
        snapshot.put("sources", sources.values().stream().map(e -> Map.of(
                "sourceId", e.sourceId(),
                "questionId", e.questionId(),
                "revision", e.revision(),
                "part", e.part(),
                "ordinal", e.ordinal())).toList());
        return snapshot;
    }

    public synchronized List<EligibleQuestion> candidateList() {
        return new ArrayList<>(candidates.values());
    }

    public synchronized void clear() {
        candidates.clear();
        sources.clear();
    }
}
