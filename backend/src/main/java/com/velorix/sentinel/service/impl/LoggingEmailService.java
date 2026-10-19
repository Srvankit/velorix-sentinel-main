package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logging email transport used until a provider (SES, Postmark, SMTP) is configured.
 * Replace this bean to plug in real delivery; no calling code changes.
 */
@Service
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendEmailVerification(User user, String verificationUrl) {
        log.info("[email] Verify address for {} -> {}", user.getEmail(), verificationUrl);
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl) {
        log.info("[email] Password reset for {} -> {}", user.getEmail(), resetUrl);
    }

    @Override
    public void sendPasswordChanged(User user) {
        log.info("[email] Password changed notification for {}", user.getEmail());
    }
}
