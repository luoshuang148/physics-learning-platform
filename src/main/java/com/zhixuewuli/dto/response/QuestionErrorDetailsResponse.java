package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "题目错误详情响应")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuestionErrorDetailsResponse {

    @Schema(description = "学生错误列表")
    private List<QuestionStudentErrorDetailResponse> studentList;

}
