package in.ac.caluniv.cse.ubrca.experiment;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Workflow;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.Simulator;
import in.ac.caluniv.cse.ubrca.statistics.Statistics;
import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExperimentRunner {
    public static final List<SchedulerPolicy> MANUSCRIPT_POLICIES = List.of(
            SchedulerPolicy.HEFT, SchedulerPolicy.KRP, SchedulerPolicy.EAVC,
            SchedulerPolicy.CCS, SchedulerPolicy.CARS, SchedulerPolicy.UBR_CA);

    public record SensitivityPoint(String parameter, String value,
                                   Statistics.Summary cost,
                                   Statistics.Summary violationRate,
                                   Statistics.Summary exhaustions,
                                   Statistics.Summary throttledVmHours,
                                   Statistics.Summary boundExceedanceRate,
                                   List<SimulationResult> runs) {}

    public record ScalePoint(int tasks, Statistics.Summary runtimeSeconds,
                             List<Double> runs) {}

    public record ExperimentBundle(
            ExperimentConfig config,
            int repetitions,
            String workloadSource,
            List<SimulationResult> overall,
            List<SimulationResult> ablations,
            List<SimulationResult> stress,
            List<SensitivityPoint> sensitivity,
            List<ScalePoint> scalability) {}

    private final ExperimentConfig config;
    private final int repetitions;
    private final boolean quick;
    private final Path traceCsv;

    public ExperimentRunner(ExperimentConfig config, int repetitions,
                            boolean quick, Path traceCsv) {
        this.config = config;
        this.repetitions = repetitions;
        this.quick = quick;
        this.traceCsv = traceCsv;
    }

    public ExperimentBundle run() throws IOException {
        String source = traceCsv == null
                ? "benchmark-shaped synthetic DAGs with step, ramp, bursty, and trace-driven utilization"
                : "imported trace: " + traceCsv.toAbsolutePath();
        System.out.printf("Overall comparison: %d policies x %d seeds%n",
                MANUSCRIPT_POLICIES.size(), repetitions);
        List<SimulationResult> overall = runPolicies(config, MANUSCRIPT_POLICIES,
                repetitions, traceCsv);

        System.out.printf("Ablation study: 2 variants x %d seeds%n", repetitions);
        List<SimulationResult> ablations = runPolicies(config,
                List.of(SchedulerPolicy.UBR_CA_NO_MIGRATION,
                        SchedulerPolicy.UBR_CA_NO_BAYES),
                repetitions, traceCsv);

        int secondaryRepetitions = quick ? Math.min(2, repetitions) : repetitions;
        List<SimulationResult> stress = new ArrayList<>();
        if (traceCsv == null) {
            System.out.printf("Credit stress study: 3 levels x 6 policies x %d seeds%n",
                    secondaryRepetitions);
            List<SchedulerPolicy> stressPolicies = List.of(SchedulerPolicy.HEFT,
                    SchedulerPolicy.KRP, SchedulerPolicy.CCS, SchedulerPolicy.CARS,
                    SchedulerPolicy.UBR_CA_NO_MIGRATION, SchedulerPolicy.UBR_CA);
            for (WorkloadGenerator.Stress level : WorkloadGenerator.Stress.values()) {
                stress.addAll(runPolicies(config.withStress(level), stressPolicies,
                        secondaryRepetitions, null));
            }
        }

        System.out.println("Parameter sensitivity study");
        List<SensitivityPoint> sensitivity = runSensitivity(secondaryRepetitions);
        System.out.println("Scalability study");
        List<ScalePoint> scalability = runScalability();
        return new ExperimentBundle(config, repetitions, source, overall, ablations,
                stress, sensitivity, scalability);
    }

    private List<SimulationResult> runPolicies(ExperimentConfig base,
                                               List<SchedulerPolicy> policies,
                                               int count, Path importedTrace)
            throws IOException {
        List<SimulationResult> results = new ArrayList<>();
        for (int repetition = 0; repetition < count; repetition++) {
            ExperimentConfig seeded = base.withSeed(base.seed() + repetition * 7_919L);
            List<Workflow> templates = importedTrace == null
                    ? WorkloadGenerator.generate(seeded)
                    : WorkloadGenerator.importCsv(importedTrace, seeded);
            int taskCount = templates.stream().mapToInt(w -> w.tasks.size()).sum();
            System.out.printf("  seed %2d/%-2d value=%d workflows=%d tasks=%d "
                            + "stress=%s interval=%.0fs H=%d epsilon=%.2f lambda=%.0f%n",
                    repetition + 1, count, seeded.seed(), templates.size(), taskCount,
                    seeded.stress(), seeded.intervalSeconds(),
                    seeded.lookaheadHorizon(), seeded.epsilon(),
                    seeded.latenessPenalty());
            for (SchedulerPolicy policy : policies) {
                SimulationResult result = new Simulator(seeded, policy, templates).run();
                results.add(result);
                System.out.printf("    %-24s cost=%8.3f USD  violations=%6.2f%%  "
                                + "lateness=%10.1f s  exhaustions=%3d  throttled=%7.3f VM-h  bound-exceed=%6.2f%%  migrations=%3d  "
                                + "runtime=%8.4f s  maxVMs=%3d%n",
                        policy.label(), result.cost(), result.violationRate() * 100.0,
                        result.totalLateness(), result.creditExhaustions(),
                        result.throttledVmSeconds() / 3_600.0,
                        result.robustBoundExceedanceRate() * 100.0, result.migrations(), result.schedulingRuntimeSeconds(),
                        result.maximumVms());
            }
            if ((repetition + 1) % Math.max(1, count / 4) == 0
                    || repetition + 1 == count) {
                System.out.printf("  completed seed %d/%d%n", repetition + 1, count);
            }
        }
        return results;
    }

    private List<SensitivityPoint> runSensitivity(int count) throws IOException {
        List<SensitivityPoint> points = new ArrayList<>();
        Map<String, List<ExperimentConfig>> configurations = new LinkedHashMap<>();
        configurations.put("interval_seconds", List.of(
                config.withSensitivity(60, config.lookaheadHorizon(), config.epsilon(),
                        config.latenessPenalty()),
                config.withSensitivity(300, config.lookaheadHorizon(), config.epsilon(),
                        config.latenessPenalty()),
                config.withSensitivity(900, config.lookaheadHorizon(), config.epsilon(),
                        config.latenessPenalty())));
        configurations.put("lookahead_H", List.of(
                config.withSensitivity(config.intervalSeconds(), 1, config.epsilon(),
                        config.latenessPenalty()),
                config.withSensitivity(config.intervalSeconds(), 2, config.epsilon(),
                        config.latenessPenalty()),
                config.withSensitivity(config.intervalSeconds(), 4, config.epsilon(),
                        config.latenessPenalty())));
        configurations.put("epsilon", List.of(
                config.withSensitivity(config.intervalSeconds(), config.lookaheadHorizon(),
                        0.01, config.latenessPenalty()),
                config.withSensitivity(config.intervalSeconds(), config.lookaheadHorizon(),
                        0.05, config.latenessPenalty()),
                config.withSensitivity(config.intervalSeconds(), config.lookaheadHorizon(),
                        0.10, config.latenessPenalty())));
        configurations.put("lateness_lambda", List.of(
                config.withSensitivity(config.intervalSeconds(), config.lookaheadHorizon(),
                        config.epsilon(), 10),
                config.withSensitivity(config.intervalSeconds(), config.lookaheadHorizon(),
                        config.epsilon(), 100),
                config.withSensitivity(config.intervalSeconds(), config.lookaheadHorizon(),
                        config.epsilon(), 1_000)));

        for (Map.Entry<String, List<ExperimentConfig>> entry : configurations.entrySet()) {
            for (ExperimentConfig variant : entry.getValue()) {
                List<SimulationResult> values = runPolicies(variant,
                        List.of(SchedulerPolicy.UBR_CA), count, traceCsv);
                String value = switch (entry.getKey()) {
                    case "interval_seconds" -> "%.0f".formatted(variant.intervalSeconds());
                    case "lookahead_H" -> "%d".formatted(variant.lookaheadHorizon());
                    case "epsilon" -> "%.2f".formatted(variant.epsilon());
                    default -> "%.0f".formatted(variant.latenessPenalty());
                };
                points.add(new SensitivityPoint(entry.getKey(), value,
                        summary(values, Metric.COST),
                        summary(values, Metric.VIOLATION),
                        summary(values, Metric.EXHAUSTIONS),
                        summary(values, Metric.THROTTLED_VM_HOURS),
                        summary(values, Metric.BOUND_EXCEEDANCE),
                        List.copyOf(values)));
            }
        }
        return points;
    }

    public List<ScalePoint> runScalability() {
        int[] sizes = quick
                ? new int[]{50, 200, 500, 2_000}
                : new int[]{50, 200, 500, 2_000, 5_000, 10_000, 50_000};
        int scaleRepetitions = quick ? 3 : 10;
        int warmupRepetitions = quick ? 1 : 3;
        List<ScalePoint> points = new ArrayList<>();
        for (int size : sizes) {
            for (int warmup = 0; warmup < warmupRepetitions; warmup++) {
                ExperimentConfig scaled = config.withScale(1, size)
                        .withSeed(config.seed() - size - warmup * 7_919L);
                List<Workflow> workflow = WorkloadGenerator.generate(scaled);
                new Simulator(scaled, SchedulerPolicy.UBR_CA, workflow).run();
            }
            List<Double> runtimes = new ArrayList<>();
            for (int repetition = 0; repetition < scaleRepetitions; repetition++) {
                ExperimentConfig scaled = config.withScale(1, size)
                        .withSeed(config.seed() + size + repetition * 7_919L);
                List<Workflow> workflow = WorkloadGenerator.generate(scaled);
                SimulationResult result = new Simulator(scaled,
                        SchedulerPolicy.UBR_CA, workflow).run();
                runtimes.add(result.schedulingRuntimeSeconds());
            }
            Statistics.Summary runtime = Statistics.summarize(runtimes);
            points.add(new ScalePoint(size, runtime, List.copyOf(runtimes)));
            System.out.printf("  %,d tasks: %.4f +/- %.4f s scheduler CPU time (%d runs)%n",
                    size, runtime.mean(), runtime.standardDeviation(), scaleRepetitions);
        }
        return points;
    }

    private enum Metric { COST, VIOLATION, EXHAUSTIONS, THROTTLED_VM_HOURS, BOUND_EXCEEDANCE }

    private static Statistics.Summary summary(List<SimulationResult> results,
                                              Metric metric) {
        List<Double> values = results.stream().map(result -> switch (metric) {
            case COST -> result.cost();
            case VIOLATION -> result.violationRate();
            case EXHAUSTIONS -> (double) result.creditExhaustions();
            case THROTTLED_VM_HOURS -> result.throttledVmSeconds() / 3_600.0;
            case BOUND_EXCEEDANCE -> result.robustBoundExceedanceRate();
        }).toList();
        return Statistics.summarize(values);
    }

    public static Map<SchedulerPolicy, List<SimulationResult>> byPolicy(
            List<SimulationResult> results) {
        Map<SchedulerPolicy, List<SimulationResult>> grouped =
                new EnumMap<>(SchedulerPolicy.class);
        for (SimulationResult result : results) {
            grouped.computeIfAbsent(result.policy(), ignored -> new ArrayList<>())
                    .add(result);
        }
        return grouped;
    }
}
