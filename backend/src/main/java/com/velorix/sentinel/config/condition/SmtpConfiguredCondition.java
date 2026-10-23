package com.velorix.sentinel.config.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches when SMTP transactional email delivery is configured with a non-blank host.
 */
public class SmtpConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String host = context.getEnvironment().getProperty("spring.mail.host");
        return host != null && !host.trim().isEmpty();
    }
}
