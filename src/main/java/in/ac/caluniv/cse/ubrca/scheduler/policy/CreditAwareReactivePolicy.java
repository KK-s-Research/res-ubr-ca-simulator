package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Credit-Aware Reactive Scheduler (CARS) baseline.
 *
 * <p>This is an adapted reactive credit-aware baseline. It borrows the central
 * idea from burst-credit-aware schedulers: monitor available burst credits and
 * avoid clearly unsafe placements. Unlike predictive systems such as CEDULE+
 * or full platform schedulers such as CASH, this simulator abstraction uses a
 * simple credit-risk score and performs migration only after a VM becomes
 * credit-critical or has already exhausted credits.</p>
 */
public final class CreditAwareReactivePolicy extends AbstractSchedulingPolicy {
    public CreditAwareReactivePolicy() {
        super(SchedulerPolicy.CARS, false, false, true, MigrationMode.REACTIVE);
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.incrementalCost()
                + config.creditRiskWeight() * context.creditRisk();
    }
}
