package com.velorix.sentinel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.BetaProperties;
import com.velorix.sentinel.dto.auth.RegisterRequest;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.exception.ForbiddenException;
import com.velorix.sentinel.mapper.UserMapper;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.OAuthExchangeCodeRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.repository.VerificationTokenRepository;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.security.JwtTokenProvider;
import com.velorix.sentinel.service.impl.AuthServiceImpl;
import com.velorix.sentinel.storage.FileStorageService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("Task 8.4 — AuthService Closed Beta Gate Unit Tests")
class AuthServiceBetaGateUnitTest {

    private UserRepository userRepository;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider tokenProvider;
    private AuthenticationManager authenticationManager;
    private RefreshTokenService refreshTokenService;
    private VerificationTokenService verificationTokenService;
    private VerificationTokenRepository verificationTokenRepository;
    private OAuthExchangeCodeRepository oauthExchangeCodeRepository;
    private ActivityLogService activityLogService;
    private EmailService emailService;
    private AppProperties appProperties;
    private CurrentUserProvider currentUserProvider;
    private UploadedFileRepository uploadedFileRepository;
    private FileStorageService fileStorageService;
    private RefreshTokenRepository refreshTokenRepository;
    private ActivityLogRepository activityLogRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenProvider = mock(JwtTokenProvider.class);
        authenticationManager = mock(AuthenticationManager.class);
        refreshTokenService = mock(RefreshTokenService.class);
        verificationTokenService = mock(VerificationTokenService.class);
        verificationTokenRepository = mock(VerificationTokenRepository.class);
        oauthExchangeCodeRepository = mock(OAuthExchangeCodeRepository.class);
        activityLogService = mock(ActivityLogService.class);
        emailService = mock(EmailService.class);
        appProperties = mock(AppProperties.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        uploadedFileRepository = mock(UploadedFileRepository.class);
        fileStorageService = mock(FileStorageService.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        activityLogRepository = mock(ActivityLogRepository.class);

        when(appProperties.frontendBaseUrl()).thenReturn("https://velorix.tech");
    }

    private AuthServiceImpl createService(BetaProperties betaProperties) {
        return new AuthServiceImpl(
                userRepository,
                userMapper,
                passwordEncoder,
                tokenProvider,
                authenticationManager,
                refreshTokenService,
                verificationTokenService,
                verificationTokenRepository,
                oauthExchangeCodeRepository,
                activityLogService,
                emailService,
                appProperties,
                currentUserProvider,
                uploadedFileRepository,
                fileStorageService,
                refreshTokenRepository,
                activityLogRepository,
                betaProperties);
    }

    @Test
    @DisplayName("Password registration succeeds when invite-only is disabled")
    void passwordRegistrationSucceedsWhenBetaGateDisabled() {
        AuthServiceImpl service = createService(new BetaProperties(false, List.of()));
        RegisterRequest request = new RegisterRequest("Jane", "Doe", "jane@example.com", "Password123!", "Password123!");

        when(userRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
        User savedUser = User.builder().id(UUID.randomUUID()).email("jane@example.com").role(Role.USER).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(verificationTokenService.issue(any(User.class), eq(TokenPurpose.EMAIL_VERIFICATION))).thenReturn("token-123");

        service.register(request);

        verify(userRepository).save(any(User.class));
        verify(emailService).sendEmailVerification(any(User.class), anyString());
    }

    @Test
    @DisplayName("Password registration rejects unlisted email with 403 Forbidden when invite-only is enabled")
    void passwordRegistrationRejectsUnlistedEmailWhenBetaGateEnabled() {
        AuthServiceImpl service = createService(new BetaProperties(true, List.of("allowed@example.com")));
        RegisterRequest request = new RegisterRequest("Intruder", "User", "intruder@external.com", "Password123!", "Password123!");

        when(userRepository.findByEmailIgnoreCase("intruder@external.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Registration is currently invite-only.")
                .satisfies(ex -> assertThat(((ForbiddenException) ex).getCode()).isEqualTo("INVITE_ONLY"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Password registration allows listed email when invite-only is enabled")
    void passwordRegistrationAllowsListedEmailWhenBetaGateEnabled() {
        AuthServiceImpl service = createService(new BetaProperties(true, List.of("allowed@example.com")));
        RegisterRequest request = new RegisterRequest("Allowed", "User", "ALLOWED@example.com", "Password123!", "Password123!");

        when(userRepository.findByEmailIgnoreCase("allowed@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
        User savedUser = User.builder().id(UUID.randomUUID()).email("allowed@example.com").role(Role.USER).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(verificationTokenService.issue(any(User.class), eq(TokenPurpose.EMAIL_VERIFICATION))).thenReturn("token-123");

        service.register(request);

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("OAuth authentication for a NEW user rejects unlisted email when invite-only is enabled")
    void oAuthAuthenticationRejectsNewUserWhenUnlistedInBeta() {
        AuthServiceImpl service = createService(new BetaProperties(true, List.of("vip@domain.com")));

        when(userRepository.findByOauthProviderAndOauthProviderId("google", "sub-999")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("uninvited@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticateOAuthUser("google", "sub-999", "uninvited@gmail.com", "Uninvited", "User", true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Registration is currently invite-only.")
                .satisfies(ex -> assertThat(((ForbiddenException) ex).getCode()).isEqualTo("INVITE_ONLY"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("OAuth authentication for an EXISTING user succeeds even when invite-only is enabled and email is not on allowlist")
    void oAuthAuthenticationAllowsExistingUserEvenIfNotOnBetaAllowlist() {
        AuthServiceImpl service = createService(new BetaProperties(true, List.of("vip@domain.com")));

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("legacy@gmail.com")
                .role(Role.USER)
                .oauthProvider("google")
                .oauthProviderId("sub-111")
                .build();

        when(userRepository.findByOauthProviderAndOauthProviderId("google", "sub-111")).thenReturn(Optional.of(existingUser));

        User authenticated = service.authenticateOAuthUser("google", "sub-111", "legacy@gmail.com", "Legacy", "User", true);

        assertThat(authenticated).isSameAs(existingUser);
    }
}
