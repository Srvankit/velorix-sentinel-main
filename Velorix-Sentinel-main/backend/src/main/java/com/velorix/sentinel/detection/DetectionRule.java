package com.velorix.sentinel.detection;

/**
 * Strategy contract for a detection rule.
 *
 * <p>Adding a rule means adding one Spring bean implementing this interface — the engine
 * discovers it, the catalogue endpoint publishes it and the configuration can disable it,
 * all with no change to existing code.</p>
 */
public interface DetectionRule {

    /** Static description; must be constant for the lifetime of the bean. */
    RuleMetadata metadata();

    /** Creates the per-run, single threaded evaluator for this rule. */
    RuleEvaluator newEvaluator(DetectionContext context);
}
