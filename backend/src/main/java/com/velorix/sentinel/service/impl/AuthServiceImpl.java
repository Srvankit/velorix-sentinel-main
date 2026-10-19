package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.dto.auth.AuthResponse;
import com.velorix.sentinel.dto.auth.ForgotPasswordRequest;
import com.velorix.sentinel.dto.auth.LoginRequest;
import com.velorix.sentinel.dto.auth.RefreshTokenRequest;
import com.velorix.sentinel.dto.auth.RegisterRequest;
import com.velorix.sentinel.dto.auth.ResendVerificationRequest;
import com.velorix.sentinel.dto.auth.ResetPasswordRequest;
import com.velorix.sentinel.dto.auth.UserResponse;
import com.velorix.sentinel.dto.auth.VerifyEmailRequest;
import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.VerificationToken;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.exception.UnauthorizedException;
import com.velorix.sentinel.mapper.UserMapper;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.security.JwtTokenProvider;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.service.AuthService;
import com.velorix.sentinel.service.EmailService;
import com.velorix.sentinel.service.RefreshTokenService;
import com.velorix.sentinel.service.VerificationTokenService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of the authentication module.
 *
 * <p>Registration hashes credentials with BCrypt, login delegates to Spring Security's
 * {@link AuthenticationManager}, and every refresh token is persisted as a SHA-256 hash so it
 * can be rotated and revoked server side.</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String BEARER = "Bearer";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final ActivityLogService activityLogService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final CurrentUserProvider currentUserProvider;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            AuthenticationManager authenticationManager,
            RefreshTokenService refreshTokenService,
            VerificationTokenService verificationTokenService,
            ActivityLogService activityLogService,
            EmailService emailService,
            AppProperties appProperties,
            CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.verificationTokenService = verificationTokenService;
        this.activityLogService = activityLogService;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.currentUserProvider = currentUserProvider;
    }


    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("An account with this email already exists");
        }

        User user = userRepository.save(User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .verified(false)
                .active(true)
                .build());

        dispatchVerificationEmail(user);
        activityLogService.success(user, ActivityType.REGISTRATION, "Account registered");
        log.info("Registered account {}", user.getId());

        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AuthenticationException ex) {
            activityLogService.failure(user, ActivityType.LOGIN_FAILURE, "Failed sign-in for " + email);
            log.debug("Failed authentication for {}: {}", email, ex.getMessage());
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        if (user == null) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        activityLogService.success(user, ActivityType.LOGIN_SUCCESS, "Successful sign-in");
        return issueSession(user, request.rememberMe());
    }

    @Override
    @Transactional
    public AuthResponse loginWithOAuth(String email, String firstName, String lastName) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new UnauthorizedException("The identity provider did not return an email address");
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(() ->
                userRepository.save(User.builder()
                        .firstName(firstName == null || firstName.isBlank() ? "Velorix" : firstName.trim())
                        .lastName(lastName == null || lastName.isBlank() ? "User" : lastName.trim())
                        .email(normalizedEmail)
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .role(Role.USER)
                        .verified(true)
                        .active(true)
                        .build()));

        if (!user.isActive()) {
            throw new UnauthorizedException("This account has been deactivated");
        }
        user.setVerified(true);
        activityLogService.success(user, ActivityType.LOGIN_SUCCESS, "Successful social sign-in");
        return issueSession(user, true);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenService.requireUsable(request.refreshToken());
        User user = stored.getUser();

        // Rotation: the presented token dies with the response that replaces it.
        refreshTokenService.revoke(request.refreshToken());
        activityLogService.success(user, ActivityType.TOKEN_REFRESHED, "Access token refreshed");

        boolean rememberMe = stored.getExpiresAt()
                .isAfter(Instant.now().plus(appProperties.shortLivedRefreshExpiration()));
        return issueSession(user, rememberMe);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        Optional<User> user = Optional.ofNullable(currentUserOrNull());
        refreshTokenService.revoke(refreshToken);
        user.ifPresent(value -> activityLogService.success(value, ActivityType.LOGOUT, "Signed out"));
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always succeeds to avoid disclosing whether an account exists.
        userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).ifPresent(user -> {
            String token = verificationTokenService.issue(user, TokenPurpose.PASSWORD_RESET);
            emailService.sendPasswordReset(user, buildLink("/reset-password", token));
            activityLogService.success(user, ActivityType.PASSWORD_RESET_REQUESTED, "Password reset requested");
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        VerificationToken token = verificationTokenService.consume(request.token(), TokenPurpose.PASSWORD_RESET);
        User user = token.getUser();

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // A password change invalidates every existing session.
        refreshTokenService.revokeAllForUser(user);
        emailService.sendPasswordChanged(user);
        activityLogService.success(user, ActivityType.PASSWORD_RESET_COMPLETED, "Password reset completed");
    }

    @Override
    @Transactional
    public UserResponse verifyEmail(VerifyEmailRequest request) {
        VerificationToken token = verificationTokenService.consume(request.token(), TokenPurpose.EMAIL_VERIFICATION);
        User user = token.getUser();

        if (!user.isVerified()) {
            user.setVerified(true);
            userRepository.save(user);
            activityLogService.success(user, ActivityType.EMAIL_VERIFICATION, "Email address verified");
        }
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(user -> !user.isVerified())
                .ifPresent(this::dispatchVerificationEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        User user = currentUserOrNull();
        if (user == null) {
            throw new UnauthorizedException("You are not signed in");
        }
        return userMapper.toResponse(user);
    }

    private AuthResponse issueSession(User user, boolean rememberMe) {
        JwtTokenProvider.IssuedToken access = tokenProvider.issueAccessToken(
                user.getId(), user.getEmail(), user.getRole().authority());
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user, rememberMe);

        return new AuthResponse(
                access.token(),
                refresh.token(),
                BEARER,
                access.expiresAt(),
                refresh.expiresAt(),
                userMapper.toResponse(user));
    }

    private void dispatchVerificationEmail(User user) {
        String token = verificationTokenService.issue(user, TokenPurpose.EMAIL_VERIFICATION);
        emailService.sendEmailVerification(user, buildLink("/verify-email", token));
    }

    private String buildLink(String path, String token) {
        return appProperties.frontendBaseUrl() + path + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private User currentUserOrNull() {
        return currentUserProvider.principal()
                .flatMap(principal -> userRepository.findById(principal.id()))
                .filter(User::isActive)
                .orElse(null);
    }


    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
