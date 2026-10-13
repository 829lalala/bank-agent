# ===== 构建阶段：Maven 编译打包 =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先复制 pom.xml 单独解析依赖：源码不变时依赖层走缓存，构建提速
# 注入阿里云镜像配置：容器内默认直连 Maven 中央仓库，国内网络会超时
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# 再复制源码打包（跳过测试：测试在 CI 阶段执行）
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ===== 运行阶段：仅含 JRE 与应用，镜像更小更安全 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# 时区设置为东八区，保证消息时间戳与日志时间正确
ENV TZ=Asia/Shanghai

# 知识库文档进镜像（PathGuard 校验的是相对工作目录的 docs/knowledge）
COPY --from=build /app/target/bank-agent-*.jar app.jar
COPY docs/knowledge ./docs/knowledge

# 数据持久化在 PostgreSQL（docker-compose 提供 postgres 服务 + pg-data 卷），无需本地落盘卷

EXPOSE 8080

# 容器内不写死密钥：运行时通过 -e ZHIPUAGENT_API_KEY 或 compose environment 注入
ENTRYPOINT ["java", "-jar", "app.jar"]
