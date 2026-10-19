package com.velorix.sentinel.service;

import com.velorix.sentinel.entity.User;

/**
 * Transactional email delivery boundary.
 *
 * <p>The default implementation logs the message so the flows are complete end to end;
 * swapping in a real provider is a single bean replacement.</p>
 */
public interface EmailService {

    void sendEmailVerification(User user, String verificationUrl);

    void sendPasswordReset(User user, String resetUrl);

    void sendPasswordChanged(User user);
}
