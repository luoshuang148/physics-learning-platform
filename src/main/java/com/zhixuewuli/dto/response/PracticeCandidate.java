package com.zhixuewuli.dto.response;

import java.util.List;

/** A safe recommendation preview. It intentionally has no options, answer, or explanation fields. */
public record PracticeCandidate(Long questionId, String content, Integer difficulty,
                                List<String> matchedKnowledgePointCodes) {}
