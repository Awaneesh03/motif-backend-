# Multi-stage Docker build for Render deployment

# ============================================
# Stage 1: Build Stage
# ============================================
FROM maven:3.9-eclipse-temurin-17-alpine AS build

# JAVA_TOOL_OPTIONS is picked up by ALL JVM processes (including annotation processors)
ENV JAVA_TOOL_OPTIONS="\
--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED"

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ============================================
# Stage 2: Runtime Stage
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /build/target/idea-forge-backend.jar app.jar

RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod \
  -Dserver.port=${PORT:-8080} \
  -Dserver.address=0.0.0.0 \
  -jar app.jar"]
