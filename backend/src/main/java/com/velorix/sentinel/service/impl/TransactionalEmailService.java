package com.velorix.sentinel.service.impl;

import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.MailProperties;
import com.velorix.sentinel.entity.User;
import com.velorix.sentinel.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

/**
 * Transactional email service delivering branded multipart HTML and plain-text emails via SMTP.
 */
public class TransactionalEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(TransactionalEmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final AppProperties appProperties;

    public TransactionalEmailService(
            JavaMailSender mailSender,
            MailProperties mailProperties,
            AppProperties appProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.appProperties = appProperties;
    }

    @Override
    public void sendEmailVerification(User user, String verificationUrl) {
        sendMimeMessage(
                user != null ? user.getEmail() : null,
                "Verify your Velorix Sentinel account",
                buildVerificationText(user, verificationUrl),
                buildVerificationHtml(user, verificationUrl),
                "verification",
                EmailLogSanitizer.describeRecipient(user));
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl) {
        sendMimeMessage(
                user != null ? user.getEmail() : null,
                "Reset your Velorix Sentinel password",
                buildPasswordResetText(user, resetUrl),
                buildPasswordResetHtml(user, resetUrl),
                "password-reset",
                EmailLogSanitizer.describeRecipient(user));
    }

    @Override
    public void sendPasswordChanged(User user) {
        sendMimeMessage(
                user != null ? user.getEmail() : null,
                "Security Alert: Your Velorix Sentinel password was changed",
                buildPasswordChangedText(user),
                buildPasswordChangedHtml(user),
                "password-changed",
                EmailLogSanitizer.describeRecipient(user));
    }

    private void sendMimeMessage(
            String to,
            String subject,
            String textBody,
            String htmlBody,
            String emailType,
            String recipientTarget) {
        if (to == null || to.isBlank()) {
            log.warn("[email] Cannot send {} email: recipient address is empty", emailType);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.from(), mailProperties.fromName());
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.send(message);
            log.info("[email] Dispatched {} email for {}", emailType, recipientTarget);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.error("[email] Failed to send {} email for {}: {}", emailType, recipientTarget, ex.getClass().getSimpleName());
        }
    }

    private String buildVerificationText(User user, String verificationUrl) {
        return """
            Hello %s,

            Thank you for registering with Velorix Sentinel. Please verify your email address to complete your account setup:

            %s

            This verification link will expire in 24 hours.

            If you did not create a Velorix Sentinel account, you can safely ignore this email.

            — The Velorix Sentinel Security Team
            """.formatted(getDisplayName(user), verificationUrl);
    }

    private String buildVerificationHtml(User user, String verificationUrl) {
        String safeName = HtmlUtils.htmlEscape(getDisplayName(user));
        String safeUrl = HtmlUtils.htmlEscape(verificationUrl);
        String body = """
            <h2 style="margin: 0 0 16px 0; font-size: 18px; font-weight: 600; color: #ffffff;">Verify your email address</h2>
            <p style="margin: 0 0 16px 0;">Hello %s,</p>
            <p style="margin: 0 0 24px 0;">Thank you for registering with Velorix Sentinel. Please verify your email address to complete your account setup and activate your access.</p>
            <div style="margin: 28px 0; text-align: center;">
                <a href="%s" style="background-color: #4f46e5; color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; display: inline-block;">
                    Verify Email Address
                </a>
            </div>
            <p style="margin: 24px 0 8px 0; font-size: 13px; color: #94a3b8;">
                If the button above does not work, copy and paste this link into your browser:
            </p>
            <p style="margin: 0 0 24px 0; font-size: 12px; word-break: break-all;">
                <a href="%s" style="color: #818cf8; text-decoration: underline;">%s</a>
            </p>
            <p style="margin: 0 0 8px 0; font-size: 13px; color: #94a3b8;">
                This link will expire in 24 hours.
            </p>
            <p style="margin: 0; font-size: 13px; color: #64748b;">
                If you did not create a Velorix Sentinel account, you can safely ignore this email.
            </p>
            """.formatted(safeName, safeUrl, safeUrl, safeUrl);
        return buildEmailHtml("Verify your Velorix Sentinel account", body);
    }

    private String buildPasswordResetText(User user, String resetUrl) {
        return """
            Hello %s,

            We received a request to reset the password for your Velorix Sentinel account.

            You can reset your password using the following link:
            %s

            This link will expire in 30 minutes.

            If you did not request a password reset, you can safely ignore this email. Your password will remain unchanged.

            — The Velorix Sentinel Security Team
            """.formatted(getDisplayName(user), resetUrl);
    }

    private String buildPasswordResetHtml(User user, String resetUrl) {
        String safeName = HtmlUtils.htmlEscape(getDisplayName(user));
        String safeUrl = HtmlUtils.htmlEscape(resetUrl);
        String body = """
            <h2 style="margin: 0 0 16px 0; font-size: 18px; font-weight: 600; color: #ffffff;">Reset your password</h2>
            <p style="margin: 0 0 16px 0;">Hello %s,</p>
            <p style="margin: 0 0 24px 0;">We received a request to reset the password for your Velorix Sentinel account. Click the button below to choose a new password.</p>
            <div style="margin: 28px 0; text-align: center;">
                <a href="%s" style="background-color: #4f46e5; color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; display: inline-block;">
                    Reset Password
                </a>
            </div>
            <p style="margin: 24px 0 8px 0; font-size: 13px; color: #94a3b8;">
                If the button above does not work, copy and paste this link into your browser:
            </p>
            <p style="margin: 0 0 24px 0; font-size: 12px; word-break: break-all;">
                <a href="%s" style="color: #818cf8; text-decoration: underline;">%s</a>
            </p>
            <p style="margin: 0 0 8px 0; font-size: 13px; color: #94a3b8;">
                This link will expire in 30 minutes.
            </p>
            <p style="margin: 0; font-size: 13px; color: #64748b;">
                If you did not request a password reset, you can safely ignore this email. Your password will remain unchanged.
            </p>
            """.formatted(safeName, safeUrl, safeUrl, safeUrl);
        return buildEmailHtml("Reset your Velorix Sentinel password", body);
    }

    private String buildPasswordChangedText(User user) {
        return """
            Hello %s,

            The password for your Velorix Sentinel account (%s) was recently changed.

            All active sign-in sessions have been invalidated as a security precaution.

            If you made this change, no further action is required.

            If you did not make this change, please reset your password immediately or contact your administrator.

            — The Velorix Sentinel Security Team
            """.formatted(getDisplayName(user), user.getEmail());
    }

    private String buildPasswordChangedHtml(User user) {
        String safeName = HtmlUtils.htmlEscape(getDisplayName(user));
        String safeEmail = HtmlUtils.htmlEscape(user.getEmail());
        String body = """
            <div style="background-color: rgba(79, 70, 229, 0.1); border-left: 4px solid #6366f1; padding: 12px 16px; border-radius: 0 8px 8px 0; margin-bottom: 24px;">
                <h2 style="margin: 0; font-size: 16px; font-weight: 600; color: #818cf8;">Security Alert: Password Changed</h2>
            </div>
            <p style="margin: 0 0 16px 0;">Hello %s,</p>
            <p style="margin: 0 0 16px 0;">The password for your Velorix Sentinel account (<strong>%s</strong>) was recently changed.</p>
            <p style="margin: 0 0 24px 0; color: #94a3b8;">As a security precaution, all existing active sign-in sessions for your account have been invalidated.</p>
            <p style="margin: 0 0 16px 0; font-size: 13px; color: #94a3b8;">If you made this change, no further action is required.</p>
            <div style="background-color: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.2); border-radius: 8px; padding: 16px; margin: 24px 0;">
                <p style="margin: 0; font-size: 13px; color: #f87171; font-weight: 500;">
                    Did not make this change?
                </p>
                <p style="margin: 6px 0 0 0; font-size: 12px; color: #fca5a5;">
                    If you did not change your password, your account may be compromised. Please reset your password immediately or contact your administrator.
                </p>
            </div>
            """.formatted(safeName, safeEmail);
        return buildEmailHtml("Security Alert: Velorix Sentinel Password Changed", body);
    }

    private String buildEmailHtml(String title, String bodyContent) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #0b0f19; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #f8fafc;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color: #0b0f19; padding: 40px 20px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width: 560px; background-color: #111827; border: 1px solid #1f2937; border-radius: 12px; overflow: hidden;">
                                <tr>
                                    <td style="background: linear-gradient(135deg, #1e1b4b 0%%, #312e81 100%%); padding: 28px 36px; border-bottom: 1px solid #374151;">
                                        <h1 style="margin: 0; font-size: 20px; font-weight: 700; color: #ffffff; letter-spacing: -0.02em;">
                                            VELORIX <span style="color: #818cf8; font-weight: 400;">SENTINEL</span>
                                        </h1>
                                        <p style="margin: 4px 0 0 0; font-size: 12px; color: #c7d2fe; letter-spacing: 0.05em; text-transform: uppercase;">
                                            Security &amp; Threat Intelligence
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 36px; font-size: 15px; line-height: 1.6; color: #e2e8f0;">
                                        %s
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #0d1117; padding: 20px 36px; border-top: 1px solid #1f2937; font-size: 12px; color: #64748b; line-height: 1.5;">
                                        <p style="margin: 0;">This is an automated security notification from Velorix Sentinel.</p>
                                        <p style="margin: 4px 0 0 0;">If you need assistance, please contact your security administrator.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(title, bodyContent);
    }

    private String getDisplayName(User user) {
        if (user != null && user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName().trim();
        }
        return "there";
    }
}
