package com.zhixuewuli.dto;

import com.zhixuewuli.common.constant.AppConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "分页请求参数")
@Data
public class PageRequestDTO {
    /** 页码，从1开始 */
    @Min(value = AppConstants.MIN_PAGE_NUMBER, message = "页码必须大于0")
    private int page = AppConstants.DEFAULT_PAGE_NUMBER;

    /** 页面大小 */
    @Min(value = AppConstants.MIN_PAGE_SIZE, message = "页面大小必须大于0")
    @Max(value = AppConstants.MAX_PAGE_SIZE, message = "页面大小不能超过" + AppConstants.MAX_PAGE_SIZE)
    private int size = AppConstants.DEFAULT_PAGE_SIZE;

    /** 排序字段 */
    @Pattern(regexp = AppConstants.REGEX_SORT_FIELD, message = "排序字段不合法")
    private String sortBy;

    @Pattern(regexp = "^(ASC|DESC)$", message = "排序方向只能是ASC或DESC")
    private String sortDirection; // ASC/DESC
}
