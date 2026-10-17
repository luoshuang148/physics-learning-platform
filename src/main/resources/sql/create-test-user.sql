-- 创建zhixuewuli数据库专用测试账户脚本（MySQL 8.0优化版）
-- 专为MySQL 8.0+版本设计
-- 执行前请确保已登录MySQL root账户

-- 1. 删除可能存在的同名用户（幂等性保障）
DROP USER IF EXISTS 'zhixuewuli_test'@'localhost';

-- 2. 创建测试用户（MySQL 8.4+ 语法）
-- MySQL 8.4+ 已移除 mysql_native_password，使用默认的 caching_sha256_password
CREATE USER 'zhixuewuli_test'@'localhost' 
IDENTIFIED BY 'ShiJiaChun2005=!';

-- 3. 授予zhixuewuli数据库的所有权限
-- MySQL 8.0中GRANT语句不能同时创建用户
GRANT ALL PRIVILEGES ON zhixuewuli.* TO 'zhixuewuli_test'@'localhost';

-- 4. 刷新权限
FLUSH PRIVILEGES;

-- 5. 验证用户创建成功
SELECT 
    User, 
    Host, 
    authentication_string,
    plugin 
FROM mysql.user 
WHERE User = 'zhixuewuli_test';

-- 6. 验证权限授予情况
SHOW GRANTS FOR 'zhixuewuli_test'@'localhost';

-- 7. 显示MySQL版本信息
SELECT VERSION() as mysql_version;

-- 使用说明：
-- 1. 修改第12行的密码为你想要设置的密码
-- 2. 在MySQL命令行中执行：
--    SOURCE src/main/resources/sql/create-test-user-mysql8.sql;
-- 3. 或者退出MySQL后执行：
--    mysql -u root -p < src/main/resources/sql/create-test-user-mysql8.sql
--
-- 注意事项：
-- • MySQL 8.4+ 已移除 mysql_native_password 插件
-- • 使用 caching_sha256_password 认证（MySQL 8.4+ 默认）
-- • 用户只能访问 zhixuewuli 数据库，符合安全要求