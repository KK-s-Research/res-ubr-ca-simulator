package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * HEFT baseline.
 *
 * <p>This policy represents an adaptation of the original HEFT algorithm to
 * the simulator's online burstable-VM setting. Ready tasks are ordered by the
 * workflow upward rank computed by the workload generator. Because the
 * simulator does not model inter-VM communication costs, the upward rank uses
 * average task execution time only. Processor selection then minimizes an
 * earliest-finish-time score under nominal CPU demand. The policy deliberately
 * ignores CPU-credit dynamics and uncertainty.</p>
 */
public final class HeftPolicy extends AbstractSchedulingPolicy {
    public HeftPolicy() {
        super(SchedulerPolicy.HEFT, false, false, false, MigrationMode.NONE);
    }

    @Override
    public double readyPriority(Task task) {
        return task.criticality;
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.finishEstimateSeconds()
                + context.currentMeanDemand() * 600.0;
    }
}
