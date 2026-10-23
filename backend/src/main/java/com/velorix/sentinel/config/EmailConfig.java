package com.velorix.sentinel.config;

import com.velorix.sentinel.config.condition.SmtpConfiguredCondition;
import com.velorix.sentinel.config.condition.SmtpNotConfiguredCondition;
import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.MailProperties;
import com.velorix.sentinel.service.EmailService;
import com.velorix.sentinel.service.impl.LoggingEmailService;
import com.velorix.sentinel.service.impl.TransactionalEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Configuration selecting the active {@link EmailService} implementation based on SMTP availability.
 *
 * <p>When SMTP is configured with a non-blank host, {@link TransactionalEmailService} is registered.
 * Otherwise, {@link LoggingEmailService} acts as the safe, redacted fallback.</p>
 */
@Configuration
public class EmailConfig {

    @Bean
    @Conditional(SmtpConfiguredCondition.class)
    public EmailService transactionalEmailService(
            JavaMailSender mailSender,
            MailProperties mailProperties,
            AppProperties appProperties) {
        return new TransactionalEmailService(mailSender, mailProperties, appProperties);
    }

    @Bean
    @Conditional(SmtpNotConfiguredCondition.class)
    public EmailService loggingEmailService() {
        return new LoggingEmailService();
    }
}
