package in.ac.caluniv.cse.ubrca.config;

import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;

import java.nio.file.Path;

/**
 * Immutable experiment parameters. Credit quantities use vCPU-minutes and
 * durations use seconds.
 */
public record ExperimentConfig(
        double intervalSeconds,
        int lookaheadHorizon,
        double epsilon,
        double latenessPenalty,
        double creditRiskWeight,
        double deadlineRiskWeight,
        double observationVariance,
        double priorVariance,
        double minimumCredits,
        double safeCredits,
        int workflows,
        int tasksPerWorkflow,
        double deadlineFactor,
        WorkloadGenerator.Stress stress,
        long seed,
        Path outputDirectory) {

    public static ExperimentConfig defaults(Path outputDirectory) {
        return new ExperimentConfig(
                300.0, 2, 0.05, 100.0, 0.025, 0.00002,
                0.0225, 0.04, 6.0, 12.0,
                8, 90, 1.55, WorkloadGenerator.Stress.MODERATE,
                20260703L, outputDirectory);
    }

    public ExperimentConfig withSeed(long value) {
        return new ExperimentConfig(intervalSeconds, lookaheadHorizon, epsilon,
                latenessPenalty, creditRiskWeight, deadlineRiskWeight,
                observationVariance, priorVariance, minimumCredits, safeCredits,
                workflows, tasksPerWorkflow, deadlineFactor, stress, value,
                outputDirectory);
    }

    public ExperimentConfig withScale(int workflowCount, int taskCount) {
        return new ExperimentConfig(intervalSeconds, lookaheadHorizon, epsilon,
                latenessPenalty, creditRiskWeight, deadlineRiskWeight,
                observationVariance, priorVariance, minimumCredits, safeCredits,
                workflowCount, taskCount, deadlineFactor, stress, seed,
                outputDirectory);
    }

    public ExperimentConfig withDeadlineFactor(double value) {
        return new ExperimentConfig(intervalSeconds, lookaheadHorizon, epsilon,
                latenessPenalty, creditRiskWeight, deadlineRiskWeight,
                observationVariance, priorVariance, minimumCredits, safeCredits,
                workflows, tasksPerWorkflow, value, stress, seed, outputDirectory);
    }

    public ExperimentConfig withSensitivity(
            double interval, int horizon, double risk, double lambda) {
        return new ExperimentConfig(interval, horizon, risk, lambda,
                creditRiskWeight, deadlineRiskWeight, observationVariance,
                priorVariance, minimumCredits, safeCredits, workflows,
                tasksPerWorkflow, deadlineFactor, stress, seed, outputDirectory);
    }

    public ExperimentConfig withStress(WorkloadGenerator.Stress value) {
        return new ExperimentConfig(intervalSeconds, lookaheadHorizon, epsilon,
                latenessPenalty, creditRiskWeight, deadlineRiskWeight,
                observationVariance, priorVariance, minimumCredits, safeCredits,
                workflows, tasksPerWorkflow, deadlineFactor, value, seed,
                outputDirectory);
    }
}
