package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ClassMemberRequest {
    @NotNull(message = "用户 ID 不能为空")
    private Long userId;
}
