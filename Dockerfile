# Multi-stage Docker build for Render deployment

# ============================================
# Stage 1: Build Stage
# ============================================
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /build

# Copy Maven config with JVM args for Lombok compatibility
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ============================================
# Stage 2: Runtime Stage
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built jar
COPY --from=build /build/target/idea-forge-backend.jar app.jar

RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8080

# Render injects PORT at runtime
ENTRYPOINT ["sh", "-c", "exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod \
  -Dserver.port=${PORT:-8080} \
  -Dserver.address=0.0.0.0 \
  -jar app.jar"]
