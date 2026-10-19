package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.VerificationToken;
import com.velorix.sentinel.entity.enums.TokenPurpose;
import com.velorix.sentinel.exception.TokenException;
import com.velorix.sentinel.repository.VerificationTokenRepository;
import com.velorix.sentinel.service.VerificationTokenService;
import com.velorix.sentinel.util.TokenUtils;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hash-at-rest implementation of {@link VerificationTokenService}.
 */
@Service
public class VerificationTokenServiceImpl implements VerificationTokenService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final AppProperties appProperties;

    public VerificationTokenServiceImpl(
            VerificationTokenRepository verificationTokenRepository, AppProperties appProperties) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional
    public String issue(User user, TokenPurpose purpose) {
        Instant now = Instant.now();
        verificationTokenRepository.consumeAllForUser(user.getId(), purpose, now);

        String rawToken = TokenUtils.randomToken();
        verificationTokenRepository.save(VerificationToken.builder()
                .user(user)
                .tokenHash(TokenUtils.sha256Hex(rawToken))
                .purpose(purpose)
                .expiresAt(now.plus(ttl(purpose)))
                .build());
        return rawToken;
    }

    @Override
    @Transactional
    public VerificationToken consume(String rawToken, TokenPurpose purpose) {
        VerificationToken token = verificationTokenRepository
                .findByTokenHashAndPurpose(TokenUtils.sha256Hex(rawToken), purpose)
                .orElseThrow(() -> new TokenException("This link is invalid or has already been used"));

        if (!token.isUsable(Instant.now())) {
            throw new TokenException("This link has expired. Request a new one to continue");
        }

        token.setConsumedAt(Instant.now());
        return verificationTokenRepository.save(token);
    }

    private Duration ttl(TokenPurpose purpose) {
        return purpose == TokenPurpose.EMAIL_VERIFICATION
                ? appProperties.emailVerificationExpiration()
                : appProperties.passwordResetExpiration();
    }
}
