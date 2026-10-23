package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LearningPlanPreviewRequest(@NotNull Long eventId, @Min(1) @Max(7) int days,
                                          @NotBlank @Size(max = 800) String question,
                                          @NotBlank @Size(max = 36) String requestId) {}
