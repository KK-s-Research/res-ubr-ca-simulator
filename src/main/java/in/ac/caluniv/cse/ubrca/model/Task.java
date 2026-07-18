package in.ac.caluniv.cse.ubrca.model;

import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;

import java.util.ArrayList;
import java.util.List;

public final class Task {
    public enum State { WAITING, READY, RUNNING, FINISHED }

    public final int id;
    public final int workflowId;
    public final List<Integer> predecessors;
    public final double durationSeconds;
    public final double profileMean;
    public final WorkloadGenerator.Pattern pattern;
    public final double stateSizeGb;
    public final double criticality;

    public State state = State.WAITING;
    public double remainingSeconds;
    public double startTime = Double.NaN;
    public double completionTime = Double.NaN;
    public int vmId = -1;
    public int samples;
    public double sampleSum;
    public double posteriorMean;
    public double posteriorVariance;
    public double lastObservation;
    public double lastMigrationTime = Double.NEGATIVE_INFINITY;

    public Task(int id, int workflowId, List<Integer> predecessors,
                double durationSeconds, double profileMean,
                WorkloadGenerator.Pattern pattern, double stateSizeGb,
                double criticality, double priorVariance) {
        this.id = id;
        this.workflowId = workflowId;
        this.predecessors = List.copyOf(predecessors);
        this.durationSeconds = durationSeconds;
        this.profileMean = profileMean;
        this.pattern = pattern;
        this.stateSizeGb = stateSizeGb;
        this.criticality = criticality;
        this.remainingSeconds = durationSeconds;
        this.posteriorMean = profileMean;
        this.posteriorVariance = priorVariance;
    }

    public Task copy(double priorVariance) {
        return new Task(id, workflowId, new ArrayList<>(predecessors),
                durationSeconds, profileMean, pattern, stateSizeGb, criticality,
                priorVariance);
    }

    public double progress() {
        return Math.max(0.0, Math.min(1.0,
                1.0 - remainingSeconds / Math.max(1.0, durationSeconds)));
    }
}
