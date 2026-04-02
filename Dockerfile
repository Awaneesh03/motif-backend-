# Multi-stage Docker build for Render deployment

# ============================================
# Stage 1: Build Stage
# ============================================
# Pinned to a specific patch release so every Render build uses an identical
# layer.  Debian-based (not Alpine/musl) — avoids musl/glibc incompatibilities
# that can silently break native components in the dependency tree.
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /build

# Resolve dependencies before copying source so that this layer is cached
# between deploys when only source files change.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

# -DskipTests     — no unit tests during image build (run in CI separately)
# -B              — batch/non-interactive mode
# -e              — print full stack trace on failure so the real error surfaces
RUN mvn clean package -DskipTests -B -e

# Debug: show what was produced before we try to copy it.
# If the step above failed this line is never reached, but if it produced
# unexpected files this makes the issue immediately visible in Render logs.
RUN ls -la target/

# find is used instead of `mv target/*.jar` because:
#   • glob expansion in sh is undefined when 0 files match — mv would silently
#     exit non-zero and the COPY in Stage 2 would fail with a cryptic message
#   • find -not -name "*.original" explicitly skips the .jar.original file that
#     spring-boot-maven-plugin leaves behind after repackaging, so we always
#     copy exactly the one fat JAR
RUN find target -maxdepth 1 -name "*.jar" -not -name "*.original" \
      -exec cp {} target/app.jar \;

# ============================================
# Stage 2: Runtime Stage
# ============================================
# Jammy (Ubuntu 22.04 LTS) — same glibc family as the build stage.
# JRE only — no compiler, Maven, or build tools in the final image.
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
