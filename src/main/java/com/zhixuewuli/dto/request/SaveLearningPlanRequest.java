package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveLearningPlanRequest(@NotBlank @Size(max=36) String runId,
                                      @NotBlank @Size(max=36) String requestId) {}
