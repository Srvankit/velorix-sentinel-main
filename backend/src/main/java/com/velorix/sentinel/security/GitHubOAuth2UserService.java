package com.velorix.sentinel.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Loads and validates GitHub OAuth2 user identities, strictly enforcing that email
 * information is fetched from GitHub's trusted API and verified as primary.
 */
@Component
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuth2UserService.class);

    private final DefaultOAuth2UserService delegate;
    private final RestClient restClient;

    public GitHubOAuth2UserService() {
        this(new DefaultOAuth2UserService(), RestClient.create());
    }

    public GitHubOAuth2UserService(DefaultOAuth2UserService delegate, RestClient restClient) {
        this.delegate = delegate;
        this.restClient = restClient;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);
        if (!"github".equalsIgnoreCase(request.getClientRegistration().getRegistrationId())) {
            return user;
        }

        List<Map<String, Object>> emails;
        try {
            emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .headers(headers -> headers.setBearerAuth(request.getAccessToken().getTokenValue()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException ex) {
            log.error("Failed to query GitHub /user/emails API: {}", ex.getMessage());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("github_email_fetch_failed", "Unable to verify email with GitHub", null), ex);
        }

        String verifiedEmail = selectVerifiedPrimaryEmail(emails);
        if (verifiedEmail == null || verifiedEmail.isBlank()) {
            log.warn("GitHub user {} lacks a verified primary email address", user.getName());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unverified_email", "GitHub account does not have a verified primary email address", null));
        }

        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put("email", verifiedEmail);
        attributes.put("email_verified", Boolean.TRUE);

        String nameAttribute = request.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(
                new ArrayList<GrantedAuthority>(user.getAuthorities()),
                attributes,
                nameAttribute);
    }

    public static String selectVerifiedPrimaryEmail(List<Map<String, Object>> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        return emails.stream()
                .filter(email -> Boolean.TRUE.equals(email.get("verified")) && Boolean.TRUE.equals(email.get("primary")))
                .map(email -> email.get("email"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }
}
