package com.velorix.sentinel.service;

import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.User;
import java.time.Instant;

/**
 * Lifecycle of database backed refresh tokens: issuance, rotation and revocation.
 */
public interface RefreshTokenService {

    /**
     * Issues a signed refresh token and persists its hash.
     *
     * @param rememberMe when false a shorter lived token is issued
     */
    IssuedRefreshToken issue(User user, boolean rememberMe);

    /** Resolves a usable (unexpired, unrevoked) stored token, or fails with a token error. */
    RefreshToken requireUsable(String rawToken);

    /** Revokes a single stored token; unknown tokens are ignored. */
    void revoke(String rawToken);

    /** Revokes every active token for the user, e.g. on password reset. */
    int revokeAllForUser(User user);

    /** A freshly issued refresh token plus its persisted record. */
    record IssuedRefreshToken(String token, Instant expiresAt, RefreshToken record) {
    }
}
