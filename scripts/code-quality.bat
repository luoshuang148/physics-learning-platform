@echo off
chcp 65001 >nul
echo ========================================
echo 智学物理后端 - 代码质量检查
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

echo.
echo 请选择要执行的质量检查:
echo 1. 编译检查 (快速验证)
echo 2. SpotBugs 静态分析
echo 3. 完整检查 (编译+SpotBugs)
echo 4. 退出
echo.

set /p choice=请输入选项 (1-4): 

if "%choice%"=="1" (
    echo 执行编译检查...
    mvn compile test-compile -q
    
) else if "%choice%"=="2" (
    echo 执行 SpotBugs 静态分析...
    mvn spotbugs:check -q
    
) else if "%choice%"=="3" (
    echo 执行完整质量检查...
    echo [1/2] 编译检查...
    mvn compile test-compile -q
    if %errorlevel% equ 0 (
        echo [2/2] SpotBugs 分析...
        mvn spotbugs:check -q
    )
    
) else if "%choice%"=="4" (
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
    echo 检查结果：通过
) else (
    echo 检查结果：发现问题
)
echo ========================================

pause
