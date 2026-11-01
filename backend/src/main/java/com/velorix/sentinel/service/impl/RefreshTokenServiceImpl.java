package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.JwtProperties;
import com.velorix.sentinel.constants.SecurityConstants;
import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.ActivityType;
import com.velorix.sentinel.exception.TokenException;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.security.JwtTokenProvider;
import com.velorix.sentinel.service.ActivityLogService;
import com.velorix.sentinel.service.RefreshTokenService;
import com.velorix.sentinel.util.RequestUtils;
import com.velorix.sentinel.util.TokenUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signed refresh tokens whose SHA-256 hash is stored, enabling server side revocation.
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final AppProperties appProperties;
    private final ActivityLogService activityLogService;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider tokenProvider,
            JwtProperties jwtProperties,
            AppProperties appProperties,
            ActivityLogService activityLogService,
            ObjectProvider<HttpServletRequest> requestProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.appProperties = appProperties;
        this.activityLogService = activityLogService;
        this.requestProvider = requestProvider;
    }

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user, boolean rememberMe) {
        return issue(user, rememberMe, UUID.randomUUID(), null);
    }

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user, boolean rememberMe, UUID familyId, String previousTokenHash) {
        JwtTokenProvider.IssuedToken issued = tokenProvider.issueRefreshToken(user.getId(), user.getEmail());
        Instant expiresAt = rememberMe ? issued.expiresAt() : cap(issued.expiresAt());

        HttpServletRequest request = requestProvider.getIfAvailable();
        RefreshToken record = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(TokenUtils.sha256Hex(issued.token()))
                .familyId(familyId != null ? familyId : UUID.randomUUID())
                .previousTokenHash(previousTokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .ipAddress(RequestUtils.clientIp(request))
                .userAgent(RequestUtils.userAgent(request))
                .build());

        return new IssuedRefreshToken(issued.token(), expiresAt, record);
    }

    @Override
    @Transactional
    public RotationResult rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return RotationResult.invalidToken("Refresh token is required");
        }

        Claims claims;
        try {
            claims = tokenProvider.parseClaims(rawToken.trim());
        } catch (Exception ex) {
            log.debug("Failed parsing refresh token claims: {}", ex.getMessage());
            return RotationResult.invalidToken("The supplied token is not a refresh token");
        }

        if (!tokenProvider.isTokenOfType(claims, SecurityConstants.TOKEN_TYPE_REFRESH)) {
            return RotationResult.invalidToken("The supplied token is not a refresh token");
        }

        String tokenHash = TokenUtils.sha256Hex(rawToken.trim());

        // Pessimistic write lock acquires exclusive row lock in PostgreSQL
        Optional<RefreshToken> storedOpt = refreshTokenRepository.findByTokenHashWithLock(tokenHash);
        if (storedOpt.isEmpty()) {
            return RotationResult.invalidToken("This refresh token is no longer valid");
        }

        RefreshToken stored = storedOpt.get();
        User user = stored.getUser();
        Instant now = Instant.now();

        // 1. REUSE DETECTION: If token is already marked revoked, treat as compromised token replay
        if (stored.isRevoked()) {
            log.warn("Refresh token reuse detected for token {} in family {} for user {}",
                    stored.getId(), stored.getFamilyId(), user.getId());

            // Revoke all tokens belonging to the compromised family
            int revokedCount = refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            log.info("Revoked {} tokens in compromised family {}", revokedCount, stored.getFamilyId());

            // Durably record security incident in activity_logs
            activityLogService.failure(
                    user,
                    ActivityType.TOKEN_REVOKED,
                    "Suspicious token reuse detected for family " + stored.getFamilyId() + "; all sessions revoked.");

            return RotationResult.reuseDetected("Suspicious token reuse detected; all sessions revoked.");
        }

        // 2. Validate token ownership against claims subject
        UUID subjectId = tokenProvider.subjectId(claims);
        if (subjectId == null || !subjectId.equals(user.getId())) {
            return RotationResult.invalidToken("This refresh token does not belong to the requesting account");
        }

        // 3. Expiration check
        if (stored.isExpired(now)) {
            return RotationResult.expiredToken("This refresh token has expired or been revoked");
        }

        // 4. Inactive user check
        if (!user.isActive()) {
            return RotationResult.accountDeactivated("This account is deactivated");
        }

        // 5. Normal legitimate rotation:
        // Revoke the presented token
        stored.setRevoked(true);
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        // Determine rememberMe based on duration threshold
        boolean rememberMe = stored.getExpiresAt()
                .isAfter(now.plus(appProperties.shortLivedRefreshExpiration()));

        // Issue new child token belonging to the same family, recording parent token hash
        IssuedRefreshToken newIssued = issue(user, rememberMe, stored.getFamilyId(), stored.getTokenHash());

        // Record activity log for successful rotation
        activityLogService.success(user, ActivityType.TOKEN_REFRESHED, "Access token refreshed");

        return RotationResult.success(newIssued, user, rememberMe);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken requireUsable(String rawToken) {
        Claims claims = tokenProvider.parseClaims(rawToken);
        if (!tokenProvider.isTokenOfType(claims, SecurityConstants.TOKEN_TYPE_REFRESH)) {
            throw new TokenException("The supplied token is not a refresh token");
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawToken))
                .orElseThrow(() -> new TokenException("This refresh token is no longer valid"));

        if (!stored.isUsable(Instant.now())) {
            throw new TokenException("This refresh token has expired or been revoked");
        }
        if (!stored.getUser().getId().equals(tokenProvider.subjectId(claims))) {
            throw new TokenException("This refresh token does not belong to the requesting account");
        }
        if (!stored.getUser().isActive()) {
            throw new TokenException("This account is deactivated");
        }
        return stored;
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(TokenUtils.sha256Hex(rawToken));
        stored.filter(token -> !token.isRevoked()).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            log.debug("Revoked refresh token {}", token.getId());
        });
    }

    @Override
    @Transactional
    public int revokeAllForUser(User user) {
        return refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    @Override
    @Transactional
    public int revokeFamily(UUID familyId) {
        if (familyId == null) {
            return 0;
        }
        return refreshTokenRepository.revokeFamily(familyId, Instant.now());
    }

    /** Sessions without "remember me" get the shorter of the two lifetimes. */
    private Instant cap(Instant maximum) {
        Duration shortLived = appProperties.shortLivedRefreshExpiration();
        Duration configured = jwtProperties.refreshTokenExpiration();
        Duration effective = shortLived.compareTo(configured) < 0 ? shortLived : configured;
        Instant capped = Instant.now().plus(effective);
        return capped.isBefore(maximum) ? capped : maximum;
    }
}
