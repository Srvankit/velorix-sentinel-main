package com.velorix.sentinel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class GitHubOAuth2UserServiceTest {

    @Test
    @DisplayName("selectVerifiedPrimaryEmail returns verified primary email when present")
    void selectVerifiedPrimaryEmailFindsPrimary() {
        List<Map<String, Object>> emails = List.of(
                Map.of("email", "secondary@velorix.io", "verified", true, "primary", false),
                Map.of("email", "unverified@velorix.io", "verified", false, "primary", true),
                Map.of("email", "primary@velorix.io", "verified", true, "primary", true)
        );

        String result = GitHubOAuth2UserService.selectVerifiedPrimaryEmail(emails);
        assertThat(result).isEqualTo("primary@velorix.io");
    }

    @Test
    @DisplayName("selectVerifiedPrimaryEmail returns null when list is null or empty")
    void selectVerifiedPrimaryEmailHandlesNullOrEmpty() {
        assertThat(GitHubOAuth2UserService.selectVerifiedPrimaryEmail(null)).isNull();
        assertThat(GitHubOAuth2UserService.selectVerifiedPrimaryEmail(Collections.emptyList())).isNull();
    }

    @Test
    @DisplayName("selectVerifiedPrimaryEmail returns null when no email is both verified and primary")
    void selectVerifiedPrimaryEmailNoMatch() {
        List<Map<String, Object>> emails = List.of(
                Map.of("email", "secondary@velorix.io", "verified", true, "primary", false),
                Map.of("email", "unverified@velorix.io", "verified", false, "primary", true)
        );

        String result = GitHubOAuth2UserService.selectVerifiedPrimaryEmail(emails);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("loadUser delegates directly for non-github providers without calling email API")
    void loadUserDelegatesForNonGitHub() {
        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        RestClient restClient = mock(RestClient.class);
        GitHubOAuth2UserService service = new GitHubOAuth2UserService(delegate, restClient);

        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("test-client")
                .redirectUri("https://example.com/callback")
                .authorizationUri("https://example.com/oauth")
                .tokenUri("https://example.com/token")
                .userNameAttributeName("sub")
                .build();

        OAuth2UserRequest request = new OAuth2UserRequest(
                registration,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token123", null, null));

        OAuth2User expectedUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-sub-123", "email", "user@gmail.com", "email_verified", true),
                "sub");
        when(delegate.loadUser(request)).thenReturn(expectedUser);

        OAuth2User user = service.loadUser(request);
        assertThat(user).isSameAs(expectedUser);
        verify(restClient, never()).get();
    }

    @Test
    @DisplayName("loadUser throws OAuth2AuthenticationException when GitHub user lacks verified primary email")
    void loadUserThrowsWhenNoVerifiedPrimaryEmail() {
        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("https://api.github.com/user/emails")).thenReturn(headersSpec);
        when(headersSpec.headers(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        // Returns only an unverified email
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(Map.of("email", "attacker@github.com", "verified", false, "primary", true)));

        GitHubOAuth2UserService service = new GitHubOAuth2UserService(delegate, restClient);

        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("gh-client")
                .redirectUri("https://example.com/callback")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userNameAttributeName("id")
                .userInfoUri("https://api.github.com/user")
                .build();

        OAuth2UserRequest request = new OAuth2UserRequest(
                registration,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "gh-token", null, null));

        OAuth2User rawUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", 123456, "login", "octocat"),
                "id");
        when(delegate.loadUser(request)).thenReturn(rawUser);

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("GitHub account does not have a verified primary email address");
    }

    @Test
    @DisplayName("loadUser throws OAuth2AuthenticationException when GitHub /user/emails API call fails")
    void loadUserThrowsWhenEmailApiFails() {
        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("https://api.github.com/user/emails")).thenReturn(headersSpec);
        when(headersSpec.headers(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RestClientException("Connection timeout"));

        GitHubOAuth2UserService service = new GitHubOAuth2UserService(delegate, restClient);

        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("gh-client")
                .redirectUri("https://example.com/callback")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userNameAttributeName("id")
                .userInfoUri("https://api.github.com/user")
                .build();

        OAuth2UserRequest request = new OAuth2UserRequest(
                registration,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "gh-token", null, null));

        OAuth2User rawUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", 123456, "login", "octocat"),
                "id");
        when(delegate.loadUser(request)).thenReturn(rawUser);

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(thrown -> {
                    OAuth2AuthenticationException oauthEx = (OAuth2AuthenticationException) thrown;
                    assertThat(oauthEx.getError().getErrorCode()).isEqualTo("github_email_fetch_failed");
                    assertThat(oauthEx.getError().getDescription()).isEqualTo("Unable to verify email with GitHub");
                });
    }

    @Test
    @DisplayName("loadUser successfully enriches GitHub user with verified primary email and sets email_verified=true")
    void loadUserSuccessEnrichesVerifiedPrimaryEmail() {
        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("https://api.github.com/user/emails")).thenReturn(headersSpec);
        when(headersSpec.headers(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(
                        Map.of("email", "linus@kernel.org", "verified", true, "primary", true),
                        Map.of("email", "secondary@kernel.org", "verified", true, "primary", false)
                ));

        GitHubOAuth2UserService service = new GitHubOAuth2UserService(delegate, restClient);

        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("gh-client")
                .redirectUri("https://example.com/callback")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userNameAttributeName("id")
                .userInfoUri("https://api.github.com/user")
                .build();

        OAuth2UserRequest request = new OAuth2UserRequest(
                registration,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "gh-token", null, null));

        OAuth2User rawUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", 98765, "login", "linustorvalds", "name", "Linus Torvalds"),
                "id");
        when(delegate.loadUser(request)).thenReturn(rawUser);

        OAuth2User enrichedUser = service.loadUser(request);
        assertThat((String) enrichedUser.getAttribute("email")).isEqualTo("linus@kernel.org");
        assertThat((Boolean) enrichedUser.getAttribute("email_verified")).isEqualTo(Boolean.TRUE);
        assertThat(enrichedUser.getName()).isEqualTo("98765");
    }
}
