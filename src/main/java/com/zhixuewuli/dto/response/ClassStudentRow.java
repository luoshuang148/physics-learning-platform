package com.zhixuewuli.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClassStudentRow {
    private Long userId;
    private String username;
    private String realName;
    private String className;
    private boolean active;
}
