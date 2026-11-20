package com.velorix.sentinel.exception;

import java.util.List;

/**
 * Thrown during application startup when production profile validation fails.
 *
 * <p>Enforces fail-fast startup behavior if required production secrets or operational
 * parameters are missing, malformed, or using insecure development defaults.
 * Crucially, error messages are sanitized and NEVER print raw secret values.</p>
 */
public class ProductionConfigurationException extends IllegalStateException {

    private final List<String> violations;

    public ProductionConfigurationException(List<String> violations) {
        super(formatMessage(violations));
        this.violations = violations != null ? List.copyOf(violations) : List.of();
    }

    public List<String> getViolations() {
        return violations;
    }

    private static String formatMessage(List<String> violations) {
        if (violations == null || violations.isEmpty()) {
            return "Production configuration validation failed with unspecified violations.";
        }
        StringBuilder sb = new StringBuilder("Production configuration validation failed with ")
                .append(violations.size())
                .append(" violation(s):\n");
        for (String violation : violations) {
            sb.append("  - ").append(violation).append("\n");
        }
        sb.append("Application startup halted to prevent running in production with insecure or invalid configuration.");
        return sb.toString();
    }
}

