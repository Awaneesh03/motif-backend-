# Multi-stage Docker build for Render deployment
# Cache bust: v3

# ============================================
# Stage 1: Build Stage
# ============================================
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B && \
    mv target/idea-forge-backend.jar target/app.jar

# ============================================
# Stage 2: Runtime Stage
# ============================================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=build /build/target/app.jar /app/app.jar

RUN chown -R spring:spring /app

USER spring:spring

# Render injects PORT at runtime — do NOT hardcode it here
EXPOSE 8080

# exec replaces sh with java, making java PID 1 — required for Render port detection
ENTRYPOINT ["sh", "-c", "exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod \
  -Dserver.port=$PORT \
  -Dserver.address=0.0.0.0 \
  -jar app.jar"]
