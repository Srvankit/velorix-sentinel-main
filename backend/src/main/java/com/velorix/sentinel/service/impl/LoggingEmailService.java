package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback email transport used when SMTP is not configured.
 *
 * <p>Logs only event metadata for troubleshooting. Recipient identities are sanitized to opaque
 * user identifiers or masked email addresses. Sensitive URLs, tokens, and message bodies are
 * strictly redacted.</p>
 */
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendEmailVerification(User user, String verificationUrl) {
        log.info("[email-fallback] Verification email event for {}", EmailLogSanitizer.describeRecipient(user));
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl) {
        log.info("[email-fallback] Password reset email event for {}", EmailLogSanitizer.describeRecipient(user));
    }

    @Override
    public void sendPasswordChanged(User user) {
        log.info("[email-fallback] Password changed notification event for {}", EmailLogSanitizer.describeRecipient(user));
    }
}
