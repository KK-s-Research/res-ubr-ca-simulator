package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

/**
 * Kubernetes Resource Packing (KRP) baseline.
 *
 * <p>This is an adapted Kubernetes-style resource-packing baseline rather than
 * a full reproduction of the Kubernetes scheduling framework. It follows the
 * spirit of resource-fit scoring with a most-allocated/bin-packing preference:
 * fill existing VMs before launching new ones. It does not model Kubernetes
 * plugins such as affinity, taints, topology, volumes, or multi-resource
 * scoring, and it does not reason about CPU credits, deadlines, migration, or
 * uncertainty.</p>
 */
public final class KubernetesResourcePackingPolicy
        extends AbstractSchedulingPolicy {
    public KubernetesResourcePackingPolicy() {
        super(SchedulerPolicy.KRP, false, false, false, MigrationMode.NONE);
    }

    @Override
    public double score(PlacementContext context, ExperimentConfig config) {
        return context.remainingCapacity()
                + (context.vm().tasks.isEmpty() ? 0.5 : 0.0);
    }
}
