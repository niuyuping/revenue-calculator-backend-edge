# 使用官方的 OpenJDK 21 镜像作为基础镜像
FROM openjdk:21-jdk-slim

# 设置工作目录
WORKDIR /app

# 复制 Gradle wrapper 文件
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 复制源代码
COPY src src

# 构建应用
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

# 暴露端口 8080 (Cloud Run 要求)
EXPOSE 8080

# Cloud Run 会设置 PORT 环境变量，应用需要监听该端口
# 移除 SERVER_PORT 设置，让应用使用 Cloud Run 提供的 PORT 环境变量

# 运行应用
CMD ["java", "-jar", "build/libs/revenue-calculator-backend-edge-0.0.1-SNAPSHOT.jar"]
