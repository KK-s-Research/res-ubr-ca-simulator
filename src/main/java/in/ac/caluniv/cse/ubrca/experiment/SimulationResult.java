package in.ac.caluniv.cse.ubrca.experiment;

import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;

import java.util.List;

public record SimulationResult(
        SchedulerPolicy policy,
        long seed,
        double cost,
        double violationRate,
        double totalLateness,
        int creditExhaustions,
        double throttledVmSeconds,
        long robustBoundEvaluations,
        long robustBoundExceedances,
        int migrations,
        double schedulingRuntimeSeconds,
        int maximumVms,
        double makespan,
        List<CreditPoint> creditTrajectory) {

    public double robustBoundExceedanceRate() {
        return robustBoundEvaluations == 0L ? 0.0
                : robustBoundExceedances / (double) robustBoundEvaluations;
    }

    public record CreditPoint(double timeSeconds, double minimum,
                              double mean, double maximum) {}
}
