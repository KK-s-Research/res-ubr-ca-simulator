package in.ac.caluniv.cse.ubrca.scheduler.policy;

import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.model.VirtualMachine;
import in.ac.caluniv.cse.ubrca.model.Workflow;

/**
 * Immutable view of a candidate placement. Scheduling policies score this
 * context; the simulator applies the lowest feasible score.
 */
public record PlacementContext(
        VirtualMachine vm,
        Task task,
        Workflow workflow,
        double timeSeconds,
        double currentMeanDemand,
        double currentWorstDemand,
        double projectedMeanDemand,
        double projectedWorstDemand,
        double selectedDemand,
        double finishEstimateSeconds,
        double remainingCapacity,
        double incrementalCost,
        double creditRisk,
        double deadlineRiskSeconds) {
}
