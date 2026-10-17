@echo off
chcp 65001 >nul
cd /d "%~dp0.."
echo [后端] 工作目录: %cd%
echo [后端] 正在启动 Spring Boot...
mvn spring-boot:run -Dspring-boot.run.profiles=dev

