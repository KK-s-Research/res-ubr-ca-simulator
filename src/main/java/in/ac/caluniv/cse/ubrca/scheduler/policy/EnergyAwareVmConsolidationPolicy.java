package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Energy-Aware VM Consolidation (EA-VC) baseline.
 *
 * <p>This is an adapted consolidation baseline, not a full implementation of a
 * dynamic energy-aware cloud controller. Original VM-consolidation systems
 * commonly include host overload/underload detection, VM selection, live
 * migration, and host power-state management. This simulator-level abstraction
 * captures the placement-side idea by aggressively reusing active VMs to reduce
 * fragmentation. It treats CPU capacity as continuously available and does not
 * model burstable-instance credit depletion.</p>
 */
public final class EnergyAwareVmConsolidationPolicy
        extends AbstractSchedulingPolicy {
    public EnergyAwareVmConsolidationPolicy() {
        super(SchedulerPolicy.EAVC, false, false, false, MigrationMode.NONE);
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.remainingCapacity() * 0.5
                + (context.vm().tasks.isEmpty() ? 10.0 : 0.0);
    }
}
