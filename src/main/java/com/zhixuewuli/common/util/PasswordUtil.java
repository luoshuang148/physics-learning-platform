package com.zhixuewuli.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 密码加密工具类 - 专注密码安全处理
 * 增强字符编码处理和安全验证
 */
@Component
public class PasswordUtil {
    
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    
    // 密码强度正则表达式
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$"
    );
    
    /**
     * 加密密码 - 增强字符处理
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        // 标准化输入字符编码
        String normalizedPassword = normalizeInput(rawPassword);
        return encoder.encode(normalizedPassword);
    }
    
    /**
     * 验证密码 - 增强字符处理和安全检查
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        
        try {
            // 标准化输入字符编码
            String normalizedPassword = normalizeInput(rawPassword);
            return encoder.matches(normalizedPassword, encodedPassword);
        } catch (Exception e) {
            // 记录异常但不暴露具体错误信息
            return false;
        }
    }
    
    /**
     * 验证密码强度
     */
    public boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }
    
    /**
     * 标准化输入 - 处理字符编码问题
     */
    private String normalizeInput(String input) {
        // 转换为标准UTF-8编码
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    
    /**
     * 安全比较两个字符串（防止时序攻击）
     */
    public boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
