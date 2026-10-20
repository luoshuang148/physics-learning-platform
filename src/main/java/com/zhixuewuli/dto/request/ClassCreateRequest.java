package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClassCreateRequest {
    @NotBlank(message = "班级名称不能为空")
    @Size(max = 80, message = "班级名称不能超过80字符")
    private String name;
    @Size(max = 30, message = "年级不能超过30字符")
    private String grade;
}
