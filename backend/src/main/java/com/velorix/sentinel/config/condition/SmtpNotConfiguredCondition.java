package com.velorix.sentinel.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches when SMTP transactional email delivery is not configured,
 * activating the redacted logging fallback.
 */
public class SmtpNotConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String host = context.getEnvironment().getProperty("spring.mail.host");
        return host == null || host.trim().isEmpty();
    }
}
