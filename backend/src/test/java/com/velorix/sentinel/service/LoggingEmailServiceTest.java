package com.velorix.sentinel.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.service.impl.LoggingEmailService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingEmailServiceTest {

    private LoggingEmailService emailService;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        emailService = new LoggingEmailService();
        logger = (Logger) LoggerFactory.getLogger(LoggingEmailService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @Test
    @DisplayName("Verification email logs opaque user ID and redacts cleartext email and tokens")
    void verificationEmailLogsOpaqueUserIdAndRedactsSecrets() {
        String secretToken = "FAKE_SECRET_VERIFY_TOKEN_99999999";
        String verificationUrl = "https://sentinel.velorix.io/verify-email?token=" + secretToken;
        User user = testUser("ada.lovelace@velorix.io", "Ada", "Lovelace");

        emailService.sendEmailVerification(user, verificationUrl);

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);

        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);

        String formattedMessage = event.getFormattedMessage();
        assertThat(formattedMessage)
                .contains("[email-fallback]")
                .contains("Verification email event for user " + user.getId())
                .doesNotContain("ada.lovelace@velorix.io")
                .doesNotContain(secretToken)
                .doesNotContain(verificationUrl)
                .doesNotContain("verify-email")
                .doesNotContain("token=");
    }

    @Test
    @DisplayName("Password reset email logs masked email when user ID is missing and redacts secrets")
    void passwordResetLogsMaskedEmailWhenUserIdMissing() {
        String secretResetToken = "FAKE_SECRET_RESET_TOKEN_88888888";
        String resetUrl = "https://sentinel.velorix.io/reset-password?token=" + secretResetToken;
        // User without ID (unpersisted entity)
        User user = User.builder()
                .id(null)
                .email("alan.turing@velorix.io")
                .firstName("Alan")
                .lastName("Turing")
                .role(Role.USER)
                .build();

        emailService.sendPasswordReset(user, resetUrl);

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);

        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);

        String formattedMessage = event.getFormattedMessage();
        assertThat(formattedMessage)
                .contains("[email-fallback]")
                .contains("Password reset email event for a***g@velorix.io")
                .doesNotContain("alan.turing@velorix.io")
                .doesNotContain(secretResetToken)
                .doesNotContain(resetUrl)
                .doesNotContain("reset-password")
                .doesNotContain("token=");
    }

    @Test
    @DisplayName("Password changed email logs opaque user ID without cleartext email")
    void passwordChangedLogsOpaqueUserId() {
        User user = testUser("grace.hopper@velorix.io", "Grace", "Hopper");

        emailService.sendPasswordChanged(user);

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);

        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);

        String formattedMessage = event.getFormattedMessage();
        assertThat(formattedMessage)
                .contains("[email-fallback]")
                .contains("Password changed notification event for user " + user.getId())
                .doesNotContain("grace.hopper@velorix.io");
    }

    private User testUser(String email, String firstName, String lastName) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .role(Role.USER)
                .active(true)
                .verified(false)
                .build();
    }
}
