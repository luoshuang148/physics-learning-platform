@echo off
chcp 65001 >nul
setlocal

title 智学物理微课 - 停止服务
cls

echo.
echo  ╔══════════════════════════════════════════╗
echo  ║       智学物理微课  停止服务              ║
echo  ╚══════════════════════════════════════════╝
echo.

set STOPPED=0

:: 终止占用 8080 端口的进程（后端）
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    echo  [->] 停止后端进程 PID %%p
    taskkill /pid %%p /f >nul 2>&1
    set STOPPED=1
)

:: 终止占用 5173 端口的进程（前端）
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":5173 " ^| findstr "LISTENING"') do (
    echo  [->] 停止前端进程 PID %%p
    taskkill /pid %%p /f >nul 2>&1
    set STOPPED=1
)

if %STOPPED%==0 (
    echo  [OK] 服务未在运行，无需停止
) else (
    echo  [OK] 所有服务已停止
)

echo.
pause
endlocal

