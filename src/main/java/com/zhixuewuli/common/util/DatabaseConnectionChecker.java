package com.zhixuewuli.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 数据库连接检查工具
 * 启动时验证数据库连接和字符集配置
 */
@Slf4j
@Component
public class DatabaseConnectionChecker implements CommandLineRunner {
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private Environment env;
    
    @Override
    public void run(String... args) throws Exception {
        if (env.acceptsProfiles("dev")) {
            checkDatabaseConnection();
        }
    }
    
    private void checkDatabaseConnection() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            log.info("=== 数据库连接信息 ===");
            log.info("数据库产品: {}", metaData.getDatabaseProductName());
            log.info("数据库版本: {}", metaData.getDatabaseProductVersion());
            log.info("驱动程序: {}", metaData.getDriverName());
            log.info("驱动版本: {}", metaData.getDriverVersion());
            
            // 检查字符集设置
            checkCharacterSet(conn);
            
            // 测试特殊字符插入
            testSpecialCharacters(conn);
            
            log.info("数据库连接检查完成 ✓");
            
        } catch (Exception e) {
            log.error("数据库连接检查失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据库连接检查失败", e);
        }
    }
    
    private void checkCharacterSet(Connection conn) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SHOW VARIABLES LIKE 'character_set_%'")) {
            ResultSet rs = stmt.executeQuery();
            log.info("=== 字符集配置 ===");
            while (rs.next()) {
                log.info("{}: {}", rs.getString(1), rs.getString(2));
            }
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SHOW VARIABLES LIKE 'collation_%'")) {
            ResultSet rs = stmt.executeQuery();
            log.info("=== 排序规则配置 ===");
            while (rs.next()) {
                log.info("{}: {}", rs.getString(1), rs.getString(2));
            }
        }
    }
    
    private void testSpecialCharacters(Connection conn) throws Exception {
        String testString = "测试特殊字符: 中文English123!@#$%^&*()_+-=[]{}|;':\",./<>?";
        
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT ? as test_string")) {
            stmt.setString(1, testString);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String result = rs.getString("test_string");
                if (testString.equals(result)) {
                    log.info("特殊字符测试通过 ✓");
                } else {
                    log.warn("特殊字符测试失败: 原始={} 结果={}", testString, result);
                }
            }
        }
    }
}
