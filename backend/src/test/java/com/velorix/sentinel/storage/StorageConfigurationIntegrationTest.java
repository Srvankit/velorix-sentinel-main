package com.velorix.sentinel.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.velorix.sentinel.config.properties.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageConfiguration.class)
            .withBean(StorageProperties.class, () -> new StorageProperties(
                    "filesystem",
                    "./data/captures",
                    null,
                    null,
                    null,
                    new StorageProperties.S3Properties(
                            "test-bucket", "us-east-1", "http://localhost:9000",
                            "test-key", "test-secret", true, "captures/"
                    ),
                    null,
                    null
            ));

    @Test
    @DisplayName("Default / filesystem provider activates LocalFileSystemStorageService")
    void defaultActivatesFileSystemStorage() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context.getBean(FileStorageService.class))
                    .isInstanceOf(LocalFileSystemStorageService.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
        });
    }

    @Test
    @DisplayName("S3 provider activates S3CompatibleFileStorageService and S3Client")
    void s3ProviderActivatesS3Storage() {
        contextRunner
                .withPropertyValues("velorix.storage.provider=s3")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileStorageService.class);
                    assertThat(context.getBean(FileStorageService.class))
                            .isInstanceOf(S3CompatibleFileStorageService.class);
                    assertThat(context).hasSingleBean(S3Client.class);
                });
    }
}

