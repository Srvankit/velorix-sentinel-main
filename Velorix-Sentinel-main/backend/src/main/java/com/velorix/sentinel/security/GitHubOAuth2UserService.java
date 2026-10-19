package com.velorix.sentinel.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Loads GitHub's private email address when it is not present in /user.
 */
@Component
public class GitHubOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);
        if (!"github".equals(request.getClientRegistration().getRegistrationId())
                || user.getAttribute("email") != null) {
            return user;
        }

        List<Map<String, Object>> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .headers(headers -> headers.setBearerAuth(request.getAccessToken().getTokenValue()))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {});
        String email = selectEmail(emails);
        if (email == null) {
            return user;
        }

        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put("email", email);
        String nameAttribute = request.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(
                new ArrayList<GrantedAuthority>(user.getAuthorities()),
                attributes,
                nameAttribute);
    }

    private static String selectEmail(List<Map<String, Object>> emails) {
        if (emails == null) {
            return null;
        }
        return emails.stream()
                .filter(email -> Boolean.TRUE.equals(email.get("verified")))
                .sorted((left, right) -> Boolean.compare(
                        Boolean.TRUE.equals(right.get("primary")),
                        Boolean.TRUE.equals(left.get("primary"))))
                .map(email -> email.get("email"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }
}
