package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.Min;

public record LearningPlanVersionRequest(@Min(1) long expectedVersion) {}
