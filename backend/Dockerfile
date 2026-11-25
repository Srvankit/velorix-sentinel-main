# ==============================================================================
# Velorix Sentinel - Production Backend Container Image (Render / Staging)
# Optimized multi-stage build with resilient dependency caching, conservative memory
# management, non-root security execution (UID 10001), and curl healthcheck.
# ==============================================================================

FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Configure conservative memory limits for Render builder environments (512MB - 2GB)
ENV MAVEN_OPTS="-Xmx512m -XX:+ExitOnOutOfMemoryError"

# Layer caching: copy pom.xml first to resolve dependencies before copying source code
COPY pom.xml .

# Download dependencies and build plugins into dedicated layer with HTTP retry resilience.
# Mitigates transient 429 rate limiting on shared builder IPs and network drops.
RUN max_attempts=5; \
    attempt=1; \
    delay=10; \
    while true; do \
        echo "Resolving dependencies (attempt $attempt of $max_attempts)..."; \
        if mvn -B dependency:resolve dependency:resolve-plugins \
            -Daether.connector.http.retryHandler.count=5 \
            -Daether.connector.http.retryHandler.interval=5000 \
            -Daether.connector.http.connectionMaxTtl=60 \
            -Daether.connector.requestTimeout=60000 \
            -Dmaven.wagon.http.retryHandler.count=5 \
            -Dmaven.wagon.http.retryHandler.requestSentRetryEnabled=true; then \
            break; \
        fi; \
        if [ "$attempt" -ge "$max_attempts" ]; then \
            echo "ERROR: Maven dependency resolution failed after $max_attempts attempts." >&2; \
            exit 1; \
        fi; \
        echo "Maven dependency resolution attempt $attempt failed. Retrying in ${delay}s..." >&2; \
        sleep "$delay"; \
        delay=$((delay * 2)); \
        attempt=$((attempt + 1)); \
    done

# Copy source code after dependencies have been resolved and cached
COPY src ./src

# Compile and package executable fat jar skipping tests (tests are pre-verified in CI).
# Includes a retry loop in case any secondary plugin or resource resolution encounters transient 429.
RUN max_attempts=3; \
    attempt=1; \
    delay=10; \
    while true; do \
        echo "Packaging application (attempt $attempt of $max_attempts)..."; \
        if mvn -B clean package -DskipTests \
            -Daether.connector.http.retryHandler.count=5 \
            -Daether.connector.http.retryHandler.interval=5000 \
            -Daether.connector.http.connectionMaxTtl=60 \
            -Daether.connector.requestTimeout=60000 \
            -Dmaven.wagon.http.retryHandler.count=5 \
            -Dmaven.wagon.http.retryHandler.requestSentRetryEnabled=true; then \
            break; \
        fi; \
        if [ "$attempt" -ge "$max_attempts" ]; then \
            echo "ERROR: Maven package failed after $max_attempts attempts." >&2; \
            exit 1; \
        fi; \
        echo "Maven package attempt $attempt failed. Retrying in ${delay}s..." >&2; \
        sleep "$delay"; \
        delay=$((delay * 2)); \
        attempt=$((attempt + 1)); \
    done

# ==============================================================================
# Runtime Stage
# ==============================================================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install curl for container health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Run as unprivileged non-root user
RUN groupadd -g 10001 appuser && \
    useradd -u 10001 -g appuser -d /app -s /sbin/nologin appuser && \
    chown -R 10001:10001 /app

# Copy verified Spring Boot executable fat jar (excludes *.jar.original)
COPY --from=build --chown=10001:10001 /app/target/sentinel-backend-*.jar app.jar

# Render web services detect the application on this exposed port.
ENV SERVER_PORT=10000
EXPOSE 10000

USER 10001:10001

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:10000/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-jar", "app.jar"]
