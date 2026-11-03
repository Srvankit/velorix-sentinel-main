package com.velorix.sentinel.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 6.1 — CORS Security Integration Test")
class CorsSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Preflight from authorized production origin (https://velorixsentinel.netlify.app) returns 204 with matching Allow-Origin")
    void preflightAuthorizedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://velorixsentinel.netlify.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://velorixsentinel.netlify.app"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Max-Age"));
    }

    @Test
    @DisplayName("Preflight from multi-tenant shared hosting origin (https://evil.netlify.app) does NOT contain Access-Control-Allow-Origin")
    void preflightMultiTenantWildcardOriginRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://evil.netlify.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Preflight from arbitrary attacker origin does NOT contain Access-Control-Allow-Origin")
    void preflightArbitraryAttackerOriginRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://attacker.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Preflight from subdomain or suffix spoofing origin does NOT contain Access-Control-Allow-Origin")
    void preflightSubdomainSpoofingRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://velorixsentinel.netlify.app.evil.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("Actual POST request from unauthorized origin does not receive Access-Control-Allow-Origin")
    void actualRequestFromUnauthorizedOriginReceivesNoCorsHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Origin", "https://evil.netlify.app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@velorix.com\",\"password\":\"password\"}"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

