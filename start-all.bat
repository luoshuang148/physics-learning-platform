@echo off
chcp 65001 >nul
echo ========================================
echo 智学物理 - 一键启动前后端
echo ========================================
echo.

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

:: 检查 Node.js 环境
node -v >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Node.js，请安装 Node.js
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
echo [1/2] 正在启动后端服务...
echo.

:: 启动后端（在新窗口中）
start "智学物理-后端" cmd /k "cd /d "%~dp0" && mvn spring-boot:run -Dspring.profiles.active=dev"

:: 等待后端启动
echo 等待后端服务启动...
timeout /t 15 /nobreak >nul

echo.
echo [2/2] 正在启动前端服务...
echo.

:: 启动前端（在新窗口中）
start "智学物理-前端" cmd /k "cd /d "%~dp0frontend" && npm run dev"

:: 等待前端启动
echo 等待前端服务启动...
timeout /t 5 /nobreak >nul

echo.
echo ========================================
echo 启动完成！
echo ========================================
echo.
echo 后端服务:
echo   - API 地址: http://localhost:8080/api/v1
echo   - API 文档: http://localhost:8080/swagger-ui.html
echo.
echo 前端服务:
echo   - 访问地址: http://localhost:5173
echo.
echo 提示:
echo   - 后端和前端已在独立窗口中运行
echo   - 关闭窗口即可停止对应服务
echo   - 首次启动可能需要较长时间
echo ========================================
echo.
pause
