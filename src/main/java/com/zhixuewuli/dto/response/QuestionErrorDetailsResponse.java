package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Schema(description = "题目错误详情响应")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuestionErrorDetailsResponse {

    @Schema(description = "学生错误列表")
    private List<QuestionStudentErrorDetailResponse> studentList;

    @Schema(description = "词云数据（词汇->频次）")
    private Map<String, Integer> wordCloud;
}
