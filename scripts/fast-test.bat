@echo off
chcp 65001 >nul
echo ========================================
echo 智学物理后端 - 快速测试
echo (针对多核 CPU 优化)
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

:: 系统信息
echo.
echo 系统信息:
java -version 2>&1 | findstr /i "version"
echo CPU 核心数：%NUMBER_OF_PROCESSORS%

:: 设置并行参数
set MAVEN_OPTS=-Xmx6G -XX:+UseParallelGC
set PARALLEL_FORK=0.67C
set PARALLEL_THREAD=2

echo 并行配置：Fork=%PARALLEL_FORK%, 线程=%PARALLEL_THREAD%
echo.

:: 清理端口
netstat -ano | findstr :8080 >nul
if %errorlevel% equ 0 (
    echo 清理 8080 端口...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080') do (
        taskkill /pid %%a /f >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
)

:: 编译项目
echo [1/2] 编译项目...
call mvn clean compile test-compile -DskipTests -T 1C
if %errorlevel% neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)
echo 编译完成
echo.

:: 执行测试
echo [2/2] 执行测试 (并行模式)...
call mvn surefire:test ^
    -Dspring.profiles.active=test ^
    -Dsurefire.forkCount=%PARALLEL_FORK% ^
    -Dsurefire.threadCount=%PARALLEL_THREAD% ^
    -Dsurefire.parallel=classes ^
    -Dfile.encoding=UTF-8

echo.
echo ========================================
if %errorlevel% equ 0 (
    echo 测试执行：通过
) else (
    echo 测试执行：失败
)
echo ========================================

pause
