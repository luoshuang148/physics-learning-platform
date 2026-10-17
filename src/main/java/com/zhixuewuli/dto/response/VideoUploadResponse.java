package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "视频上传响应")
public class VideoUploadResponse {
    @Schema(description = "视频访问URL", example = "/videos/abc123.mp4")
    private String url;
    
    @Schema(description = "原始文件名", example = "lesson1.mp4")
    private String originalFilename;
    
    @Schema(description = "文件大小(字节)", example = "104857600")
    private Long fileSize;

    @Schema(description = "视频时长(秒)", example = "180")
    private Integer duration;

    public static Integer fromSeconds(Double seconds) {
        if (seconds == null || !Double.isFinite(seconds) || seconds < 0) {
            return null;
        }
        return BigDecimal.valueOf(seconds)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }
}
