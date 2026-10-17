@echo off
chcp 65001 >nul
echo ========================================
echo 外部 API 验证测试
echo (使用真实 API 密钥)
echo ========================================

:: 设置项目目录
set PROJECT_DIR=%~dp0..
cd /d "%PROJECT_DIR%"

echo 工作目录：%cd%
echo.

:: 检查 Maven
mvn -v >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Maven
    pause
    exit /b 1
)

echo 请选择要执行的测试:
echo 1. 运行所有外部 API 测试
echo 2. 仅阿里云 AI 测试
echo 3. 仅智谱 AI 测试
echo 4. 自定义测试类
echo 5. 退出
echo.

set /p choice=请输入选项 (1-5): 

if "%choice%"=="1" (
    echo 运行所有外部 API 测试...
    call mvn clean test -Pexternal-api-test
    
) else if "%choice%"=="2" (
    echo 运行阿里云 AI 测试...
    call mvn clean test -Pexternal-api-test -Dtest="AliyunAIExternalApiTest"
    
) else if "%choice%"=="3" (
    echo 运行智谱 AI 测试...
    call mvn clean test -Pexternal-api-test -Dtest="ZhipuAIExternalApiTest"
    
) else if "%choice%"=="4" (
    set /p testClass=请输入测试类名： 
    echo 运行测试：%testClass%
    call mvn clean test -Pexternal-api-test -Dtest="%testClass%"
    
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
    echo 测试执行：通过
) else (
    echo 测试执行：失败
)
echo ========================================

pause
