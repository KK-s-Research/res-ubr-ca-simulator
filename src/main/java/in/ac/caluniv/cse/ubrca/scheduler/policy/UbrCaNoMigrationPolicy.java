package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * UBR-CA ablation without migration.
 *
 * <p>This variant keeps Bayesian posterior learning, robust capacity checks,
 * and multi-step credit-feasible placement, but disables credit-aware recovery
 * migration. It isolates the contribution of proactive migration.</p>
 */
public final class UbrCaNoMigrationPolicy extends AbstractSchedulingPolicy {
    public UbrCaNoMigrationPolicy() {
        super(SchedulerPolicy.UBR_CA_NO_MIGRATION, true, true, true,
                MigrationMode.NONE);
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.incrementalCost()
                + config.creditRiskWeight() * context.creditRisk()
                + config.deadlineRiskWeight() * config.latenessPenalty()
                * context.deadlineRiskSeconds();
    }
}
