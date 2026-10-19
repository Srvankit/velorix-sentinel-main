package com.velorix.sentinel.documentation;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI definition for the Velorix Sentinel backend.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    private final String serverUrl;

    public OpenApiConfig(@Value("${velorix.openapi.server-url:http://localhost:8080}") String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Bean
    public OpenAPI velorixOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Velorix Sentinel API")
                        .version("v1")
                        .description("""
                                Backend API for Velorix Sentinel, the AI powered deep packet inspection and
                                network threat detection platform by Velorix Technologies.

                                Authentication uses stateless JWT bearer tokens. Public endpoints live under
                                `/api/auth/**`; every `/api/v1/**` endpoint requires a valid access token.
                                """)
                        .contact(new Contact()
                                .name("Velorix Technologies")
                                .email("engineering@velorix.io"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url(serverUrl).description("Primary server")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token issued by /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
