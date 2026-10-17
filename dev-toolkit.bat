@echo off
chcp 65001 >nul
echo ========================================
echo 智学物理后端 - 开发工具箱
echo ========================================
echo 时间：%date% %time%
echo 目录：%cd%
echo ========================================

:menu
echo.
echo 请选择要执行的操作:
echo 1. 启动开发服务器
echo 2. 运行测试
echo 3. 快速测试 (并行模式)
echo 4. 代码质量检查
echo 5. 外部 API 测试
echo 6. AI 服务测试
echo 7. 查看项目信息
echo 8. 退出
echo.

set /p choice=请输入选项 (1-8): 

if "%choice%"=="1" (
    echo 启动开发服务器...
    call scripts\start-dev.bat
    
) else if "%choice%"=="2" (
    echo 运行测试...
    call scripts\run-tests.bat
    
) else if "%choice%"=="3" (
    echo 快速测试...
    call scripts\fast-test.bat
    
) else if "%choice%"=="4" (
    echo 代码质量检查...
    call scripts\code-quality.bat
    
) else if "%choice%"=="5" (
    echo 外部 API 测试...
    call scripts\run-external-api-tests.bat
    
) else if "%choice%"=="6" (
    echo AI 服务测试...
    powershell -ExecutionPolicy Bypass -File scripts\test-ai-simple.ps1
    
) else if "%choice%"=="7" (
    echo.
    echo 项目名称：智学物理后端
    echo 项目版本：1.0.0
    echo 环境配置：dev(统一)
    echo.
    echo 技术栈:
    echo   - Spring Boot 3.3.8
    echo   - Java 21
    echo   - MySQL 8.0+
    echo   - Maven 3.8+
    echo.
    echo 端点信息:
    echo   - 应用接口：http://localhost:8080/api/v1
    echo   - 健康检查：http://localhost:8080/api/v1/actuator/health
    echo   - API文档：http://localhost:8080/swagger-ui.html
    echo   - 指标监控：http://localhost:8080/api/v1/actuator/metrics
    echo.
    pause
    
) else if "%choice%"=="8" (
    echo 感谢使用，再见！
    exit /b 0
    
) else (
    echo [错误] 无效选项，请重新选择
)

goto menu
