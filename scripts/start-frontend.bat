@echo off
chcp 65001 >nul
cd /d "%~dp0..\frontend"
echo [前端] 工作目录: %cd%
echo [前端] 正在启动 Vite Dev Server...
npm run dev

