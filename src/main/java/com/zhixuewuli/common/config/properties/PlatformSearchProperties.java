package com.zhixuewuli.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台题库检索配置。mode 由服务端策略决定，公共请求不允许改变权限。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.platform-search")
public class PlatformSearchProperties {
    /** 向量故障时是否允许显式降级为纯关键词（actualMode=lexical_only） */
    private boolean allowLexicalFallback = false;
    /** 每路召回条数 */
    private int perSourceLimit = 10;
    /** RRF 常数 */
    private int rrfK = 60;
    /** 最多来源单元 */
    private int maxSources = 5;
    /** 最多题目 */
    private int maxQuestions = 5;
    /** FULLTEXT 不可用时用 LIKE 适配器（actualMode=lexical_only，禁止声称全文检索） */
    private boolean preferFullText = true;
}
