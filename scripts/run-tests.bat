@echo off
chcp 65001 >nul
echo ========================================
echo 智学物理后端 - 测试执行
echo ========================================

:: 检查环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java 环境
    pause
    exit /b 1
)

mvn -v >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Maven 环境
    pause
    exit /b 1
)

:: 清理端口占用
netstat -ano | findstr :8080 >nul
if %errorlevel% equ 0 (
    echo 清理 8080 端口占用...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080') do (
        taskkill /pid %%a /f >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
)

:: 设置 JVM 参数
set MAVEN_OPTS=-Xmx4G -XX:+UseParallelGC

echo.
echo 请选择要执行的测试类型:
echo 1. 单元测试 (unit 标签)
echo 2. 集成测试 (integration 标签)
echo 3. 完整测试 (所有测试)
echo 4. 自定义测试类
echo 5. 退出
echo.

set /p choice=请输入选项 (1-5): 

if "%choice%"=="1" (
    echo 执行单元测试...
    mvn test -Dgroups="unit,ai-sync,ai-async" -Dspring.profiles.active=test
    
) else if "%choice%"=="2" (
    echo 执行集成测试...
    mvn test -Dgroups="integration-controller,integration-repository" -Dspring.profiles.active=test
    
) else if "%choice%"=="3" (
    echo 执行完整测试...
    mvn clean test -Dspring.profiles.active=test
    
) else if "%choice%"=="4" (
    set /p testClass=请输入测试类全名： 
    echo 执行自定义测试：%testClass%
    mvn test -Dtest="%testClass%" -Dspring.profiles.active=test
    
) else if "%choice%"=="5" (
    echo 已退出
    exit /b 0
    
) else (
    echo [错误] 无效选项
    pause
    exit /b 1
)

echo.
echo ========================================
if %errorlevel% equ 0 (
    echo 测试结果：通过
) else (
    echo 测试结果：失败
)
echo ========================================

pause
