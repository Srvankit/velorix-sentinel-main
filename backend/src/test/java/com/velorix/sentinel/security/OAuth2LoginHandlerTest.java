package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.service.AuthService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class OAuth2LoginHandlerTest {

    private AuthService authService;
    private AppProperties appProperties;
    private OAuth2LoginSuccessHandler successHandler;
    private OAuth2LoginFailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        ObjectProvider<AuthService> authServiceProvider = mock(ObjectProvider.class);
        when(authServiceProvider.getObject()).thenReturn(authService);

        appProperties = new AppProperties(
                "https://sentinel.velorix.io",
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofHours(12));

        successHandler = new OAuth2LoginSuccessHandler(authServiceProvider, appProperties);
        failureHandler = new OAuth2LoginFailureHandler(appProperties);
    }

    @Test
    @DisplayName("Success handler redirects to /oauth/callback with one-time exchange code on valid verified authentication")
    void successHandlerRedirectsWithExchangeCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-sub-789",
                        "email", "user@velorix.io",
                        "email_verified", true,
                        "given_name", "Ada",
                        "family_name", "Lovelace"),
                "sub");

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@velorix.io")
                .firstName("Ada")
                .lastName("Lovelace")
                .role(Role.USER)
                .verified(true)
                .active(true)
                .build();

        when(authService.authenticateOAuthUser(eq("google"), eq("google-sub-789"), eq("user@velorix.io"), eq("Ada"), eq("Lovelace"), eq(true)))
                .thenReturn(user);
        when(authService.createOAuthExchangeCode(eq(user)))
                .thenReturn("sec_code_xyz123");

        successHandler.onAuthenticationSuccess(request, response, auth);

        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        assertThat(redirectUrl).isEqualTo("https://sentinel.velorix.io/oauth/callback?code=sec_code_xyz123");
        assertThat(redirectUrl).doesNotContain("accessToken");
        assertThat(redirectUrl).doesNotContain("refreshToken");
        assertThat(redirectUrl).doesNotContain("#");
    }

    @Test
    @DisplayName("Success handler redirects to /login?error=... when email_verified is false")
    void successHandlerRedirectsToErrorWhenEmailNotVerified() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-sub-789",
                        "email", "unverified@velorix.io",
                        "email_verified", false),
                "sub");

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google");

        successHandler.onAuthenticationSuccess(request, response, auth);

        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        assertThat(redirectUrl).startsWith("https://sentinel.velorix.io/login?error=");
        assertThat(redirectUrl).contains("Email+is+not+verified+by+google");
    }

    @Test
    @DisplayName("Success handler redirects to /login?error=... when account conflict occurs")
    void successHandlerRedirectsToErrorOnConflict() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-sub-conflict",
                        "email", "existing@velorix.io",
                        "email_verified", true),
                "sub");

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "google");

        when(authService.authenticateOAuthUser(any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new ResourceConflictException("An account with this email already exists with password authentication."));

        successHandler.onAuthenticationSuccess(request, response, auth);

        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        assertThat(redirectUrl).startsWith("https://sentinel.velorix.io/login?error=");
        assertThat(redirectUrl).contains("An+account+with+this+email+already+exists");
    }

    @Test
    @DisplayName("Failure handler redirects to /login?error=... with encoded message")
    void failureHandlerRedirectsToFrontendLoginPage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthenticationException ex = new AuthenticationException("Bad credentials or state mismatch") {};

        failureHandler.onAuthenticationFailure(request, response, ex);

        String redirectUrl = response.getRedirectedUrl();
        assertThat(redirectUrl).isNotNull();
        assertThat(redirectUrl).startsWith("https://sentinel.velorix.io/login?error=");
        assertThat(redirectUrl).contains("Bad+credentials+or+state+mismatch");
    }
}
