@echo off
chcp 65001 >nul
echo ========================================
echo 智学物理后端 - 开发服务器
echo ========================================

:: 检查 Java 环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java 环境，请安装 JDK 21+
    pause
    exit /b 1
)

:: 检查 Maven 环境
mvn -v >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Maven，请安装 Maven 3.8+
    pause
    exit /b 1
)

:: 检查端口占用
netstat -ano | findstr :8080 >nul
if %errorlevel% equ 0 (
    echo [警告] 8080 端口被占用
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080') do (
        echo PID: %%a
        tasklist /FI "PID eq %%a"
    )
    echo.
    set /p choice=是否强制终止占用进程？(y/n): 
    if /i "%choice%"=="y" (
        for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080') do (
            taskkill /pid %%a /f >nul 2>&1
        )
        timeout /t 2 /nobreak >nul
        echo 端口已释放
    ) else (
        echo 请手动关闭占用端口的应用
        pause
        exit /b 1
    )
)

echo.
echo 启动信息:
echo - 应用地址：http://localhost:8080/api/v1
echo - 健康检查：http://localhost:8080/api/v1/actuator/health
echo - API文档：http://localhost:8080/swagger-ui.html
echo ========================================
echo.

:: 启动应用
mvn spring-boot:run -Dspring.profiles.active=dev
