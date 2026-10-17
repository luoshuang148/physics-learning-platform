package com.zhixuewuli.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 会话工具类
 * 
 * 提供会话管理相关的实用方法，主要用于生成、验证和清理会话标识符。
 * 确保会话ID的安全性和有效性，防范常见的安全威胁。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>生成安全的会话标识符</li>
 * <li>验证会话ID格式合法性</li>
 * <li>清理会话ID防止注入攻击</li>
 * <li>提供会话ID标准化处理</li>
 * </ul>
 * 
 * <p>
 * 安全考虑：
 * </p>
 * <ul>
 * <li>使用UUID保证全局唯一性</li>
 * <li>移除连字符减少长度</li>
 * <li>严格的格式验证规则</li>
 * <li>危险字符过滤机制</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see UUID Java标准UUID类
 */
public class SessionUtil {

    /**
     * 生成安全的会话ID
     * 
     * 使用时间戳+UUID混合格式生成会话标识符，
     * 格式为：yyyyMMddHHmmss_xxxxxxxxxxxxxxxx（14位时间戳+下划线+16位UUID）
     * 便于按时间维度进行会话管理和分析。
     * 
     * @return String 安全的会话ID字符串，格式示例：20261222143025_a1b2c3d4e5f67890
     * @throws RuntimeException 当UUID生成失败时抛出
     * 
     * @implSpec 使用当前时间戳确保时间有序性
     * @implSpec UUID部分保证全局唯一性
     * @implSpec 下划线分隔便于解析和处理
     */
    public static String generateSecureSessionId() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuidPart = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return timestamp + "_" + uuidPart;
    }

    /**
     * 从会话ID中提取时间戳
     * 
     * 解析时间戳+UUID格式的会话ID，提取其中的时间戳部分
     * 并转换为LocalDateTime对象。
     * 
     * @param sessionId 会话ID字符串（格式：yyyyMMddHHmmss_16位UUID）
     * @return LocalDateTime 提取的时间戳，如果解析失败返回LocalDateTime.MIN
     */
    public static LocalDateTime extractTimestamp(String sessionId) {
        if (sessionId == null || sessionId.length() < 14) {
            return LocalDateTime.MIN;
        }

        try {
            // 必须是新格式：包含下划线且下划线位置为14
            if (sessionId.contains("_") && sessionId.indexOf('_') == 14) {
                String timestampStr = sessionId.substring(0, 14);
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            }
            // 不符合新格式，返回最小值
            return LocalDateTime.MIN;
        } catch (Exception e) {
            return LocalDateTime.MIN;
        }
    }

    /**
     * 获取会话ID的时间部分
     * 
     * 从会话ID中提取时间戳并格式化为易读的时间字符串。
     * 
     * @param sessionId 会话ID字符串
     * @return String 格式化的时间字符串，格式：yyyy-MM-dd HH:mm:ss
     */
    public static String getSessionTimeDisplay(String sessionId) {
        LocalDateTime timestamp = extractTimestamp(sessionId);
        if (timestamp == LocalDateTime.MIN) {
            return "未知时间";
        }
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 验证会话ID格式
     * 
     * 检查会话ID是否符合新的时间戳+UUID混合格式规范，
     * 同时保持向后兼容旧的纯UUID格式。
     * 
     * @param sessionId 待验证的会话ID
     * @return boolean 会话ID是否有效
     * 
     * @implSpec 新格式：14位时间戳+下划线+16位UUID，总长度31位
     * @implSpec 旧格式：32位纯UUID（向后兼容）
     * @implSpec 空值和空字符串返回false
     */
    public static boolean isValidSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }

        // 验证新格式：时间戳_UUID
        if (sessionId.contains("_") && sessionId.length() == 31) {
            String[] parts = sessionId.split("_");
            if (parts.length == 2 && parts[0].length() == 14 && parts[1].length() == 16) {
                return parts[0].matches("^\\d{14}$") && parts[1].matches("^[a-zA-Z0-9]{16}$");
            }
        }

        // 向后兼容旧格式：纯UUID
        return sessionId.matches("^[a-zA-Z0-9]{32}$");
    }

    /**
     * 清理会话ID，防止注入攻击
     * 
     * 过滤掉可能导致安全问题的危险字符，
     * 确保会话ID可以在各种上下文中安全使用。
     * 
     * @param sessionId 原始会话ID
     * @return String 清理后的会话ID，null输入返回null
     * 
     * @implSpec 移除HTML标签字符：< >
     * @implSpec 移除引号字符：" '
     * @implSpec 移除反斜杠：\
     * @implSpec 保留字母数字和安全符号（包括下划线）
     */
    public static String sanitizeSessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        // 移除所有非字母数字和非下划线字符
        // 允许字母、数字、下划线，确保sessionId格式中的下划线得以保留
        return sessionId.replaceAll("[^a-zA-Z0-9_]", "");
    }
}
