package com.velorix.sentinel.detection;

import com.velorix.sentinel.config.properties.DetectionProperties;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Catalogue of every detection rule known to the application.
 *
 * <p>Rules are Spring beans, so the registry is populated by component scanning: dropping
 * a new {@link DetectionRule} into the {@code rules} package is the entire integration
 * step. Configuration can silence a noisy rule without a redeploy of the detection
 * logic.</p>
 */
@Component
public class DetectionRuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(DetectionRuleRegistry.class);

    private final Map<String, DetectionRule> byId = new LinkedHashMap<>();
    private final List<DetectionRule> enabled;
    private final DetectionProperties properties;

    public DetectionRuleRegistry(List<DetectionRule> rules, DetectionProperties properties) {
        this.properties = properties;
        rules.stream()
                .sorted(Comparator.comparing(rule -> rule.metadata().id()))
                .forEach(rule -> {
                    DetectionRule clash = byId.putIfAbsent(rule.metadata().id(), rule);
                    if (clash != null) {
                        throw new IllegalStateException(
                                "Duplicate detection rule id: " + rule.metadata().id());
                    }
                });
        this.enabled = byId.values().stream()
                .filter(rule -> !properties.disabledRulesOrEmpty().contains(rule.metadata().id()))
                .toList();
        log.info("Detection registry initialised with {} rules ({} enabled, engine {})",
                byId.size(), enabled.size(), properties.engineVersion());
    }

    /** Rules that will actually run, in stable order. */
    public List<DetectionRule> enabledRules() {
        return enabled;
    }

    /** Every rule, including disabled ones, for the catalogue endpoint. */
    public List<DetectionRule> allRules() {
        return List.copyOf(byId.values());
    }

    public boolean isEnabled(String ruleId) {
        return !properties.disabledRulesOrEmpty().contains(ruleId);
    }

    public Optional<DetectionRule> find(String ruleId) {
        return Optional.ofNullable(byId.get(ruleId));
    }
}
