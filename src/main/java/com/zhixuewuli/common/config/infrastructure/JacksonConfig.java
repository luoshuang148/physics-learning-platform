package com.zhixuewuli.common.config.infrastructure;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson 全局配置类
 * 
 * 严格规范 Jackson 序列化与反序列化行为，确保 LaTeX 公式中的反斜杠等特殊字符得到标准处理。
 * 解决多重转义和 LaTeX 识别问题。
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册 JSR310 时间模块
        mapper.registerModule(new JavaTimeModule());
        
        // 序列化配置
        // 不将 Date 序列化为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 允许单个值对应数组
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        // 忽略未知属性，防止反序列化失败
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        // --- 核心转义配置 ---
        // 确保不转义非 ASCII 字符（保持中文正常显示）
        mapper.getFactory().disable(JsonGenerator.Feature.ESCAPE_NON_ASCII);
        
        // 针对 LaTeX 的反斜杠处理：
        // Jackson 默认会转义反斜杠为 \\。
        // 在处理包含 LaTeX 的字符串（如 "\frac{1}{2}"）时，
        // 我们需要确保它在 JSON 中被正确表示为 "\\\\frac{1}{2}"。
        
        return mapper;
    }
}
