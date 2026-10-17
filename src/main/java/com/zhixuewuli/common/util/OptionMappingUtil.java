package com.zhixuewuli.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 选项和视频映射工具类
 * 
 * 专门处理题目选项和讲解视频映射的JSON序列化/反序列化操作。
 * 提供便捷的方法来操作ABC选项结构。
 * 
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>选项Map的JSON序列化和反序列化</li>
 * <li>视频映射的JSON序列化和反序列化</li>
 * <li>选项验证和格式化</li>
 * <li>默认选项生成</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptionMappingUtil {

    private final ObjectMapper objectMapper;

    // 选项标识常量
    public static final String OPTION_A = "A";
    public static final String OPTION_B = "B";
    public static final String OPTION_C = "C";
    public static final String OPTION_D = "D";

    // 默认选项内容
    private static final Map<String, String> DEFAULT_OPTIONS = Map.of(
            OPTION_A, "选项A",
            OPTION_B, "选项B",
            OPTION_C, "选项C",
            OPTION_D, "选项D");

    /**
     * 将选项Map转换为JSON字符串
     * 
     * @param options 选项Map，key为A/B/C/D，value为选项内容
     * @return JSON字符串
     * @throws BusinessException 转换失败时抛出
     */
    public String optionsToJson(Map<String, String> options) {
        try {
            validateOptions(options);
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            log.error("选项转换为JSON失败", e);
            throw new BusinessException("选项格式转换失败: " + e.getMessage());
        }
    }

    /**
     * 将JSON字符串转换为选项Map
     * 
     * @param jsonOptions JSON格式的选项字符串
     * @return 选项Map
     * @throws BusinessException 解析失败时抛出
     */
    public Map<String, String> jsonToOptions(String jsonOptions) {
        try {
            if (jsonOptions == null || jsonOptions.trim().isEmpty()) {
                return new HashMap<>(DEFAULT_OPTIONS);
            }

            Map<String, String> options = objectMapper.readValue(jsonOptions,
                    new TypeReference<Map<String, String>>() {
                    });

            validateOptions(options);
            return options;
        } catch (Exception e) {
            log.error("解析选项JSON失败: {}", jsonOptions, e);
            throw new BusinessException("选项解析失败: " + e.getMessage());
        }
    }

    /**
     * 将视频映射转换为JSON字符串
     * 
     * @param videoMapping 视频映射Map，key为A/B/C/D，value为视频标题
     * @return JSON字符串，当videoMapping为null时返回"{}"
     * @throws BusinessException 转换失败时抛出
     */
    public String videoMappingToJson(Map<String, String> videoMapping) {
        try {
            if (videoMapping == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(videoMapping);
        } catch (Exception e) {
            log.error("视频映射转换为JSON失败", e);
            throw new BusinessException("视频映射格式转换失败: " + e.getMessage());
        }
    }

    /**
     * 将JSON字符串转换为视频映射Map
     * 
     * @param jsonMapping JSON格式的视频映射字符串
     * @return 视频映射Map，key为选项标识(A/B/C/D)，value为视频ID(Long类型)
     * @throws BusinessException 解析失败时抛出
     */
    public Map<String, Long> jsonToVideoMapping(String jsonMapping) {
        try {
            if (jsonMapping == null || jsonMapping.trim().isEmpty()) {
                return new HashMap<>();
            }

            return objectMapper.readValue(jsonMapping,
                    new TypeReference<Map<String, Long>>() {
                    });
        } catch (Exception e) {
            log.error("解析视频映射JSON失败: {}", jsonMapping, e);
            throw new BusinessException("视频映射解析失败: " + e.getMessage());
        }
    }

    /**
     * 验证选项Map的格式
     * 
     * @param options 选项Map
     * @throws BusinessException 格式不正确时抛出
     */
    public void validateOptions(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            throw new BusinessException("选项不能为空");
        }

        // 检查必需的选项标识
        for (String optionKey : new String[] { OPTION_A, OPTION_B, OPTION_C, OPTION_D }) {
            if (!options.containsKey(optionKey)) {
                throw new BusinessException("缺少选项: " + optionKey);
            }
            if (options.get(optionKey) == null || options.get(optionKey).trim().isEmpty()) {
                throw new BusinessException("选项内容不能为空: " + optionKey);
            }
        }

        // 检查是否有额外的选项标识
        if (options.size() != 4) {
            throw new BusinessException("选项数量必须为4个(A/B/C/D)");
        }
    }

    /**
     * 获取默认选项Map
     * 
     * @return 默认选项Map
     */
    public Map<String, String> getDefaultOptions() {
        return new HashMap<>(DEFAULT_OPTIONS);
    }

    /**
     * 根据选项标识获取选项内容
     * 
     * @param options   选项Map
     * @param optionKey 选项标识(A/B/C/D)
     * @return 选项内容
     * @throws BusinessException 选项不存在时抛出
     */
    public String getOptionContent(Map<String, String> options, String optionKey) {
        if (options == null || !options.containsKey(optionKey)) {
            throw new BusinessException("选项不存在: " + optionKey);
        }
        return options.get(optionKey);
    }

    /**
     * 获取选项对应的视频标题
     * 
     * @param videoMapping 视频映射Map
     * @param optionKey    选项标识(A/B/C/D)
     * @return 视频标题，不存在时返回null
     */
    public String getVideoTitleForOption(Map<String, String> videoMapping, String optionKey) {
        if (videoMapping == null) {
            return null;
        }
        return videoMapping.get(optionKey);
    }

    /**
     * 设置选项对应的视频标题
     * 
     * @param videoMapping 视频映射Map
     * @param optionKey    选项标识(A/B/C/D)
     * @param videoTitle   视频标题
     * @throws BusinessException 当videoMapping为null时抛出
     */
    public void setVideoTitleForOption(Map<String, String> videoMapping, String optionKey, String videoTitle) {
        if (videoMapping == null) {
            throw new BusinessException("视频映射不能为空");
        }
        videoMapping.put(optionKey, videoTitle);
    }
}