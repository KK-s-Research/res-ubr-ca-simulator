package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * UBR-CA ablation without Bayesian learning.
 *
 * <p>This variant preserves credit-aware placement and proactive migration but
 * replaces posterior task estimates and Bayesian uncertainty bounds with
 * deterministic profile means. It isolates the value of Bayesian uncertainty
 * learning in the full UBR-CA policy.</p>
 */
public final class UbrCaNoBayesPolicy extends AbstractSchedulingPolicy {
    public UbrCaNoBayesPolicy() {
        super(SchedulerPolicy.UBR_CA_NO_BAYES, false, false, true,
                MigrationMode.PROACTIVE);
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.incrementalCost()
                + config.creditRiskWeight() * context.creditRisk()
                + config.deadlineRiskWeight() * config.latenessPenalty()
                * context.deadlineRiskSeconds();
    }
}
