package com.velorix.sentinel.security;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.dto.auth.AuthResponse;
import com.velorix.sentinel.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final ObjectProvider<AuthService> authService;
    private final AppProperties appProperties;

    public OAuth2LoginSuccessHandler(ObjectProvider<AuthService> authService, AppProperties appProperties) {
        this.authService = authService;
        this.appProperties = appProperties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        String firstName = principal.getAttribute("given_name");
        String lastName = principal.getAttribute("family_name");
        if ((firstName == null || firstName.isBlank()) && name != null) {
            String[] parts = name.trim().split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }

        AuthResponse session = authService.getObject().loginWithOAuth(email, firstName, lastName);
        String target = appProperties.frontendBaseUrl() + "/oauth/callback"
                + "#accessToken=" + encode(session.accessToken())
                + "&refreshToken=" + encode(session.refreshToken())
                + "&tokenType=" + encode(session.tokenType())
                + "&accessTokenExpiresAt=" + encode(session.accessTokenExpiresAt().toString())
                + "&refreshTokenExpiresAt=" + encode(session.refreshTokenExpiresAt().toString());
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
