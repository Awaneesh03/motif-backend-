# Production-grade multi-stage Docker build for Railway deployment
# This Dockerfile does NOT require Maven wrapper (mvnw)

# ============================================
# Stage 1: Build Stage
# ============================================
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /build

# Copy POM file first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests -B && \
    # Verify JAR was created
    ls -lh target/ && \
    # Rename to app.jar for consistency
    mv target/idea-forge-backend.jar target/app.jar

# ============================================
# Stage 2: Runtime Stage
# ============================================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install wget for healthchecks (optional)
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Copy JAR from build stage
COPY --from=build /build/target/app.jar /app/app.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Railway provides PORT env variable dynamically
# Spring Boot will use ${PORT:8080} from application.yml
# JVM Options optimized for Railway deployment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-XX:+HeapDumpOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom \
-Dspring.profiles.active=prod"

# Expose port (Railway ignores this, but good for documentation)
EXPOSE ${PORT:-8080}

# Healthcheck - Railway will handle this, but keeping for local testing
# Note: Use wget with full URL, PORT is injected at runtime
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health 2>&1 || exit 1

# Run the application
# Railway will inject PORT env var automatically
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
