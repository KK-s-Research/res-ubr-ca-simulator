package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Strategy interface for workflow scheduling policies.
 *
 * <p>The simulator is responsible for common mechanics such as VM launch,
 * task execution, credit accounting, and workflow completion metrics. Each
 * policy defines only the conceptual scheduling decisions: the demand estimate
 * it trusts, whether it enforces robust capacity and credit feasibility, how it
 * scores candidate placements, and whether migration is disabled, reactive, or
 * proactive.</p>
 */
public interface SchedulingPolicy {
    /** Stable experiment identifier used in CSV files, tables, and figures. */
    SchedulerPolicy id();

    /** Human-readable policy name used in result artifacts. */
    default String label() {
        return id().label();
    }

    /**
     * Whether the policy uses online Bayesian posterior means instead of static
     * profile means.
     */
    boolean usesBayesianEstimation();

    /**
     * Whether capacity checks use a one-sided uncertainty bound instead of only
     * the mean utilization estimate.
     */
    boolean usesRobustCapacity();

    /**
     * Whether placement must reserve enough CPU credits over the configured
     * lookahead horizon.
     */
    boolean enforcesCreditFeasibility();

    /** Migration rule used after placement. */
    MigrationMode migrationMode();

    /** Score a feasible or infeasible placement candidate. Lower is better. */
    double score(PlacementContext context, ExperimentConfig config);

    /**
     * Priority used to order ready tasks. Higher values are scheduled first.
     *
     * <p>The default is the workflow upward-rank/criticality value computed by
     * the workload loader. HEFT explicitly relies on this value, while the
     * other baselines inherit it as a common precedence-aware ready queue.</p>
     */
    default double readyPriority(Task task) {
        return task.criticality;
    }

    /** Utilization mean used by this policy for a task. */
    default double utilizationMean(Task task) {
        return usesBayesianEstimation() ? task.posteriorMean : task.profileMean;
    }

    /** Utilization variance used to construct robust capacity bounds. */
    default double utilizationVariance(Task task, ExperimentConfig config) {
        return usesBayesianEstimation()
                ? task.posteriorVariance + config.observationVariance()
                : 0.0;
    }
}
