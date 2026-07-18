package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Chance-Constrained Scheduler (CCS) baseline.
 *
 * <p>This is an adapted chance-constrained capacity baseline. It protects
 * instantaneous VM capacity using a fixed variance term and the simulator's
 * one-sided risk parameter. It is not a complete implementation of submodular
 * bin-packing or provider-side overcommitment optimization. Unlike UBR-CA, it
 * does not learn task-specific posterior uncertainty online and does not
 * enforce multi-step CPU-credit feasibility.</p>
 */
public final class ChanceConstrainedPolicy extends AbstractSchedulingPolicy {
    public ChanceConstrainedPolicy() {
        super(SchedulerPolicy.CCS, false, true, false, MigrationMode.NONE);
    }

    @Override
    public double utilizationVariance(Task task, ExperimentConfig config) {
        return config.priorVariance();
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.remainingCapacity()
                + (context.vm().tasks.isEmpty() ? 0.5 : 0.0);
    }
}
