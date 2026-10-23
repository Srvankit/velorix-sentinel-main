package com.velorix.sentinel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.MailProperties;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.entity.enums.Role;
import com.velorix.sentinel.service.impl.TransactionalEmailService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class TransactionalEmailServiceTest {

    private JavaMailSender mailSender;
    private MailProperties mailProperties;
    private AppProperties appProperties;
    private TransactionalEmailService emailService;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new JavaMailSenderImpl().createMimeMessage());
        doAnswer(inv -> {
            MimeMessage msg = inv.getArgument(0);
            if (msg != null) {
                msg.saveChanges();
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        mailProperties = new MailProperties("noreply@velorix.com", "Velorix Sentinel");
        appProperties = new AppProperties(
                "https://sentinel.velorix.io",
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofHours(12));

        emailService = new TransactionalEmailService(mailSender, mailProperties, appProperties);

        logger = (Logger) LoggerFactory.getLogger(TransactionalEmailService.class);
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
    @DisplayName("Verification email creates branded multipart alternative with plain-text and HTML")
    void sendEmailVerificationCreatesMultipartMimeMessage() throws Exception {
        String verificationUrl = "https://sentinel.velorix.io/verify-email?token=TEST_VERIFY_TOKEN_12345";
        User user = testUser("ada@velorix.io", "Ada", "Lovelace");

        emailService.sendEmailVerification(user, verificationUrl);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage message = captor.getValue();
        message.saveChanges();

        assertThat(message.getSubject()).isEqualTo("Verify your Velorix Sentinel account");
        assertThat(message.getAllRecipients()).containsExactly(new InternetAddress("ada@velorix.io"));

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("noreply@velorix.com");
        assertThat(from.getPersonal()).isEqualTo("Velorix Sentinel");

        // Verify multipart content
        assertThat(message.getContentType()).startsWith("multipart/");

        String plainText = extractPartContent(message, "text/plain");
        String htmlText = extractPartContent(message, "text/html");

        assertThat(plainText)
                .isNotNull()
                .contains("Hello Ada,")
                .contains(verificationUrl)
                .contains("expire in 24 hours");

        assertThat(htmlText)
                .isNotNull()
                .contains("Hello Ada,")
                .contains("Verify your email address")
                .contains("href=\"" + verificationUrl + "\"")
                .contains("expire in 24 hours");
    }

    @Test
    @DisplayName("HTML content escapes user-controlled strings (XSS prevention)")
    void htmlEscapesUserControlledContent() throws Exception {
        String maliciousName = "<script>alert('xss')</script> & \"test\"";
        String verificationUrl = "https://sentinel.velorix.io/verify-email?token=ESCAPE_TEST_TOKEN";
        User user = testUser("victim@velorix.io", maliciousName, "Attacker");

        emailService.sendEmailVerification(user, verificationUrl);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage message = captor.getValue();
        message.saveChanges();

        String htmlText = extractPartContent(message, "text/html");

        // Malicious script tags and quotes must be HTML escaped
        assertThat(htmlText)
                .isNotNull()
                .doesNotContain("<script>alert('xss')</script>")
                .contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt; &amp; &quot;test&quot;");
    }

    @Test
    @DisplayName("Password reset email contains reset link and 30-minute expiry")
    void sendPasswordResetCreatesCorrectMessage() throws Exception {
        String resetUrl = "https://sentinel.velorix.io/reset-password?token=TEST_RESET_TOKEN_54321";
        User user = testUser("alan@velorix.io", "Alan", "Turing");

        emailService.sendPasswordReset(user, resetUrl);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage message = captor.getValue();
        message.saveChanges();

        assertThat(message.getSubject()).isEqualTo("Reset your Velorix Sentinel password");
        assertThat(message.getAllRecipients()).containsExactly(new InternetAddress("alan@velorix.io"));

        String plainText = extractPartContent(message, "text/plain");
        String htmlText = extractPartContent(message, "text/html");

        assertThat(plainText)
                .isNotNull()
                .contains("Hello Alan,")
                .contains(resetUrl)
                .contains("expire in 30 minutes");

        assertThat(htmlText)
                .isNotNull()
                .contains("Reset your password")
                .contains("href=\"" + resetUrl + "\"")
                .contains("expire in 30 minutes");
    }

    @Test
    @DisplayName("Password changed email alerts user of session revocation")
    void sendPasswordChangedCreatesSecurityAlert() throws Exception {
        User user = testUser("grace@velorix.io", "Grace", "Hopper");

        emailService.sendPasswordChanged(user);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage message = captor.getValue();
        message.saveChanges();

        assertThat(message.getSubject()).isEqualTo("Security Alert: Your Velorix Sentinel password was changed");
        assertThat(message.getAllRecipients()).containsExactly(new InternetAddress("grace@velorix.io"));

        String plainText = extractPartContent(message, "text/plain");
        String htmlText = extractPartContent(message, "text/html");

        assertThat(plainText)
                .isNotNull()
                .contains("Hello Grace,")
                .contains("grace@velorix.io")
                .contains("invalidated as a security precaution");

        assertThat(htmlText)
                .isNotNull()
                .contains("Security Alert: Password Changed")
                .contains("grace@velorix.io")
                .contains("invalidated");
    }

    @Test
    @DisplayName("Logs use opaque user ID when available and redact sensitive URLs and cleartext emails")
    void logsOpaqueUserIdAndRedactsSensitiveInformation() {
        String secretToken = "RECOGNIZABLE_SECRET_TOKEN_XYZ_123456789";
        String verificationUrl = "https://sentinel.velorix.io/verify-email?token=" + secretToken;
        User user = testUser("redact@velorix.io", "Redact", "Test");

        emailService.sendEmailVerification(user, verificationUrl);

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);

        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);

        String message = event.getFormattedMessage();
        assertThat(message)
                .contains("[email] Dispatched verification email for user " + user.getId())
                .doesNotContain("redact@velorix.io")
                .doesNotContain(secretToken)
                .doesNotContain(verificationUrl)
                .doesNotContain("verify-email")
                .doesNotContain("token=");
    }

    @Test
    @DisplayName("Logs use masked email when user ID is unavailable")
    void logsMaskedEmailWhenUserIdUnavailable() {
        String secretToken = "RECOGNIZABLE_SECRET_MASK_555";
        String verificationUrl = "https://sentinel.velorix.io/verify-email?token=" + secretToken;
        User user = User.builder()
                .id(null)
                .email("redact.unpersisted@velorix.io")
                .firstName("Redact")
                .lastName("Test")
                .role(Role.USER)
                .build();

        emailService.sendEmailVerification(user, verificationUrl);

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);

        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);

        String message = event.getFormattedMessage();
        assertThat(message)
                .contains("[email] Dispatched verification email for r***d@velorix.io")
                .doesNotContain("redact.unpersisted@velorix.io")
                .doesNotContain(secretToken)
                .doesNotContain(verificationUrl);
    }

    @Test
    @DisplayName("SMTP exceptions are handled gracefully without breaking caller and without leaking secrets or cleartext email")
    void handlesSmtpExceptionsSafely() {
        doThrow(new MailSendException("SMTP server connection refused to host 10.0.0.1:587"))
                .when(mailSender).send(any(MimeMessage.class));

        String secretToken = "RECOGNIZABLE_SECRET_ON_FAILURE_99999";
        String verificationUrl = "https://sentinel.velorix.io/verify-email?token=" + secretToken;
        User user = testUser("fail@velorix.io", "Fail", "User");

        // Caller must not crash with uncaught exception
        assertDoesNotThrow(() -> emailService.sendEmailVerification(user, verificationUrl));

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);

        ILoggingEvent event = events.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);

        String logMessage = event.getFormattedMessage();
        assertThat(logMessage)
                .contains("[email] Failed to send verification email for user " + user.getId() + ": MailSendException")
                .doesNotContain("fail@velorix.io")
                .doesNotContain(secretToken)
                .doesNotContain(verificationUrl)
                .doesNotContain("verify-email")
                .doesNotContain("token=");
    }

    @Test
    @DisplayName("Empty or null recipient address is handled gracefully without sending")
    void handlesEmptyRecipientGracefully() {
        User user = testUser("", "Empty", "Email");

        emailService.sendEmailVerification(user, "https://example.com/verify-email?token=abc");

        verify(mailSender, never()).send(any(MimeMessage.class));

        List<ILoggingEvent> events = listAppender.list;
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(events.get(0).getFormattedMessage()).contains("Cannot send verification email: recipient address is empty");
    }

    private String extractPartContent(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType) && part.getContent() instanceof String str) {
            return str;
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String result = extractPartContent(multipart.getBodyPart(i), mimeType);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
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
