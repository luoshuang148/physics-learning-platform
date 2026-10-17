# =====================================================
# 第一阶段：前端构建
# =====================================================
FROM node:22-alpine AS frontend-builder

WORKDIR /build/frontend/frontend

ARG VITE_APP_BASE=/api/v1/
ARG VITE_API_BASE_URL=
ENV VITE_APP_BASE=${VITE_APP_BASE}
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
# 为前端 ?raw 导入指南文件提供构建时路径：/build/frontend/docs
COPY docs ../docs

# 构建前端
RUN npm run build

# =====================================================
# 第二阶段：后端构建
# =====================================================
FROM maven:3.9.9-eclipse-temurin-21 AS backend-builder

WORKDIR /build/backend

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
# 将前端构建产物打包进 Spring Boot jar 的静态目录
RUN rm -rf src/main/resources/static && mkdir -p src/main/resources/static
COPY --from=frontend-builder /build/frontend/frontend/dist/ ./src/main/resources/static/

RUN mvn clean package -DskipTests -B


# =====================================================
# 运行阶段
# =====================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装 curl 用于健康检查
RUN apk add --no-cache curl

# 复制后端 jar 包
COPY --from=backend-builder /build/backend/target/*.jar app.jar

# 创建日志目录
RUN mkdir -p logs /data/videos

# 设置 JVM 参数
ENV JAVA_OPTS="-Xms256m -Xmx448m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs"

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/v1/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
