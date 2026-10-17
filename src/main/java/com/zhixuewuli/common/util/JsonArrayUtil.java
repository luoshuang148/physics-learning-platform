package com.zhixuewuli.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.experiment.ExperimentBusinessException;
import com.zhixuewuli.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 数组工具类
 * 
 * <p>
 * 提供 JSON 数组与 List 之间的转换功能。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Component
public class JsonArrayUtil {
    
    private final ObjectMapper objectMapper;
    
    public JsonArrayUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 将 JSON 数组字符串转换为 List<Long>
     * 
     * @param json JSON 数组字符串
     * @return List<Long> Long 类型的列表
     * @throws ExperimentBusinessException 如果 JSON 格式无效
     */
    public List<Long> jsonToList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, Long.class));
        } catch (JsonProcessingException e) {
            // 检查是否是因为非数字元素导致的错误
            if (e.getMessage().contains("Cannot coerce") || 
                e.getMessage().contains("not a valid `java.lang.Long`")) {
                throw new ExperimentBusinessException(ResultCode.INVALID_QUESTION_IDS_FORMAT, 
                    "列表必须包含数字类型");
            }
            // 其他 JSON 格式错误
            throw new ExperimentBusinessException(ResultCode.INVALID_QUESTION_IDS_FORMAT, 
                "题目 ID 列表格式无效");
        }
    }
    
    /**
     * 将 List<Long> 转换为 JSON 数组字符串
     * 
     * @param list Long 类型的列表
     * @return String JSON 数组字符串
     * @throws ExperimentBusinessException 如果转换失败
     */
    public String listToJson(List<Long> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new ExperimentBusinessException(ResultCode.INVALID_QUESTION_IDS_FORMAT,
                "题目ID列表转换为 JSON 失败：" + e.getMessage());
        }
    }
    
    /**
     * 验证 JSON 数组格式是否有效
     * 
     * @param json JSON 数组字符串
     * @return boolean 是否有效
     */
    public boolean isValidJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            jsonToList(json);
            return true;
        } catch (ExperimentBusinessException e) {
            return false;
        }
    }
    
    /**
     * 解析题目 ID 数组
     * 
     * @param json JSON 数组字符串
     * @return List<Long> 题目 ID 列表
     * @throws ExperimentBusinessException 如果 JSON 格式无效或为空
     */
    public List<Long> parseQuestionIds(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new ExperimentBusinessException(ResultCode.INVALID_QUESTION_IDS_FORMAT,
                "题目 ID 列表不能为空");
        }
        
        return jsonToList(json);
    }
}
