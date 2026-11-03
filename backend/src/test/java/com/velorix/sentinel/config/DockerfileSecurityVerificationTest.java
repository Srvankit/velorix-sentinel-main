package com.velorix.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Task 6.4: Container Security, Healthcheck & JVM Memory Optimization Verification Test.
 * Validates backend/Dockerfile and backend/.dockerignore against container security standards.
 */
@DisplayName("Task 6.4 — Container Security, Healthcheck & JVM Memory Optimization Verification")
class DockerfileSecurityVerificationTest {

    private static Path dockerfilePath;
    private static Path dockerignorePath;
    private static String dockerfileContent;
    private static List<String> dockerignoreLines;

    @BeforeAll
    static void setUp() throws IOException {
        dockerfilePath = Paths.get("Dockerfile");
        if (!Files.exists(dockerfilePath)) {
            dockerfilePath = Paths.get("backend", "Dockerfile");
        }
        assertThat(dockerfilePath).exists();
        dockerfileContent = Files.readString(dockerfilePath);

        dockerignorePath = Paths.get(".dockerignore");
        if (!Files.exists(dockerignorePath)) {
            dockerignorePath = Paths.get("backend", ".dockerignore");
        }
        assertThat(dockerignorePath).exists();
        dockerignoreLines = Files.readAllLines(dockerignorePath);
    }

    @Test
    @DisplayName("Dockerfile executes as unprivileged non-root user (USER 10001:10001)")
    void dockerfileRunsAsNonRootUser() {
        assertThat(dockerfileContent)
                .as("Dockerfile must contain instruction to run as non-root user 10001:10001")
                .contains("USER 10001:10001");

        assertThat(dockerfileContent)
                .as("Dockerfile must provision unprivileged user and group 10001")
                .contains("10001");

        assertThat(dockerfileContent)
                .as("Dockerfile must set ownership of /app or copied jar to 10001:10001")
                .contains("--chown=10001:10001");
    }

    @Test
    @DisplayName("Dockerfile configures JVM container memory management flags (-XX:MaxRAMPercentage=75.0, -XX:InitialRAMPercentage=50.0)")
    void dockerfileConfiguresJvmMemoryOptimization() {
        assertThat(dockerfileContent)
                .as("Dockerfile ENTRYPOINT must configure -XX:MaxRAMPercentage=75.0 to prevent OOMKill")
                .contains("-XX:MaxRAMPercentage=75.0");

        assertThat(dockerfileContent)
                .as("Dockerfile ENTRYPOINT must configure -XX:InitialRAMPercentage=50.0 for optimal startup sizing")
                .contains("-XX:InitialRAMPercentage=50.0");

        assertThat(dockerfileContent)
                .as("Dockerfile ENTRYPOINT must execute java -jar app.jar with container flags")
                .contains("ENTRYPOINT [\"java\", \"-XX:MaxRAMPercentage=75.0\", \"-XX:InitialRAMPercentage=50.0\", \"-jar\", \"app.jar\"]");
    }

    @Test
    @DisplayName("Dockerfile configures container HEALTHCHECK with curl against actuator health endpoint")
    void dockerfileConfiguresContainerHealthcheck() {
        assertThat(dockerfileContent)
                .as("Dockerfile must contain HEALTHCHECK instruction")
                .contains("HEALTHCHECK");

        assertThat(dockerfileContent)
                .as("Healthcheck interval must be 30s")
                .contains("--interval=30s");

        assertThat(dockerfileContent)
                .as("Healthcheck timeout must be 5s")
                .contains("--timeout=5s");

        assertThat(dockerfileContent)
                .as("Healthcheck start-period must be 60s")
                .contains("--start-period=60s");

        assertThat(dockerfileContent)
                .as("Healthcheck retries must be 3")
                .contains("--retries=3");

        assertThat(dockerfileContent)
                .as("Healthcheck CMD must probe http://localhost:10000/actuator/health")
                .contains("curl -f http://localhost:10000/actuator/health || exit 1");
    }

    @Test
    @DisplayName("Dockerfile removes -DskipTests from the production build stage")
    void dockerfileDoesNotSkipTestsInBuildStage() {
        assertThat(dockerfileContent)
                .as("Dockerfile must run tests during build and not skip them with -DskipTests")
                .doesNotContain("-DskipTests");

        assertThat(dockerfileContent)
                .as("Dockerfile must not contain -Dmaven.test.skip")
                .doesNotContain("-Dmaven.test.skip");

        assertThat(dockerfileContent)
                .as("Dockerfile must build via mvn clean package")
                .contains("RUN mvn clean package");
    }

    @Test
    @DisplayName("Dockerfile installs curl in runtime image to support container HEALTHCHECK")
    void dockerfileInstallsCurlForHealthcheck() {
        assertThat(dockerfileContent)
                .as("Runtime image must install curl for the HEALTHCHECK instruction")
                .contains("curl");
    }

    @Test
    @DisplayName("backend/.dockerignore excludes build artifacts, version control, IDE files, and environment secrets")
    void dockerignoreExcludesSensitiveAndUnnecessaryFiles() {
        assertThat(dockerignoreLines)
                .as(".dockerignore must exclude .git directory")
                .anyMatch(line -> line.trim().equals(".git"));

        assertThat(dockerignoreLines)
                .as(".dockerignore must exclude target directory")
                .anyMatch(line -> line.trim().startsWith("target"));

        assertThat(dockerignoreLines)
                .as(".dockerignore must exclude .idea directory")
                .anyMatch(line -> line.trim().startsWith(".idea"));

        assertThat(dockerignoreLines)
                .as(".dockerignore must exclude markdown documentation (*.md)")
                .anyMatch(line -> line.trim().contains("*.md"));

        assertThat(dockerignoreLines)
                .as(".dockerignore must exclude .env files to protect local secrets")
                .anyMatch(line -> line.trim().startsWith(".env"));
    }
}
