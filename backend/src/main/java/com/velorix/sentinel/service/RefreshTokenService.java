package com.velorix.sentinel.service;

import com.velorix.sentinel.entity.RefreshToken;
import com.velorix.sentinel.entity.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Lifecycle of database backed refresh tokens: issuance, rotation and revocation.
 */
public interface RefreshTokenService {

    /**
     * Issues a signed refresh token and persists its hash, initializing a new token family.
     *
     * @param rememberMe when false a shorter lived token is issued
     */
    IssuedRefreshToken issue(User user, boolean rememberMe);

    /**
     * Issues a signed refresh token in an existing token family with a parent token linkage.
     */
    IssuedRefreshToken issue(User user, boolean rememberMe, UUID familyId, String previousTokenHash);

    /**
     * Atomically rotates a refresh token under pessimistic row locking, enforcing
     * token reuse detection and family revocation upon replay attacks.
     */
    RotationResult rotate(String rawToken);

    /** Resolves a usable (unexpired, unrevoked) stored token, or fails with a token error. */
    RefreshToken requireUsable(String rawToken);

    /** Revokes a single stored token; unknown tokens are ignored. */
    void revoke(String rawToken);

    /** Revokes every active token for the user, e.g. on password reset. */
    int revokeAllForUser(User user);

    /** Revokes every active token belonging to the specified family. */
    int revokeFamily(UUID familyId);

    /** A freshly issued refresh token plus its persisted record. */
    record IssuedRefreshToken(String token, Instant expiresAt, RefreshToken record) {
    }

    /**
     * Domain result representing the outcome of an atomic rotation operation.
     */
    record RotationResult(
            Status status,
            IssuedRefreshToken issuedRefreshToken,
            User user,
            boolean rememberMe,
            String errorMessage) {

        public enum Status {
            SUCCESS,
            REUSE_DETECTED,
            INVALID_TOKEN,
            EXPIRED_TOKEN,
            ACCOUNT_DEACTIVATED
        }

        public static RotationResult success(IssuedRefreshToken issued, User user, boolean rememberMe) {
            return new RotationResult(Status.SUCCESS, issued, user, rememberMe, null);
        }

        public static RotationResult reuseDetected(String message) {
            return new RotationResult(Status.REUSE_DETECTED, null, null, false, message);
        }

        public static RotationResult invalidToken(String message) {
            return new RotationResult(Status.INVALID_TOKEN, null, null, false, message);
        }

        public static RotationResult expiredToken(String message) {
            return new RotationResult(Status.EXPIRED_TOKEN, null, null, false, message);
        }

        public static RotationResult accountDeactivated(String message) {
            return new RotationResult(Status.ACCOUNT_DEACTIVATED, null, null, false, message);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean isReuseDetected() {
            return status == Status.REUSE_DETECTED;
        }
    }
}
