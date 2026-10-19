package com.velorix.sentinel.config;

import com.velorix.sentinel.constants.ApiConstants;
import com.velorix.sentinel.constants.SecurityConstants;
import com.velorix.sentinel.security.JwtAccessDeniedHandler;
import com.velorix.sentinel.security.JwtAuthenticationEntryPoint;
import com.velorix.sentinel.security.JwtAuthenticationFilter;
import com.velorix.sentinel.security.OAuth2LoginSuccessHandler;
import com.velorix.sentinel.security.GitHubOAuth2UserService;
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
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final GitHubOAuth2UserService oauth2UserService;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
            GitHubOAuth2UserService oauth2UserService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.clientRegistrations = clientRegistrations;
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.oauth2UserService = oauth2UserService;
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ApiConstants.PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(ApiConstants.PROTECTED_PATTERN).authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (clientRegistrations.getIfAvailable() != null) {
            security.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(endpoint -> endpoint.baseUri(ApiConstants.API_ROOT + "/oauth2/authorization"))
                    .userInfoEndpoint(endpoint -> endpoint.userService(oauth2UserService))
                    .successHandler(oauth2LoginSuccessHandler));
        }
        return security.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(SecurityConstants.BCRYPT_STRENGTH);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
