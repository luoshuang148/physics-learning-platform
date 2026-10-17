package com.zhixuewuli.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库异常解析工具类
 * 
 * 用于解析 Spring DataAccessException 中的数据库约束违反异常，
 * 提取关键信息并转换为友好的业务异常。
 * 
 * <p>
 * 支持的数据库异常类型：
 * </p>
 * <ul>
 * <li>MySQL: Duplicate entry for key</li>
 * <li>MySQL: Cannot add or update a child row (外键约束)</li>
 * <li>MySQL: Column 'xxx' cannot be null</li>
 * <li>H2: Unique index or primary key violation</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Slf4j
public class DatabaseExceptionParserUtil {

    // MySQL重复条目正则表达式
    private static final Pattern MYSQL_DUPLICATE_ENTRY_PATTERN = Pattern
            .compile("Duplicate entry '(.+?)' for key '(?:.+?\\.)?(.+?)'", Pattern.DOTALL);

    // MySQL外键约束正则表达式
    private static final Pattern MYSQL_FOREIGN_KEY_PATTERN = Pattern
            .compile("Cannot add or update a child row: a foreign key constraint fails \\('(?:.+?\\.)?(.+?)'\\)");

    // MySQL非空约束正则表达式
    private static final Pattern MYSQL_NOT_NULL_PATTERN = Pattern.compile("Column '(?:.+?\\.)?(.+?)' cannot be null");

    // H2重复条目正则表达式
    private static final Pattern H2_DUPLICATE_ENTRY_PATTERN = Pattern
            .compile("Unique index or primary key violation: \"(.+?) ON .+?\\((.+?)\\) VALUES \\('(.+?)'\\)",
                    Pattern.DOTALL);

    /**
     * 解析DataIntegrityViolationException并提取关键信息
     * 
     * @param exception 原始数据库异常
     * @return 解析结果封装对象
     */
    public static DatabaseConstraintInfo parseException(DataIntegrityViolationException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            log.warn("数据库异常消息为空");
            return new DatabaseConstraintInfo("数据库约束违反", "UNKNOWN", "unknown", "UNKNOWN");
        }

        log.debug("解析数据库异常: {}", message);

        try {
            // 尝试解析MySQL重复条目异常
            DatabaseConstraintInfo parsedInfo = parseMySqlDuplicateEntry(message);
            if (parsedInfo != null) {
                return parsedInfo;
            }

            // 尝试解析MySQL外键约束异常
            parsedInfo = parseMySqlForeignKey(message);
            if (parsedInfo != null) {
                return parsedInfo;
            }

            // 尝试解析MySQL非空约束异常
            parsedInfo = parseMySqlNotNull(message);
            if (parsedInfo != null) {
                return parsedInfo;
            }

            // 尝试解析H2重复条目异常
            parsedInfo = parseH2DuplicateEntry(message);
            if (parsedInfo != null) {
                return parsedInfo;
            }

            // 尝试解析其他常见数据库异常模式
            parsedInfo = parseGenericDatabaseException(message);
            if (parsedInfo != null) {
                return parsedInfo;
            }

            // 无法解析的具体异常，返回通用约束信息
            log.warn("无法解析的数据库约束异常: {}", message);
            return new DatabaseConstraintInfo("数据约束违反，请检查输入内容", "UNKNOWN", "unknown", "UNKNOWN");

        } catch (Exception e) {
            log.error("解析数据库异常时发生错误: {}", e.getMessage(), e);
            // 发生解析错误时返回安全的默认信息
            return new DatabaseConstraintInfo("数据库操作失败，请稍后重试", "PARSE_ERROR", "unknown", "UNKNOWN");
        }
    }

    /**
     * 解析MySQL重复条目异常
     */
    private static DatabaseConstraintInfo parseMySqlDuplicateEntry(String message) {
        Matcher matcher = MYSQL_DUPLICATE_ENTRY_PATTERN.matcher(message);
        if (matcher.find()) {
            String duplicateValue = matcher.group(1);
            String keyName = matcher.group(2);

            log.debug("解析到MySQL重复条目: 值={}, 键={}", duplicateValue, keyName);

            // 处理特殊字符转义
            duplicateValue = unescapeSpecialCharacters(duplicateValue);

            // 根据键名确定字段名
            String fieldName = extractFieldNameFromKeyName(keyName);
            String constraintType = "UNIQUE";

            return new DatabaseConstraintInfo(
                    String.format("%s已存在：%s", getFieldDisplayName(fieldName), duplicateValue),
                    duplicateValue,
                    fieldName,
                    constraintType);
        }
        return null;
    }

    /**
     * 解析MySQL外键约束异常
     */
    private static DatabaseConstraintInfo parseMySqlForeignKey(String message) {
        Matcher matcher = MYSQL_FOREIGN_KEY_PATTERN.matcher(message);
        if (matcher.find()) {
            String tableName = matcher.group(1);
            log.debug("解析到MySQL外键约束违反: 表={}", tableName);

            return new DatabaseConstraintInfo(
                    "关联数据不存在或已被删除",
                    null,
                    extractFieldNameFromTableName(tableName),
                    "FOREIGN_KEY");
        }
        return null;
    }

    /**
     * 解析MySQL非空约束异常
     */
    private static DatabaseConstraintInfo parseMySqlNotNull(String message) {
        Matcher matcher = MYSQL_NOT_NULL_PATTERN.matcher(message);
        if (matcher.find()) {
            String fieldName = matcher.group(1);
            log.debug("解析到MySQL非空约束违反: 字段={}", fieldName);

            return new DatabaseConstraintInfo(
                    String.format("%s不能为空", getFieldDisplayName(fieldName)),
                    null,
                    fieldName,
                    "NOT_NULL");
        }
        return null;
    }

    /**
     * 解析H2重复条目异常
     */
    private static DatabaseConstraintInfo parseH2DuplicateEntry(String message) {
        Matcher matcher = H2_DUPLICATE_ENTRY_PATTERN.matcher(message);
        if (matcher.find()) {
            String constraintName = matcher.group(1);
            String rawFieldName = matcher.group(2);
            String duplicateValue = matcher.group(3);

            // H2数据库字段名通常是大写的，需要转换为小写进行匹配
            String fieldName = rawFieldName.toLowerCase();

            log.debug("解析到H2重复条目: 约束={}, 原始字段={}, 转换字段={}, 值={}",
                    constraintName, rawFieldName, fieldName, duplicateValue);

            return new DatabaseConstraintInfo(
                    String.format("%s已存在：%s", getFieldDisplayName(fieldName), duplicateValue),
                    duplicateValue,
                    fieldName,
                    "UNIQUE");
        }
        return null;
    }

    /**
     * 从键名中提取字段名
     */
    private static String extractFieldNameFromKeyName(String keyName) {
        // 处理常见的键名格式
        // 例如: users.real_name -> real_name
        // 例如: UK_REAL_NAME -> real_name

        // 移除表名前缀（如果有）
        if (keyName.contains(".")) {
            keyName = keyName.substring(keyName.lastIndexOf(".") + 1);
        }

        // 处理大写带下划线的格式
        if (keyName.toUpperCase().equals(keyName) && keyName.contains("_")) {
            // UK_REAL_NAME -> REAL_NAME -> real_name
            if (keyName.startsWith("UK_")) {
                keyName = keyName.substring(3);
            }
            return keyName.toLowerCase();
        }

        return keyName;
    }

    /**
     * 从表名中提取相关字段名
     */
    private static String extractFieldNameFromTableName(String tableName) {
        // 简单的表名到字段名映射
        switch (tableName.toLowerCase()) {
            case "users":
                return "user_id";
            case "questions":
                return "question_id";
            default:
                return tableName;
        }
    }

    /**
     * 处理特殊字符转义
     */
    private static String unescapeSpecialCharacters(String value) {
        if (value == null) {
            return null;
        }

        // 处理常见的转义字符
        return value
                .replace("\\'", "'") // 单引号转义
                .replace("\\\\", "\\"); // 双反斜杠转义
    }

    /**
     * 解析通用数据库异常
     * 处理一些常见的数据库异常模式
     */
    private static DatabaseConstraintInfo parseGenericDatabaseException(String message) {
        // 处理包含特定关键词的异常消息
        if (message.toLowerCase().contains("duplicate") && message.contains("entry")) {
            log.debug("检测到可能的重复条目异常");
            return null; // 让专门的解析方法处理
        }

        if (message.toLowerCase().contains("foreign key")) {
            log.debug("检测到可能的外键约束异常");
            return null; // 让专门的解析方法处理
        }

        if (message.toLowerCase().contains("cannot be null")) {
            log.debug("检测到可能的非空约束异常");
            return null; // 让专门的解析方法处理
        }

        return null;
    }

    /**
     * 获取字段显示名称
     */
    private static String getFieldDisplayName(String fieldName) {
        switch (fieldName.toLowerCase()) {
            case "real_name":
                return "姓名";
            case "username":
                return "用户名";
            case "content":
                return "题目内容";
            case "title":
                return "标题";
            case "url":
                return "视频播放链接";
            case "user_id":
                return "用户";
            case "question_id":
                return "题目";
            case "video_id":
                return "视频";
            default:
                return fieldName;
        }
    }

    /**
     * 根据解析结果抛出相应的业务异常
     * 供Service层使用的方法
     * 
     * @param exception 原始数据库异常
     * @throws com.zhixuewuli.common.exception.BusinessException 对应的业务异常
     */
    public static void throwAppropriateBusinessException(DataIntegrityViolationException exception) {
        DatabaseConstraintInfo info = parseException(exception);

        // 根据字段名抛出对应的业务异常
        switch (info.getFieldName().toLowerCase()) {
            case "username":
                throw new com.zhixuewuli.common.exception.auth.UserNameDuplicateException(
                        info.getDuplicateValue());
            case "real_name":
                throw new com.zhixuewuli.common.exception.auth.RealNameDuplicateException(
                        info.getDuplicateValue());
            case "content":
                throw new com.zhixuewuli.common.exception.question.QuestionContentDuplicateException(
                        info.getDuplicateValue());
            case "title":
                throw new com.zhixuewuli.common.exception.video.VideoAlreadyExistsException("标题",
                        info.getDuplicateValue());
            case "url":
                throw new com.zhixuewuli.common.exception.video.VideoAlreadyExistsException("播放链接",
                        info.getDuplicateValue());
            default:
                // 对于其他字段，抛出通用的业务异常
                throw new com.zhixuewuli.common.exception.BusinessException(
                        com.zhixuewuli.common.result.ResultCode.DATABASE_CONSTRAINT_VIOLATION,
                        info.getFriendlyMessage());
        }
    }

    /**
     * 数据库约束信息封装类
     */
    public static class DatabaseConstraintInfo {
        private final String friendlyMessage;
        private final String duplicateValue;
        private final String fieldName;
        private final String constraintType;

        public DatabaseConstraintInfo(String friendlyMessage, String duplicateValue, String fieldName,
                String constraintType) {
            this.friendlyMessage = friendlyMessage;
            this.duplicateValue = duplicateValue;
            this.fieldName = fieldName;
            this.constraintType = constraintType;
        }

        public String getFriendlyMessage() {
            return friendlyMessage;
        }

        public String getDuplicateValue() {
            return duplicateValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getConstraintType() {
            return constraintType;
        }
    }
}