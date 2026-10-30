package com.velorix.sentinel.security;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

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
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            sendErrorRedirect(request, response, "Invalid authentication principal");
            return;
        }

        String provider = oauthToken.getAuthorizedClientRegistrationId();
        if (provider == null || provider.isBlank()) {
            sendErrorRedirect(request, response, "Unknown OAuth provider");
            return;
        }

        OAuth2User principal = oauthToken.getPrincipal();
        String providerId = extractProviderId(provider, principal);
        if (providerId == null || providerId.isBlank()) {
            sendErrorRedirect(request, response, "Missing stable provider identity claim");
            return;
        }

        String email = principal.getAttribute("email");
        if (email == null || email.isBlank()) {
            sendErrorRedirect(request, response, "The identity provider did not return an email address");
            return;
        }

        boolean emailVerified = extractEmailVerified(principal);
        if (!emailVerified) {
            sendErrorRedirect(request, response, "Email is not verified by " + provider);
            return;
        }

        String name = principal.getAttribute("name");
        String firstName = principal.getAttribute("given_name");
        String lastName = principal.getAttribute("family_name");
        if ((firstName == null || firstName.isBlank()) && name != null) {
            String[] parts = name.trim().split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }
        if ((firstName == null || firstName.isBlank()) && principal.getAttribute("login") != null) {
            firstName = String.valueOf(principal.getAttribute("login"));
        }

        try {
            User user = authService.getObject().authenticateOAuthUser(
                    provider, providerId, email, firstName, lastName, emailVerified);
            String code = authService.getObject().createOAuthExchangeCode(user);
            String target = appProperties.frontendBaseUrl() + "/oauth/callback?code=" + encode(code);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, target);
        } catch (Exception ex) {
            log.warn("OAuth authentication failed for provider {}: {}", provider, ex.getMessage());
            sendErrorRedirect(request, response, ex.getMessage());
        }
    }

    private String extractProviderId(String provider, OAuth2User principal) {
        if ("google".equalsIgnoreCase(provider)) {
            Object sub = principal.getAttribute("sub");
            if (sub != null && !String.valueOf(sub).isBlank()) {
                return String.valueOf(sub);
            }
        } else if ("github".equalsIgnoreCase(provider)) {
            Object id = principal.getAttribute("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        } else {
            Object sub = principal.getAttribute("sub");
            if (sub != null && !String.valueOf(sub).isBlank()) {
                return String.valueOf(sub);
            }
            Object id = principal.getAttribute("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        }
        return principal.getName();
    }

    private boolean extractEmailVerified(OAuth2User principal) {
        Object verifiedAttr = principal.getAttribute("email_verified");
        if (verifiedAttr instanceof Boolean b) {
            return b;
        }
        if (verifiedAttr instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    private void sendErrorRedirect(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        clearAuthenticationAttributes(request);
        String target = appProperties.frontendBaseUrl() + "/login?error="
                + encode(message != null ? message : "OAuth authentication failed");
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
