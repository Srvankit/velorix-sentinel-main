package com.velorix.sentinel.service;

import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.VerificationToken;
import com.velorix.sentinel.entity.enums.TokenPurpose;

/**
 * Issues and consumes the single use tokens backing email verification and password reset.
 */
public interface VerificationTokenService {

    /**
     * Invalidates any outstanding token of the same purpose and issues a new one.
     *
     * @return the raw token to embed in the outgoing email; only its hash is stored
     */
    String issue(User user, TokenPurpose purpose);

    /** Consumes a token, failing when unknown, expired or already used. */
    VerificationToken consume(String rawToken, TokenPurpose purpose);
}
