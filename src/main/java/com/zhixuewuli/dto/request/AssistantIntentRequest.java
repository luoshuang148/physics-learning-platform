package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssistantIntentRequest {
    @NotBlank @Size(max = 800)
    private String question;
    @Size(max = 800)
    private String previousQuestion;
    @Size(max = 16)
    private String previousIntent;
}
