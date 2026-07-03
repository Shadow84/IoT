# =============================================================================
# Multi-stage build
#
# Stage 1 — build: compiles the project and produces the executable JAR
# Stage 2 — runtime: copies only the JAR into a minimal JRE image
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: build
# -----------------------------------------------------------------------------
FROM gradle:9.5-jdk25 AS build

WORKDIR /workspace

# Copy dependency manifests first so Gradle's layer cache is reused when only
# source code changes (not build scripts or wrapper).
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# Pre-fetch dependencies (layer is cached as long as build.gradle doesn't change)
RUN ./gradlew dependencies --no-daemon -q || true

# Copy the full source tree and build the executable JAR
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# -----------------------------------------------------------------------------
# Stage 2: runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Copy the JAR produced in the build stage
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
