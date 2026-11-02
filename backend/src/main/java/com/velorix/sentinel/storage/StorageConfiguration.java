package com.velorix.sentinel.storage;

import com.velorix.sentinel.config.properties.StorageProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Spring configuration providing the active {@link FileStorageService} bean.
 *
 * <p>Switches between {@link LocalFileSystemStorageService} (for local dev and testing)
 * and {@link S3CompatibleFileStorageService} (for production, Supabase Storage, AWS S3, MinIO)
 * via {@code velorix.storage.provider}.</p>
 */
@Configuration
public class StorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "velorix.storage.provider", havingValue = "filesystem", matchIfMissing = true)
    public FileStorageService fileSystemStorageService(StorageProperties properties) {
        log.info("Configuring LocalFileSystemStorageService (location={})", properties.location());
        return new LocalFileSystemStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "velorix.storage.provider", havingValue = "s3")
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3Properties s3Props = properties.s3();
        log.info("Configuring S3Client (bucket={}, region={}, endpoint={})",
                s3Props.bucket(), s3Props.region(), s3Props.endpoint());

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3Props.region()));

        if (s3Props.endpoint() != null && !s3Props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Props.endpoint()));
        }

        if (s3Props.accessKey() != null && s3Props.secretKey() != null
                && !s3Props.accessKey().isBlank() && !s3Props.secretKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Props.accessKey(), s3Props.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (s3Props.pathStyleAccess()) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "velorix.storage.provider", havingValue = "s3")
    public FileStorageService s3FileStorageService(StorageProperties properties, S3Client s3Client) {
        log.info("Configuring S3CompatibleFileStorageService (bucket={})", properties.s3().bucket());
        return new S3CompatibleFileStorageService(properties, s3Client);
    }
}

