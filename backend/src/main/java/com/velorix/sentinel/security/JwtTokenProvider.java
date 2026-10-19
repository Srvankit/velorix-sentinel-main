package com.velorix.sentinel.security;

import com.velorix.sentinel.config.properties.JwtProperties;
import com.velorix.sentinel.constants.SecurityConstants;
import com.velorix.sentinel.exception.TokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the platform's access and refresh JWTs.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = resolveSigningKey(properties.secret());
    }

    public IssuedToken issueAccessToken(UUID userId, String email, String role) {
        return issue(userId, Map.of(
                        SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS,
                        SecurityConstants.CLAIM_EMAIL, email,
                        SecurityConstants.CLAIM_ROLE, role),
                properties.accessTokenExpiration());
    }

    public IssuedToken issueRefreshToken(UUID userId, String email) {
        return issue(userId, Map.of(
                        SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_REFRESH,
                        SecurityConstants.CLAIM_EMAIL, email),
                properties.refreshTokenExpiration());
    }

    /** Parses and verifies a token, throwing {@link TokenException} when it cannot be trusted. */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenException("The provided token is invalid or has expired", ex);
        }
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (TokenException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return false;
        }
    }

    public boolean isTokenOfType(Claims claims, String expectedType) {
        return expectedType.equals(claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class));
    }

    public UUID subjectId(Claims claims) {
        try {
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new TokenException("Token subject is not a valid user identifier", ex);
        }
    }

    /** Extracts the raw token from the configured header, or {@code null} when absent. */
    public String resolveToken(String headerValue) {
        String prefix = properties.tokenPrefix();
        if (headerValue == null || !headerValue.startsWith(prefix)) {
            return null;
        }
        String token = headerValue.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }

    public String headerName() {
        return properties.headerName();
    }

    private IssuedToken issue(UUID userId, Map<String, Object> claims, Duration ttl) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String token = Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, issuedAt, expiresAt);
    }

    private static SecretKey resolveSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is required. Generate a base64 secret with at least 64 bytes before starting the backend.");
        }
        byte[] keyBytes = decode(secret);
        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 64 bytes (use a random base64 value)");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET must be a valid base64 value", ex);
        }
    }

    /** A freshly minted token with its lifecycle timestamps. */
    public record IssuedToken(String token, Instant issuedAt, Instant expiresAt) {
    }
}
