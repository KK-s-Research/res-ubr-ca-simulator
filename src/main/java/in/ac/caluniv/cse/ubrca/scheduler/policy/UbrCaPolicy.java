package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Unified Bayesian-Robust Credit-Aware Scheduler (UBR-CA).
 *
 * <p>This is the proposed method. It learns task CPU utilization through
 * Bayesian posterior updates, uses a one-sided robust utilization bound for
 * capacity and credit feasibility, reserves CPU credits over a multi-step
 * lookahead horizon, and proactively migrates tasks away from VMs predicted to
 * become credit-critical.</p>
 */
public final class UbrCaPolicy extends AbstractSchedulingPolicy {
    public UbrCaPolicy() {
        super(SchedulerPolicy.UBR_CA, true, true, true,
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
