package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.dto.response.KnowledgePointResponse;
import com.zhixuewuli.dto.response.PracticeCandidate;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.KnowledgePointRepository;
import com.zhixuewuli.repository.QuestionKnowledgePointRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.question.AnswerAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PracticeRecommendationService {
    private final KnowledgePointRepository knowledgePoints;
    private final QuestionKnowledgePointRepository questionKnowledgePoints;
    private final QuestionRepository questions;
    private final AnswerAccessPolicy answerAccess;

    @Transactional(readOnly = true)
    public List<KnowledgePointResponse> knowledgePoints() {
        return knowledgePoints.findAllByOrderByTopicAscNameAsc().stream()
                .map(point -> new KnowledgePointResponse(point.getCode(), point.getName(), point.getTopic())).toList();
    }

    @Transactional(readOnly = true)
    public List<PracticeCandidate> recommend(Long viewerId, Long eventId, Collection<String> requestedCodes, int requestedLimit) {
        if (viewerId == null) throw new IllegalArgumentException("需要登录后获取练习推荐");
        if (requestedCodes == null || requestedCodes.isEmpty()) return List.of();
        if (requestedCodes.size() > 15 || requestedLimit < 1) throw new IllegalArgumentException("知识点最多15个，推荐数量须为正数");
        int limit = Math.min(requestedLimit, 6);
        var codes = requestedCodes.stream().filter(code -> code != null && !code.isBlank()).map(String::strip).distinct().toList();
        if (codes.isEmpty()) return List.of();
        var known = knowledgePoints.findByCodeIn(codes).stream().map(point -> point.getCode()).collect(java.util.stream.Collectors.toSet());
        if (known.size() != codes.size()) throw new IllegalArgumentException("包含未知知识点代码");
        Collection<Long> ids = questionKnowledgePoints.findQuestionIdsByKnowledgePointCodes(codes,
                org.springframework.data.domain.PageRequest.of(0, 200));
        if (ids.isEmpty()) return List.of();
        List<Question> candidates = questions.findByIdIn(new ArrayList<>(ids));
        return candidates.stream()
                .filter(question -> !answerAccess.blocksPracticeBeforeAssignment(viewerId, eventId, question.getId()))
                .map(question -> new CandidateWithScore(question, matchingCodes(question, codes)))
                .filter(candidate -> !candidate.matchedCodes().isEmpty())
                .sorted((left, right) -> {
                    int match = Integer.compare(right.matchedCodes().size(), left.matchedCodes().size());
                    if (match != 0) return match;
                    int difficulty = Integer.compare(left.question().getDifficulty() == null ? 3 : left.question().getDifficulty(),
                            right.question().getDifficulty() == null ? 3 : right.question().getDifficulty());
                    return difficulty != 0 ? difficulty : Long.compare(left.question().getId(), right.question().getId());
                })
                .limit(limit)
                .map(candidate -> new PracticeCandidate(candidate.question().getId(), candidate.question().getContent(),
                        candidate.question().getDifficulty(), candidate.matchedCodes()))
                .toList();
    }

    private List<String> matchingCodes(Question question, List<String> requestedCodes) {
        return question.getKnowledgePointCodes().stream().filter(requestedCodes::contains).sorted().toList();
    }

    private record CandidateWithScore(Question question, List<String> matchedCodes) {}
}
