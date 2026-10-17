package com.zhixuewuli.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 数据库用户检查工具
 * 用于验证数据库中实际存储的用户信息
 */
@Component
@Slf4j
public class DatabaseUserChecker implements CommandLineRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== 开始数据库用户信息检查 ===");

        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id, username, password_hash, user_type, real_name, active FROM users ORDER BY id";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            log.info("开始查询数据库用户信息");
            while (rs.next()) {
                Long id = rs.getLong("id");
                String username = rs.getString("username");
                String passwordHash = rs.getString("password_hash");
                String userType = rs.getString("user_type");
                String realName = rs.getString("real_name");
                Boolean active = rs.getBoolean("active");

                log.info("用户信息 - ID: {}, Username: {}, Type: {}, RealName: {}, Active: {}",
                        id, username, userType, realName, active);
                log.debug("用户密码哈希: {}", passwordHash);
            }
        } catch (Exception e) {
            log.error("检查数据库用户时发生异常", e);
        }
    }
}