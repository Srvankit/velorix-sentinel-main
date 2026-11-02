package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.dto.auth.AuthResponse;
import com.velorix.sentinel.dto.auth.ChangePasswordRequest;
import com.velorix.sentinel.dto.auth.DeleteAccountRequest;
import com.velorix.sentinel.dto.auth.ForgotPasswordRequest;
import com.velorix.sentinel.dto.auth.LoginRequest;
import com.velorix.sentinel.dto.auth.RefreshTokenRequest;
import com.velorix.sentinel.dto.auth.RegisterRequest;
import com.velorix.sentinel.dto.auth.ResendVerificationRequest;
import com.velorix.sentinel.dto.auth.ResetPasswordRequest;
import com.velorix.sentinel.dto.auth.UserResponse;
import com.velorix.sentinel.dto.auth.VerifyEmailRequest;
import com.velorix.sentinel.entity.OAuthExchangeCode;
import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.UploadedFile;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.VerificationToken;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.exception.BadRequestException;
import com.velorix.sentinel.exception.ForbiddenException;
import com.velorix.sentinel.exception.ResourceConflictException;
import com.velorix.sentinel.exception.TokenException;
import com.velorix.sentinel.exception.UnauthorizedException;
import com.velorix.sentinel.mapper.UserMapper;
import com.velorix.sentinel.repository.ActivityLogRepository;
import com.velorix.sentinel.repository.OAuthExchangeCodeRepository;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.repository.UploadedFileRepository;
import com.velorix.sentinel.repository.UserRepository;
import com.velorix.sentinel.repository.VerificationTokenRepository;
import com.velorix.sentinel.security.JwtTokenProvider;
import com.velorix.sentinel.security.CurrentUserProvider;
import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.service.AuthService;
import com.velorix.sentinel.service.EmailService;
import com.velorix.sentinel.service.RefreshTokenService;
import com.velorix.sentinel.service.VerificationTokenService;
import com.velorix.sentinel.storage.FileStorageService;
import com.velorix.sentinel.util.TokenUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Canonical set of supported OAuth provider identifiers.
     * Must match the registration IDs configured in Spring Security's OAuth2 client
     * and the values stored in the {@code users.oauth_provider} column.
     */
    private static final Set<String> SUPPORTED_OAUTH_PROVIDERS = Set.of("google", "github");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final OAuthExchangeCodeRepository oauthExchangeCodeRepository;
    private final ActivityLogService activityLogService;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final CurrentUserProvider currentUserProvider;
    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageService fileStorageService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ActivityLogRepository activityLogRepository;

    public AuthServiceImpl(
            UserRepository userRepository,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            AuthenticationManager authenticationManager,
            RefreshTokenService refreshTokenService,
            VerificationTokenService verificationTokenService,
            VerificationTokenRepository verificationTokenRepository,
            OAuthExchangeCodeRepository oauthExchangeCodeRepository,
            ActivityLogService activityLogService,
            EmailService emailService,
            AppProperties appProperties,
            CurrentUserProvider currentUserProvider,
            UploadedFileRepository uploadedFileRepository,
            FileStorageService fileStorageService,
            RefreshTokenRepository refreshTokenRepository,
            ActivityLogRepository activityLogRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.verificationTokenService = verificationTokenService;
        this.verificationTokenRepository = verificationTokenRepository;
        this.oauthExchangeCodeRepository = oauthExchangeCodeRepository;
        this.activityLogService = activityLogService;
        this.emailService = emailService;
        this.appProperties = appProperties;
        this.currentUserProvider = currentUserProvider;
        this.uploadedFileRepository = uploadedFileRepository;
        this.fileStorageService = fileStorageService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.activityLogRepository = activityLogRepository;
    }


    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);
        if (existingUser.isPresent()) {
            log.debug("Registration requested for existing email; suppressing enumeration");
            return;
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

        if (!user.isVerified()) {
            SecurityContextHolder.clearContext();
            activityLogService.failure(user, ActivityType.LOGIN_FAILURE, "Unverified email address");
            log.info("Rejected login for unverified user {}", user.getId());
            throw new ForbiddenException("Email verification is required before login.");
        }

        activityLogService.success(user, ActivityType.LOGIN_SUCCESS, "Successful sign-in");
        return issueSession(user, request.rememberMe());
    }

    @Override
    @Transactional
    public AuthResponse loginWithOAuth(
            String provider,
            String providerId,
            String email,
            String firstName,
            String lastName,
            boolean emailVerified) {
        User user = authenticateOAuthUser(provider, providerId, email, firstName, lastName, emailVerified);
        return issueSession(user, true);
    }

    @Override
    @Transactional
    public User authenticateOAuthUser(
            String provider,
            String providerId,
            String email,
            String firstName,
            String lastName,
            boolean emailVerified) {
        if (provider == null || provider.isBlank()) {
            throw new UnauthorizedException("OAuth provider is required");
        }
        if (providerId == null || providerId.isBlank()) {
            throw new UnauthorizedException("OAuth provider subject ID is required");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new UnauthorizedException("The identity provider did not return an email address");
        }
        if (!emailVerified) {
            throw new UnauthorizedException("Email is not verified by " + provider);
        }

        String cleanProvider = provider.trim().toLowerCase();
        String cleanProviderId = providerId.trim();

        // 1. Check if an account is already linked to this exact (provider, providerId)
        Optional<User> existingOAuthUser = userRepository.findByOauthProviderAndOauthProviderId(cleanProvider, cleanProviderId);
        if (existingOAuthUser.isPresent()) {
            User user = existingOAuthUser.get();
            if (!user.getEmail().equalsIgnoreCase(normalizedEmail)) {
                log.warn("OAuth provider identity {}/{} is linked to {} but callback presented email {}",
                        cleanProvider, cleanProviderId, user.getEmail(), normalizedEmail);
                throw new ResourceConflictException("This " + cleanProvider + " account is already linked to another user.");
            }
            if (!user.isActive()) {
                throw new UnauthorizedException("This account has been deactivated");
            }
            activityLogService.success(user, ActivityType.LOGIN_SUCCESS, "Successful " + cleanProvider + " OAuth sign-in");
            return user;
        }

        // 2. Check if a user with this email already exists
        Optional<User> existingEmailUser = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (existingEmailUser.isPresent()) {
            User user = existingEmailUser.get();
            if (!user.isActive()) {
                throw new UnauthorizedException("This account has been deactivated");
            }

            // Scenario 3B: Existing user is ALREADY VERIFIED
            if (user.isVerified()) {
                if (user.getOauthProvider() != null) {
                    throw new ResourceConflictException("This email is already linked to another " + user.getOauthProvider() + " account.");
                }
                log.warn("OAuth login for user {} rejected: account already verified with password authentication", user.getId());
                throw new ResourceConflictException("An account with this email already exists with password authentication. Please sign in with your password to link your social account.");
            }

            // Scenario 3A: Existing user is UNVERIFIED (Pre-account takeover defense)
            if (userRepository.existsByOauthProviderAndOauthProviderId(cleanProvider, cleanProviderId)) {
                throw new ResourceConflictException("This " + cleanProvider + " account is already linked to another user.");
            }

            // Revoke all existing sessions and refresh tokens
            refreshTokenService.revokeAllForUser(user);

            // Invalidate/consume all pending verification and password-reset tokens
            Instant now = Instant.now();
            verificationTokenRepository.consumeAllForUser(user.getId(), TokenPurpose.EMAIL_VERIFICATION, now);
            verificationTokenRepository.consumeAllForUser(user.getId(), TokenPurpose.PASSWORD_RESET, now);

            // Invalidate the old local password with an unmatchable BCrypt hash
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

            // Attach OAuth identity and mark verified atomically
            user.setOauthProvider(cleanProvider);
            user.setOauthProviderId(cleanProviderId);
            user.setVerified(true);
            userRepository.save(user);

            // Send security notification through transactional email service
            emailService.sendOAuthLinked(user, cleanProvider);

            activityLogService.success(user, ActivityType.PROFILE_UPDATED, "Unverified account transitioned and linked to " + cleanProvider);
            activityLogService.success(user, ActivityType.LOGIN_SUCCESS, "Successful social sign-in after account linking");
            return user;
        }

        // Scenario 2: Brand new account registering via OAuth
        if (userRepository.existsByOauthProviderAndOauthProviderId(cleanProvider, cleanProviderId)) {
            throw new ResourceConflictException("This " + cleanProvider + " account is already linked to another user.");
        }

        User newUser = userRepository.save(User.builder()
                .firstName(firstName == null || firstName.isBlank() ? "Velorix" : firstName.trim())
                .lastName(lastName == null || lastName.isBlank() ? "User" : lastName.trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.USER)
                .verified(true)
                .active(true)
                .oauthProvider(cleanProvider)
                .oauthProviderId(cleanProviderId)
                .build());

        activityLogService.success(newUser, ActivityType.REGISTRATION, "Account registered via " + cleanProvider + " OAuth");
        activityLogService.success(newUser, ActivityType.LOGIN_SUCCESS, "Successful social sign-in");
        return newUser;
    }

    @Override
    @Transactional
    public String createOAuthExchangeCode(User user) {
        if (user == null || !user.isActive()) {
            throw new UnauthorizedException("User account is inactive or not found");
        }
        String rawCode = TokenUtils.randomToken();
        String codeHash = TokenUtils.sha256Hex(rawCode);
        Instant now = Instant.now();
        OAuthExchangeCode exchangeCode = OAuthExchangeCode.builder()
                .codeHash(codeHash)
                .user(user)
                .expiresAt(now.plusSeconds(60))
                .build();
        oauthExchangeCodeRepository.save(exchangeCode);
        log.debug("Created OAuth exchange code for user {}", user.getId());
        return rawCode;
    }

    @Override
    @Transactional
    public AuthResponse exchangeOAuthCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new UnauthorizedException("OAuth exchange code is required");
        }
        String codeHash = TokenUtils.sha256Hex(rawCode.trim());
        OAuthExchangeCode exchangeCode = oauthExchangeCodeRepository.findByCodeHashWithLock(codeHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired OAuth exchange code"));

        Instant now = Instant.now();
        if (!exchangeCode.isUsable(now)) {
            throw new UnauthorizedException("OAuth exchange code is invalid, expired, or already used");
        }

        exchangeCode.setConsumedAt(now);
        oauthExchangeCodeRepository.save(exchangeCode);

        User user = exchangeCode.getUser();
        if (!user.isActive()) {
            throw new UnauthorizedException("This account has been deactivated");
        }

        activityLogService.success(user, ActivityType.LOGIN_SUCCESS, "Exchanged one-time OAuth code");
        return issueSession(user, true);
    }

    @Override
    @Transactional
    public void linkOAuth(String provider, String providerId) {
        User user = currentUserOrNull();
        if (user == null) {
            throw new UnauthorizedException("You must be signed in to link an OAuth provider");
        }
        linkOAuth(user, provider, providerId);
    }

    @Override
    @Transactional
    public void linkOAuth(User user, String provider, String providerId) {
        if (user == null) {
            throw new UnauthorizedException("You must be signed in to link an OAuth provider");
        }
        if (provider == null || provider.isBlank() || providerId == null || providerId.isBlank()) {
            throw new BadRequestException("OAuth provider and provider ID are required");
        }
        String cleanProvider = provider.trim().toLowerCase();
        String cleanProviderId = providerId.trim();

        if (!SUPPORTED_OAUTH_PROVIDERS.contains(cleanProvider)) {
            throw new BadRequestException(
                    "Unsupported OAuth provider '" + cleanProvider + "'. Supported providers: google, github");
        }

        if (user.getOauthProvider() != null && !user.getOauthProvider().isBlank()) {
            throw new ResourceConflictException("An OAuth provider is already linked to this account");
        }

        if (userRepository.existsByOauthProviderAndOauthProviderId(cleanProvider, cleanProviderId)) {
            throw new ResourceConflictException("This " + cleanProvider + " account is already linked to another user.");
        }

        user.setOauthProvider(cleanProvider);
        user.setOauthProviderId(cleanProviderId);
        userRepository.save(user);

        emailService.sendOAuthLinked(user, cleanProvider);
        activityLogService.success(user, ActivityType.PROFILE_UPDATED, "Linked " + cleanProvider + " social account");
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new TokenException("Refresh token is required");
        }

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(request.refreshToken());
        if (!result.isSuccess()) {
            throw new TokenException(result.errorMessage() != null ? result.errorMessage() : "Failed to rotate refresh token");
        }

        User user = result.user();
        RefreshTokenService.IssuedRefreshToken newRefresh = result.issuedRefreshToken();
        JwtTokenProvider.IssuedToken newAccess = tokenProvider.issueAccessToken(
                user.getId(), user.getEmail(), user.getRole().authority());

        return new AuthResponse(
                newAccess.token(),
                newRefresh.token(),
                BEARER,
                newAccess.expiresAt(),
                newRefresh.expiresAt(),
                userMapper.toResponse(user));
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
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUserProvider.requireUser();

        if (request == null || request.currentPassword() == null || request.currentPassword().isBlank()) {
            throw new BadRequestException("Current password is required");
        }
        if (request.newPassword() == null || request.newPassword().isBlank()) {
            throw new BadRequestException("New password is required");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            activityLogService.failure(user, ActivityType.PROFILE_UPDATED,
                    "Password change rejected: Incorrect current password");
            throw new BadRequestException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // A password change invalidates all existing sessions.
        refreshTokenService.revokeAllForUser(user);
        emailService.sendPasswordChanged(user);
        activityLogService.success(user, ActivityType.PROFILE_UPDATED, "Password changed successfully");
        log.info("Password changed successfully for user {}", user.getId());
    }

    @Override
    @Transactional
    public void deleteAccount(DeleteAccountRequest request) {
        User user = currentUserProvider.requireUser();

        if (user.isOAuthUser()) {
            boolean confirmed = request != null && (
                    "DELETE".equalsIgnoreCase(request.confirmation())
                    || "DELETE".equalsIgnoreCase(request.password())
                    || (request.password() != null && !request.password().isBlank() && passwordEncoder.matches(request.password(), user.getPassword())));
            if (!confirmed) {
                activityLogService.failure(user, ActivityType.ACCOUNT_DEACTIVATED,
                        "Account deletion rejected: Confirmation phrase 'DELETE' required");
                throw new BadRequestException("Please confirm account deletion by typing 'DELETE'");
            }
        } else {
            if (request == null || request.password() == null || request.password().isBlank()) {
                activityLogService.failure(user, ActivityType.ACCOUNT_DEACTIVATED,
                        "Account deletion rejected: Password is required");
                throw new BadRequestException("Password is required to confirm account deletion");
            }
            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                activityLogService.failure(user, ActivityType.ACCOUNT_DEACTIVATED,
                        "Account deletion rejected: Incorrect password");
                throw new BadRequestException("Incorrect password for account deletion");
            }
        }

        UUID userId = user.getId();
        log.info("Commencing account deletion for user {} ({})", userId, user.getEmail());

        // 1. Identify all capture storage keys for this user
        List<UploadedFile> userFiles = uploadedFileRepository.findAllByUserId(userId);
        List<String> storageKeys = userFiles.stream()
                .map(UploadedFile::getStoredFileName)
                .filter(Objects::nonNull)
                .toList();

        // 2. Cascade delete database entities
        uploadedFileRepository.deleteAll(userFiles);
        refreshTokenRepository.deleteAllByUserId(userId);
        verificationTokenRepository.deleteAllByUserId(userId);
        oauthExchangeCodeRepository.deleteAllByUserId(userId);
        activityLogRepository.deleteAllByUserId(userId);

        // Delete user row
        userRepository.delete(user);

        // 3. Purge storage objects
        for (String storageKey : storageKeys) {
            try {
                fileStorageService.delete(storageKey);
                log.info("Deleted capture object '{}' for deleted user {}", storageKey, userId);
            } catch (Exception ex) {
                log.warn("Failed to delete storage object '{}' for user {}: {}", storageKey, userId, ex.getMessage());
            }
        }

        SecurityContextHolder.clearContext();
        log.info("Completed account deletion for user {}", userId);
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
