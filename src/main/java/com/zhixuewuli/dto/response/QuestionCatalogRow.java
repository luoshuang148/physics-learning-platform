package com.zhixuewuli.dto.response;

/** Bounded list preview. Full options, explanations and image bodies are detail-only. */
public record QuestionCatalogRow(Long id, String content, String correctOption, Integer difficulty, Boolean hasImage) {}
