package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Branch-and-bound proxy baseline.
 *
 * <p>This policy is a strict-feasibility reference for robust credit-aware
 * placement. It applies Bayesian estimates, robust capacity checks, credit
 * feasibility, and proactive recovery, but uses a more cost-centered score.
 * It is a proxy for small-instance strict feasibility, not a claim of global
 * large-scale mixed-integer optimality.</p>
 */
public final class BranchAndBoundProxyPolicy extends AbstractSchedulingPolicy {
    public BranchAndBoundProxyPolicy() {
        super(SchedulerPolicy.BNB, true, true, true, MigrationMode.PROACTIVE);
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.incrementalCost()
                + 0.5 * config.creditRiskWeight() * context.creditRisk()
                + config.deadlineRiskWeight() * config.latenessPenalty()
                * context.deadlineRiskSeconds();
    }
}
