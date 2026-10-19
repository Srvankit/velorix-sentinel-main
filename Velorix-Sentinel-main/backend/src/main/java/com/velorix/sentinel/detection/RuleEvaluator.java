package com.velorix.sentinel.detection;

import java.util.List;

/**
 * Per-run state of a single rule.
 *
 * <p>The engine makes one pass over the capture and hands every frame to every evaluator,
 * then asks each for its findings. An evaluator is stateful and single threaded: one
 * instance is created per run, never shared, and discarded afterwards.</p>
 */
public interface RuleEvaluator {

    /** Called once per frame, in capture order. Must not retain the record itself. */
    void accept(PacketRecord packet);

    /** Called after the last frame; returns the findings accumulated during the pass. */
    List<ThreatCandidate> finish();
}
