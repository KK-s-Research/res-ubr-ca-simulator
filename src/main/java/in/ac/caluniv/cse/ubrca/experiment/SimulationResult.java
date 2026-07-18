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
        int migrations,
        double schedulingRuntimeSeconds,
        int maximumVms,
        double makespan,
        List<CreditPoint> creditTrajectory) {

    public record CreditPoint(double timeSeconds, double minimum,
                              double mean, double maximum) {}
}
