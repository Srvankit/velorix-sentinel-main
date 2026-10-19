package com.velorix.sentinel.constants;

/**
 * Centralised API path and versioning constants.
 */
public final class ApiConstants {

    public static final String API_ROOT = "/api";
    public static final String API_V1 = API_ROOT + "/v1";

    public static final String AUTH_BASE = API_ROOT + "/auth";
    public static final String HEALTH_BASE = API_ROOT + "/health";

    public static final String ANALYSIS_BASE = API_V1 + "/analysis";
    public static final String THREATS_BASE = API_V1 + "/threats";
    public static final String FILES_BASE = API_V1 + "/files";
    public static final String DASHBOARD_BASE = API_V1 + "/dashboard";


    public static final String PUBLIC_AUTH_PATTERN = AUTH_BASE + "/**";
    public static final String PROTECTED_PATTERN = API_V1 + "/**";

    public static final String[] PUBLIC_ENDPOINTS = {
            PUBLIC_AUTH_PATTERN,
            API_ROOT + "/oauth2/**",
            "/login/oauth2/**",
            HEALTH_BASE + "/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/error"
    };

    private ApiConstants() {
        throw new AssertionError("No instances allowed");
    }
}
