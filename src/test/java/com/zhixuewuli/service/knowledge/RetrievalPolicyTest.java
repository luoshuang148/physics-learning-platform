package com.zhixuewuli.service.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit") class RetrievalPolicyTest {
    private final RetrievalPolicy policy = new RetrievalPolicy();

    @Test void rrf_deduplicates_and_orders_candidates() {
        var keyword = List.of(hit(1, 1, .9), hit(2, 2, .7), hit(3, 3, .6));
        var vector = List.of(hit(1, 2, .8), hit(4, 1, .95), hit(5, 3, .5));
        var fused = policy.fuse(keyword, vector, 5);
        assertThat(fused).hasSize(5);
        assertThat(fused.get(0).chunkId()).isEqualTo(1L);
        assertThat(fused.get(0).keywordRank()).isEqualTo(1);
        assertThat(fused.get(0).vectorRank()).isEqualTo(2);
        assertThat(fused.get(0).rrfScore()).isEqualTo(1d / 61 + 1d / 62);
        assertThat(fused.get(1).chunkId()).isEqualTo(4L);
    }

    private KnowledgeSearchHit hit(long id, int rank, double score) {
        return new KnowledgeSearchHit(id, 10L, 20L, 30L, 40L, "题目", 1, "力学", "正文", rank, score);
    }
}
