package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.JwtProperties;
import com.velorix.sentinel.constants.SecurityConstants;
import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.exception.TokenException;
import com.velorix.sentinel.repository.RefreshTokenRepository;
import com.velorix.sentinel.security.JwtTokenProvider;
import com.velorix.sentinel.service.RefreshTokenService;
import com.velorix.sentinel.util.RequestUtils;
import com.velorix.sentinel.util.TokenUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider tokenProvider,
            JwtProperties jwtProperties,
            AppProperties appProperties,
            ObjectProvider<HttpServletRequest> requestProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.appProperties = appProperties;
        this.requestProvider = requestProvider;
    }

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user, boolean rememberMe) {
        JwtTokenProvider.IssuedToken issued = tokenProvider.issueRefreshToken(user.getId(), user.getEmail());
        Instant expiresAt = rememberMe ? issued.expiresAt() : cap(issued.expiresAt());

        HttpServletRequest request = requestProvider.getIfAvailable();
        RefreshToken record = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(TokenUtils.sha256Hex(issued.token()))
                .expiresAt(expiresAt)
                .revoked(false)
                .ipAddress(RequestUtils.clientIp(request))
                .userAgent(RequestUtils.userAgent(request))
                .build());

        return new IssuedRefreshToken(issued.token(), expiresAt, record);
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

    /** Sessions without "remember me" get the shorter of the two lifetimes. */
    private Instant cap(Instant maximum) {
        Duration shortLived = appProperties.shortLivedRefreshExpiration();
        Duration configured = jwtProperties.refreshTokenExpiration();
        Duration effective = shortLived.compareTo(configured) < 0 ? shortLived : configured;
        Instant capped = Instant.now().plus(effective);
        return capped.isBefore(maximum) ? capped : maximum;
    }
}
