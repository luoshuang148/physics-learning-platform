package com.zhixuewuli.dto.agent;

import java.util.List;

public record WrongQuestionFact(String recordId, Long questionId, String content, String studentAnswer,
                                Integer difficulty, List<String> knowledgePointCodes) {}
