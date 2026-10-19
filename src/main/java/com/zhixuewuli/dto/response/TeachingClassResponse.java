package com.zhixuewuli.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeachingClassResponse {
    private Long id;
    private String name;
    private String grade;
    private String status;
    private long studentCount;
    private long teacherCount;
    private String role;
}
