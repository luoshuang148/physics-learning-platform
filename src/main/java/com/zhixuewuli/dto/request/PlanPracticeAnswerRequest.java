package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 计划条目补练提交：服务端确定题目与归属。 */
@Data
public class PlanPracticeAnswerRequest {

    @NotBlank(message = "userAnswer 不能为空")
    @Size(max = 50)
    private String userAnswer;

    @NotNull(message = "requestId 不能为空")
    private String requestId;
}
