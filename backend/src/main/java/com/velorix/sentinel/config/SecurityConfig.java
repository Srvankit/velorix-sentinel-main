package com.velorix.sentinel.config;

import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.constants.SecurityConstants;
import com.velorix.sentinel.security.JwtAccessDeniedHandler;
import com.velorix.sentinel.security.JwtAuthenticationEntryPoint;
import com.velorix.sentinel.security.JwtAuthenticationFilter;
import com.velorix.sentinel.security.OAuth2LoginSuccessHandler;
import com.velorix.sentinel.security.OAuth2LoginFailureHandler;
import com.velorix.sentinel.security.GitHubOAuth2UserService;
import com.velorix.sentinel.security.TrustedProxyManager;
import com.velorix.sentinel.security.ratelimit.RateLimitingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;

/**
 * Stateless JWT security configuration.
 *
 * <p>Public: {@code /api/auth/**}, health and OpenAPI. Everything under {@code /api/v1/**}
 * requires a valid access token.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oauth2LoginFailureHandler;
    private final GitHubOAuth2UserService oauth2UserService;
    private final TrustedProxyManager trustedProxyManager;

    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerEnabled;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oauth2LoginFailureHandler,
            GitHubOAuth2UserService oauth2UserService,
            TrustedProxyManager trustedProxyManager) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.clientRegistrations = clientRegistrations;
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.oauth2LoginFailureHandler = oauth2LoginFailureHandler;
        this.oauth2UserService = oauth2UserService;
        this.trustedProxyManager = trustedProxyManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var security = http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(
                                    ApiConstants.AUTH_BASE + "/change-password",
                                    ApiConstants.AUTH_BASE + "/account",
                                    ApiConstants.AUTH_BASE + "/link-oauth",
                                    ApiConstants.AUTH_BASE + "/me",
                                    ApiConstants.API_V1 + "/auth/change-password",
                                    ApiConstants.API_V1 + "/auth/account",
                                    ApiConstants.API_V1 + "/auth/link-oauth",
                                    ApiConstants.API_V1 + "/auth/me").authenticated()
                            .requestMatchers(ApiConstants.PUBLIC_ENDPOINTS).permitAll();

                    if (isProductionProfile() && !swaggerEnabled) {
                        auth.requestMatchers(ApiConstants.SWAGGER_ENDPOINTS).denyAll();
                    } else {
                        auth.requestMatchers(ApiConstants.SWAGGER_ENDPOINTS).hasRole("ADMIN");
                    }

                    auth.requestMatchers(ApiConstants.PROTECTED_PATTERN).authenticated()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitingFilter, JwtAuthenticationFilter.class);
        if (clientRegistrations.getIfAvailable() != null) {
            security.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(endpoint -> endpoint.baseUri(ApiConstants.API_ROOT + "/oauth2/authorization"))
                    .userInfoEndpoint(endpoint -> endpoint.userService(oauth2UserService))
                    .successHandler(oauth2LoginSuccessHandler)
                    .failureHandler(oauth2LoginFailureHandler));
        }
        return security.build();
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(SecurityConstants.BCRYPT_STRENGTH);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private boolean isProductionProfile() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }
}
