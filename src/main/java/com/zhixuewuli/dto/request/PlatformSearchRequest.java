package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * POST /ai/student/platform-search 请求（无需生成模型）。
 */
@Data
public class PlatformSearchRequest {

    @Size(min = 1, max = 500, message = "查询文本长度 1～500 字")
    private String query;

    private Long eventId;

    @Size(max = 15, message = "知识点最多 15 个")
    private List<String> knowledgePointCodes;

    @Min(1) @Max(5)
    private Integer difficulty;

    /** 是否排除本人已作答题（含首次作答与补练） */
    private boolean excludeAttempted = false;

    @Min(1) @Max(5)
    private int limit = 5;
}
