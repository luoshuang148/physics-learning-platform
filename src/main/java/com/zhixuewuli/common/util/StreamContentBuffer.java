package com.zhixuewuli.common.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 流式内容缓冲区处理器
 * 
 * <p>
 * 解决流式传输过程中命令/标记被随机切割的问题：
 * </p>
 * <ul>
 * <li>LaTeX 命令可能被切断（如 \cd 与 ott 分开）</li>
 * <li>Markdown 标记可能被切断</li>
 * </ul>
 *
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see StreamContentFixer 格式修复工具
 */
@Slf4j
public class StreamContentBuffer {

    /** 内部缓冲区 */
    private final StringBuilder buffer = new StringBuilder();

    /** 最大缓冲字符数 */
    private final int maxBufferSize;

    /**
     * 创建缓冲区处理器
     * 
     * @param maxBufferSize 最大缓冲字符数，建议 30-80 字符
     */
    public StreamContentBuffer(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    /**
     * 使用默认缓冲区大小（50字符）创建处理器
     */
    public StreamContentBuffer() {
        this(50);
    }

    /**
     * 处理新到达的数据块
     * 
     * @param newChunk 新到达的原始文本
     * @return 修复后可直接发送的文本（可能为空字符串）
     */
    public String process(String newChunk) {
        if (newChunk == null || newChunk.isEmpty()) {
            return "";
        }

        buffer.append(newChunk);
        String combined = buffer.toString();

        // 若缓冲区未达到阈值，通常暂不输出；但对接近阈值的连续文本可提前输出，避免长期堆积
        if (combined.length() < maxBufferSize && !shouldForceOutputForContinuousText(combined)) {
            return "";
        }

        // 寻找安全切割点
        int safeIndex = findSafeSplitIndex(combined);

        // 如果找不到安全切割点，避免在 LaTeX 命令中间或转义序列中间强切
        if (safeIndex <= 0) {
            int boundary = Math.min(combined.length(), maxBufferSize);
            if (isInsideLatexCommand(combined, boundary) || wouldSplitEscapedSequence(combined, boundary)) {
                return "";
            }
            // 普通连续文本可强制切割，避免无限缓冲
            safeIndex = boundary;
        }

        String toSend = combined.substring(0, safeIndex);
        String remaining = combined.substring(safeIndex);

        // 更新缓冲区为剩余部分
        buffer.setLength(0);
        buffer.append(remaining);

        // 修复并返回
        String fixed = StreamContentFixer.fix(toSend);

        if (log.isDebugEnabled() && !toSend.equals(fixed)) {
            log.debug("StreamContentBuffer fixed content");
        }

        return fixed;
    }

    /**
     * 流结束时调用，强制输出缓冲区剩余内容
     * 
     * @return 修复后的最终内容
     */
    public String flush() {
        if (buffer.length() == 0) {
            return "";
        }

        String finalContent = StreamContentFixer.fix(buffer.toString());
        buffer.setLength(0);

        log.debug("StreamContentBuffer flushed final content, length: {}", finalContent.length());

        return finalContent;
    }

    /**
     * 获取当前缓冲区大小
     * 
     * @return 缓冲区中的字符数
     */
    public int getBufferSize() {
        return buffer.length();
    }

    /**
     * 判断缓冲区是否为空
     * 
     * @return true 表示缓冲区为空
     */
    public boolean isEmpty() {
        return buffer.length() == 0;
    }

    /**
     * 寻找一个安全的切割位置
     * 
     * <p>
     * 优先在空白字符和标点符号处切割，避免切断 LaTeX 命令。
     * </p>
     * 
     * @param text 待切割的文本
     * @return 安全切割位置（索引），返回 0 表示未找到
     */
    private int findSafeSplitIndex(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // 我们在 [0, maxBufferSize] 范围内寻找切割点
        int end = Math.min(text.length(), maxBufferSize);

        for (int i = end - 1; i >= 0; i--) {
            char c = text.charAt(i);

            // 空白字符是最佳切割点
            if (Character.isWhitespace(c)) {
                return i + 1;
            }

            // LaTeX 这里的处理要非常小心，避免切割 \command
            if (c == '\\') {
                // 反斜杠前后通常构成转义或 LaTeX 边界（如 \(、\)），不在这里切割。
                continue;
            }

            // 标点符号（排除反斜杠）
            if (c == '.' || c == ',' || c == ';' || c == ':' ||
                c == '!' || c == '?' || c == '-' || c == '=' ||
                c == '}' || c == '{' || c == ')' || c == ']' ||
                c == '，' || c == '。' || c == '；' || c == '：' ||
                c == '！' || c == '？') {

                // 排除命令内部的符号或被转义的符号
                boolean inLatexCommand = false;
                for (int j = i - 1; j >= 0; j--) {
                    if (text.charAt(j) == '\\') {
                        // 简单的启发式：如果前面有反斜杠且中间全是字母，可能在命令中
                        String sub = text.substring(j + 1, i);
                        if (sub.chars().allMatch(Character::isLetter)) {
                            inLatexCommand = true;
                        }
                        break;
                    }
                    if (Character.isWhitespace(text.charAt(j))) break;
                }

                if (!inLatexCommand) {
                    return i + 1;
                }
            }
        }

        return 0;
    }

    /**
     * 连续文本（无自然切割点）接近阈值时可提前输出，避免无限等待。
     */
    private boolean shouldForceOutputForContinuousText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int forceThreshold = Math.max(8, (int) Math.ceil(maxBufferSize * 0.8));
        if (text.length() < forceThreshold) {
            return false;
        }
        if (hasIncompleteLatexSuffix(text)) {
            return false;
        }
        return findSafeSplitIndex(text) <= 0;
    }

    /**
     * 判断某个边界是否落在 LaTeX 命令内部（例如 \alph|a）
     */
    private boolean isInsideLatexCommand(String text, int boundary) {
        if (boundary <= 0 || boundary >= text.length()) {
            return false;
        }
        int slashIndex = text.lastIndexOf('\\', boundary - 1);
        if (slashIndex < 0) {
            return false;
        }
        // 如果反斜杠与边界之间存在空白/常见分隔符，则不是同一命令
        for (int i = slashIndex + 1; i < boundary; i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        // 边界后仍是命令字符，说明命令被切在中间
        return Character.isLetterOrDigit(text.charAt(boundary));
    }

    /**
     * 判断文本尾部是否可能是尚未接收完整的 LaTeX 命令。
     */
    private boolean hasIncompleteLatexSuffix(String text) {
        int slashIndex = text.lastIndexOf('\\');
        if (slashIndex < 0) {
            return false;
        }
        if (slashIndex == text.length() - 1) {
            return true;
        }
        for (int i = slashIndex + 1; i < text.length(); i++) {
            if (!Character.isLetter(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断给定边界是否会把反斜杠转义序列切开（如 "\\|(" 或 "\\|)"）。
     */
    private boolean wouldSplitEscapedSequence(String text, int boundary) {
        if (text == null || boundary <= 0 || boundary >= text.length()) {
            return false;
        }
        if (text.charAt(boundary - 1) != '\\') {
            return false;
        }

        char next = text.charAt(boundary);
        return next == '(' || next == ')' || next == '[' || next == ']' ||
                next == '{' || next == '}' || next == '$' || next == '\\';
    }
}
