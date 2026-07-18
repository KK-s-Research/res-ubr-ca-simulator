package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Base class for named scheduling strategies.
 */
abstract class AbstractSchedulingPolicy implements SchedulingPolicy {
    private final SchedulerPolicy id;
    private final boolean bayesian;
    private final boolean robustCapacity;
    private final boolean creditFeasibility;
    private final MigrationMode migrationMode;

    AbstractSchedulingPolicy(SchedulerPolicy id, boolean bayesian,
                             boolean robustCapacity, boolean creditFeasibility,
                             MigrationMode migrationMode) {
        this.id = id;
        this.bayesian = bayesian;
        this.robustCapacity = robustCapacity;
        this.creditFeasibility = creditFeasibility;
        this.migrationMode = migrationMode;
    }

    @Override
    public SchedulerPolicy id() {
        return id;
    }

    @Override
    public boolean usesBayesianEstimation() {
        return bayesian;
    }

    @Override
    public boolean usesRobustCapacity() {
        return robustCapacity;
    }

    @Override
    public boolean enforcesCreditFeasibility() {
        return creditFeasibility;
    }

    @Override
    public MigrationMode migrationMode() {
        return migrationMode;
    }
}
