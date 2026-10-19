package com.velorix.sentinel;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.CorsProperties;
import com.velorix.sentinel.config.properties.DetectionProperties;
import com.velorix.sentinel.config.properties.InspectionProperties;
import com.velorix.sentinel.config.properties.JwtProperties;
import com.velorix.sentinel.config.properties.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point of the Velorix Sentinel backend.
 */
@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        CorsProperties.class,
        AppProperties.class,
        StorageProperties.class,
        InspectionProperties.class,
        DetectionProperties.class})
public class SentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelApplication.class, args);
    }
}
