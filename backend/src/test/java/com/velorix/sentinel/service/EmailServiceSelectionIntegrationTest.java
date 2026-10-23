package com.velorix.sentinel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.velorix.sentinel.config.EmailConfig;
import com.velorix.sentinel.config.properties.AppProperties;
import com.velorix.sentinel.config.properties.MailProperties;
import com.velorix.sentinel.service.impl.LoggingEmailService;
import com.velorix.sentinel.service.impl.TransactionalEmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

class EmailServiceSelectionIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmailConfig.class)
            .withBean(AppProperties.class, () -> new AppProperties(null, null, null, null))
            .withBean(MailProperties.class, () -> new MailProperties(null, null));

    @Test
    @DisplayName("When spring.mail.host is absent, LoggingEmailService is selected as fallback")
    void selectsLoggingEmailServiceWhenHostAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EmailService.class);
            assertThat(context).hasBean("loggingEmailService");
            assertThat(context).doesNotHaveBean("transactionalEmailService");
            assertThat(context.getBean(EmailService.class)).isInstanceOf(LoggingEmailService.class);
        });
    }

    @Test
    @DisplayName("When spring.mail.host is empty or whitespace, LoggingEmailService is selected")
    void selectsLoggingEmailServiceWhenHostEmpty() {
        contextRunner
                .withPropertyValues("spring.mail.host=")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasBean("loggingEmailService");
                    assertThat(context).doesNotHaveBean("transactionalEmailService");
                    assertThat(context.getBean(EmailService.class)).isInstanceOf(LoggingEmailService.class);
                });

        contextRunner
                .withPropertyValues("spring.mail.host=   ")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasBean("loggingEmailService");
                    assertThat(context).doesNotHaveBean("transactionalEmailService");
                    assertThat(context.getBean(EmailService.class)).isInstanceOf(LoggingEmailService.class);
                });
    }

    @Test
    @DisplayName("When spring.mail.host is configured, TransactionalEmailService is selected")
    void selectsTransactionalEmailServiceWhenHostConfigured() {
        contextRunner
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withPropertyValues("spring.mail.host=smtp.example.com")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailService.class);
                    assertThat(context).hasBean("transactionalEmailService");
                    assertThat(context).doesNotHaveBean("loggingEmailService");
                    assertThat(context.getBean(EmailService.class)).isInstanceOf(TransactionalEmailService.class);
                });
    }

    @SpringBootTest
    @ActiveProfiles("test")
    static class TestProfileFallbackVerification {

        @Autowired
        private EmailService emailService;

        @Test
        @DisplayName("Active 'test' profile selects safe redacted LoggingEmailService fallback")
        void testProfileUsesLoggingFallback() {
            assertThat(emailService).isNotNull();
            assertThat(emailService).isInstanceOf(LoggingEmailService.class);
            assertThat(emailService).isNotInstanceOf(TransactionalEmailService.class);
        }
    }
}
