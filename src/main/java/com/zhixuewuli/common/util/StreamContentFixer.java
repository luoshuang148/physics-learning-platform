package com.zhixuewuli.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 流式内容格式修复器（LaTeX + Markdown 统一处理）
 * 
 * <p>
 * 解决大模型输出中常见的格式问题：
 * </p>
 * <ul>
 * <li>LaTeX 命令粘连：如 \cdott → \cdot t</li>
 * <li>Markdown 标题粘连：如 ###标题 → ### 标题</li>
 * <li>Markdown 列表粘连：如 -列表 → - 列表</li>
 * </ul>
 * 
 * <p>
 * 核心特点：
 * </p>
 * <ul>
 * <li>零外部依赖，纯 JDK 正则实现</li>
 * <li>预编译正则规则，性能高效</li>
 * <li>使用前瞻断言确保只修复必要位置</li>
 * </ul>
 *
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Slf4j
public final class StreamContentFixer {

    /** 预编译的修复规则列表 */
    private static final List<PatternReplacement> RULES = new ArrayList<>();

    static {
        // ==================== LaTeX 规则 ====================
        // 1. 希腊字母 (必须在普通字母前处理)
        addRule("\\\\(alpha|beta|gamma|delta|theta|lambda|mu|pi|sigma|omega)\\s*(?=[a-zA-Z0-9])", "\\\\$1 ");
        
        // 2. 常用数学函数
        addRule("\\\\(sin|cos|tan|log|ln|exp)\\s*(?=[a-zA-Z])", "\\\\$1 ");
        
        // 3. 二元运算符和关系符 (修正：不应该在这里随意处理 + 和 =)
        addRule("\\\\(cdot|times|div|pm|mp|leq|geq|neq|approx|equiv|Rightarrow|rightarrow|leftarrow|Leftrightarrow)\\s*(?=[a-zA-Z0-9])", "\\\\$1 ");
        
        // 3a. 处理 LaTeX 命令与 +/= 粘连：\alpha+\beta -> \alpha + \beta
        addRule("(\\\\[a-zA-Z]+)([+=])(?=\\\\?[a-zA-Z0-9])", "$1 $2 ");
        
        // 4. 修饰符 (hat, bar, vec)
        addRule("\\\\(hat|bar|vec)\\s*(?=[a-zA-Z0-9])", "\\\\$1 ");
        // 4a. dot 需谨慎：避免把 \dots 误改为 \dot s
        addRule("\\\\dot(?!s)\\s*(?=[a-zA-Z0-9])", "\\\\dot ");

        // ==================== Markdown 规则 ====================
        // 0. 【关键修复】标题后紧跟有序列表/文字：###1.标题 / ###标题 -> ### 标题
        // 优先级最高，确保标题标记后面始终有空格（避免 ###1. 被误解析）
        addRule("(#{1,6})(?=[0-9a-zA-Z\\u4e00-\\u9fa5])", "$1 ");
        
        // 1. 标题标记粘连：###标题 -> ### 标题（兜底规则，兼容各种边缘情况）
        addRule("(#{1,6})(?=[^\\s#])", "$1 ");
        
        // 2. 无序列表标记粘连：-列表 / +列表 -> 增加空格
        // 仅匹配真正列表，避免误伤表格分隔线(---、-|- 等)和分片边界中的连续短横线
        addRule("(?m)^([-+])(?![-+|])(?=[^\\s])", "$1 ");
        // 2a. 注意：星号列表规则在“按分片修复”场景下容易误伤强调语法（如分片为 *“）
        // 因此后端不再对 *列表 做自动补空格，避免把 **“... ”** 误改为 ** “... ” **。
        // 前端基于完整文本渲染时仍可兜底处理列表粘连。
                
        // 3. 有序列表粘连：1.文字 → 1. 文字
        addRule("(\\d+\\.)(?=[^\\s\\.])", "$1 ");
        
        // 4. 粗体/斜体闭合后粘连修复 (核心修复)
        // 粗体：**text**next -> **text** next
        addRule("(\\*\\*(?=[^*\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5][^*\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5])[^*\\s](?:[^*]*[^*\\s])?\\*\\*)(?=[a-zA-Z0-9\\u4e00-\\u9fa5])", "$1 ");
        addRule("(__(?=[^_\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5][^_\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5])[^_\\s](?:[^_]*[^_\\s])?__)(?=[a-zA-Z0-9\\u4e00-\\u9fa5])", "$1 ");

        // 斜体：*text*next -> *text* next（严格排除 ** 粗体）
        addRule("(?<!\\*)\\*(?=[^*\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5][^*\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5])([^*\\s](?:[^*]*[^*\\s])?)\\*(?=[a-zA-Z0-9\\u4e00-\\u9fa5])", "*$1* ");
        addRule("(?<!_)_(?=[^_\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5][^_\\s]*[a-zA-Z0-9\\u4e00-\\u9fa5])([^_\\s](?:[^_]*[^_\\s])?)_(?=[a-zA-Z0-9\\u4e00-\\u9fa5])", "_$1_ ");
        
        // 5. 行内代码闭合后粘连：`code`文字 → `code` 文字
        addRule("(`[^`]+`)(?=[^\\s`])", "$1 ");
        
        // 7. 代码块语言标识符粘连：```java代码 → ```java\n代码
        addRule("```(\\w+)(?=[^\\s`\\n])", "```$1\n");
    }

    /**
     * 添加修复规则
     */
    private static void addRule(String regex, String replacement) {
        RULES.add(new PatternReplacement(Pattern.compile(regex), replacement));
    }

    /**
     * 对文本块进行格式修复
     * 
     * @param chunk 原始文本块
     * @return 修复后的文本块
     */
    public static String fix(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return chunk;
        }
        
        String result = chunk;
        for (PatternReplacement rule : RULES) {
            String before = result;
            result = rule.pattern.matcher(result).replaceAll(rule.replacement);
            
            // 仅在 DEBUG 级别记录实际发生修复的情况
            if (log.isDebugEnabled() && !before.equals(result)) {
                log.debug("StreamContentFixer applied rule '{}': before=[{}], after=[{}]", 
                    rule.pattern.pattern(), before, result);
            }
        }
        
        return result;
    }

    /**
     * 内部类：存储预编译的正则规则
     */
    private static class PatternReplacement {
        final Pattern pattern;
        final String replacement;

        PatternReplacement(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
}
