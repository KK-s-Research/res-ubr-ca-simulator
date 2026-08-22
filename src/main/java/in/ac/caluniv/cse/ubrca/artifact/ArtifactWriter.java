package in.ac.caluniv.cse.ubrca.artifact;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.experiment.ExperimentRunner;
import in.ac.caluniv.cse.ubrca.experiment.SimulationResult;
import in.ac.caluniv.cse.ubrca.scheduler.BayesianEstimator;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;
import in.ac.caluniv.cse.ubrca.statistics.Statistics;
import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class ArtifactWriter {
    private final Path output;
    private final Path raw;
    private final Path tables;
    private final Path figures;

    public ArtifactWriter(Path output) {
        this.output = output;
        this.raw = output.resolve("raw");
        this.tables = output.resolve("tables");
        this.figures = output.resolve("figures");
    }

    public void write(ExperimentRunner.ExperimentBundle bundle) throws IOException {
        Files.createDirectories(raw);
        Files.createDirectories(tables);
        Files.createDirectories(figures);
        System.out.println("Writing publication artifacts");
        System.out.println("  configuration: experiment_config.json");
        writeConfig(bundle);
        System.out.println("  raw CSV files");
        writeRaw("overall_results.csv", bundle.overall(), "");
        writeRaw("ablation_results.csv", bundle.ablations(), "");
        writeStressRaw(bundle.stress());
        writeSensitivity(bundle.sensitivity());
        writeScalability(bundle.scalability());
        writeCreditTrajectory(bundle);
        PythonPlotScriptWriter.write(output);
        System.out.println("  manuscript tables");
        writeTables(bundle);
        System.out.println("  manuscript figures");
        writeFigures(bundle);
        System.out.println("  summary and manuscript guide");
        writeSummary(bundle);
        writeManuscriptGuide(bundle);
    }

    public void writeScalabilityStudy(List<ExperimentRunner.ScalePoint> points)
            throws IOException {
        Files.createDirectories(raw);
        writeScalability(points);
    }

    private void writeConfig(ExperimentRunner.ExperimentBundle bundle) throws IOException {
        ExperimentConfig c = bundle.config();
        String json = """
                {
                  "generated_at": "%s",
                  "workload_source": "%s",
                  "repetitions": %d,
                  "java_version": "%s",
                  "interval_seconds": %.1f,
                  "lookahead_horizon": %d,
                  "epsilon": %.4f,
                  "confidence_level": %.4f,
                  "lateness_lambda": %.1f,
                  "observation_variance": %.6f,
                  "prior_variance": %.6f,
                  "minimum_credits": %.3f,
                  "safe_credits": %.3f,
                  "workflows_per_run": %d,
                  "nominal_tasks_per_workflow": %d,
                  "deadline_factor": %.3f,
                  "default_stress": "%s"
                }
                """.formatted(OffsetDateTime.now(), escapeJson(bundle.workloadSource()),
                bundle.repetitions(), escapeJson(System.getProperty("java.version")),
                c.intervalSeconds(), c.lookaheadHorizon(), c.epsilon(),
                1.0 - c.epsilon(), c.latenessPenalty(), c.observationVariance(),
                c.priorVariance(), c.minimumCredits(), c.safeCredits(),
                c.workflows(), c.tasksPerWorkflow(), c.deadlineFactor(), c.stress());
        Files.writeString(output.resolve("experiment_config.json"), json,
                StandardCharsets.UTF_8);
    }

    private void writeRaw(String name, List<SimulationResult> results, String note)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "policy,seed,cost_usd,violation_rate,total_lateness_seconds,"
                        + "credit_exhaustions,throttled_vm_hours,robust_bound_evaluations,robust_bound_exceedances,bound_exceedance_rate,migrations,scheduler_runtime_seconds,"
                        + "maximum_vms,makespan_seconds,note\n");
        for (SimulationResult r : results) {
            csv.append(csv(r.policy().label())).append(',').append(r.seed()).append(',')
                    .append(f(r.cost())).append(',').append(f(r.violationRate())).append(',')
                    .append(f(r.totalLateness())).append(',')
                    .append(r.creditExhaustions()).append(',').append(f(r.throttledVmSeconds() / 3_600.0)).append(',')
                    .append(r.robustBoundEvaluations()).append(',')
                    .append(r.robustBoundExceedances()).append(',')
                    .append(f(r.robustBoundExceedanceRate())).append(',').append(r.migrations())
                    .append(',').append(f(r.schedulingRuntimeSeconds())).append(',')
                    .append(r.maximumVms()).append(',').append(f(r.makespan()))
                    .append(',').append(csv(note)).append('\n');
        }
        Files.writeString(raw.resolve(name), csv, StandardCharsets.UTF_8);
    }

    private void writeSensitivity(List<ExperimentRunner.SensitivityPoint> points)
            throws IOException {
        StringBuilder summary = new StringBuilder("parameter,value,cost_mean,cost_sd,"
                + "violation_rate_mean,violation_rate_sd,exhaustions_mean,exhaustions_sd,throttled_vm_hours_mean,throttled_vm_hours_sd,bound_exceedance_rate_mean,bound_exceedance_rate_sd\n");
        StringBuilder rawRuns = new StringBuilder("parameter,value,seed,cost_usd,"
                + "violation_rate,total_lateness_seconds,credit_exhaustions,throttled_vm_hours,robust_bound_evaluations,robust_bound_exceedances,bound_exceedance_rate,"
                + "migrations,scheduler_runtime_seconds,maximum_vms,makespan_seconds\n");
        for (ExperimentRunner.SensitivityPoint p : points) {
            summary.append(p.parameter()).append(',').append(p.value()).append(',')
                    .append(f(p.cost().mean())).append(',').append(f(p.cost().standardDeviation()))
                    .append(',').append(f(p.violationRate().mean())).append(',')
                    .append(f(p.violationRate().standardDeviation())).append(',')
                    .append(f(p.exhaustions().mean())).append(',')
                    .append(f(p.exhaustions().standardDeviation())).append(',')
                    .append(f(p.throttledVmHours().mean())).append(',')
                    .append(f(p.throttledVmHours().standardDeviation())).append(',')
                    .append(f(p.boundExceedanceRate().mean())).append(',')
                    .append(f(p.boundExceedanceRate().standardDeviation())).append('\n');
            for (SimulationResult r : p.runs()) {
                rawRuns.append(p.parameter()).append(',').append(p.value()).append(',')
                        .append(r.seed()).append(',').append(f(r.cost())).append(',')
                        .append(f(r.violationRate())).append(',')
                        .append(f(r.totalLateness())).append(',')
                        .append(r.creditExhaustions()).append(',')
                        .append(f(r.throttledVmSeconds() / 3_600.0)).append(',')
                        .append(r.robustBoundEvaluations()).append(',')
                        .append(r.robustBoundExceedances()).append(',')
                        .append(f(r.robustBoundExceedanceRate())).append(',')
                        .append(r.migrations()).append(',')
                        .append(f(r.schedulingRuntimeSeconds())).append(',')
                        .append(r.maximumVms()).append(',').append(f(r.makespan()))
                        .append('\n');
            }
        }
        Files.writeString(raw.resolve("sensitivity_results.csv"), rawRuns,
                StandardCharsets.UTF_8);
        Files.writeString(raw.resolve("sensitivity_summary.csv"), summary,
                StandardCharsets.UTF_8);
    }

    private void writeStressRaw(List<SimulationResult> results) throws IOException {
        StringBuilder csv = new StringBuilder("stress,policy,seed,cost_usd,"
                + "violation_rate,total_lateness_seconds,credit_exhaustions,throttled_vm_hours,robust_bound_evaluations,robust_bound_exceedances,bound_exceedance_rate,"
                + "migrations,scheduler_runtime_seconds,maximum_vms,makespan_seconds\n");
        if (!results.isEmpty()) {
            int perStress = results.size() / WorkloadGenerator.Stress.values().length;
            for (int s = 0; s < WorkloadGenerator.Stress.values().length; s++) {
                for (SimulationResult r : results.subList(s * perStress,
                        (s + 1) * perStress)) {
                    csv.append(WorkloadGenerator.Stress.values()[s]).append(',')
                            .append(csv(r.policy().label())).append(',')
                            .append(r.seed()).append(',').append(f(r.cost())).append(',')
                            .append(f(r.violationRate())).append(',')
                            .append(f(r.totalLateness())).append(',')
                            .append(r.creditExhaustions()).append(',')
                            .append(f(r.throttledVmSeconds() / 3_600.0)).append(',')
                            .append(r.robustBoundEvaluations()).append(',')
                            .append(r.robustBoundExceedances()).append(',')
                            .append(f(r.robustBoundExceedanceRate())).append(',')
                            .append(r.migrations()).append(',')
                            .append(f(r.schedulingRuntimeSeconds())).append(',')
                            .append(r.maximumVms()).append(',')
                            .append(f(r.makespan())).append('\n');
                }
            }
        }
        Files.writeString(raw.resolve("stress_results.csv"), csv,
                StandardCharsets.UTF_8);
    }

    private void writeScalability(List<ExperimentRunner.ScalePoint> points)
            throws IOException {
        StringBuilder csv = new StringBuilder(
                "tasks,repetitions,scheduler_runtime_mean_seconds,scheduler_runtime_sd_seconds\n");
        for (ExperimentRunner.ScalePoint p : points) {
            csv.append(p.tasks()).append(',').append(p.runs().size()).append(',')
                    .append(f(p.runtimeSeconds().mean())).append(',')
                    .append(f(p.runtimeSeconds().standardDeviation())).append('\n');
        }
        Files.writeString(raw.resolve("scalability_results.csv"), csv,
                StandardCharsets.UTF_8);
    }

    private void writeCreditTrajectory(ExperimentRunner.ExperimentBundle bundle)
            throws IOException {
        Map<SchedulerPolicy, List<SimulationResult>> grouped =
                ExperimentRunner.byPolicy(bundle.overall());
        List<SimulationResult> ubr = grouped.get(SchedulerPolicy.UBR_CA);
        if (ubr == null || ubr.isEmpty()) return;
        SimulationResult representative = ubr.get(0);
        StringBuilder csv = new StringBuilder(
                "policy,seed,time_seconds,time_hours,minimum,mean,maximum\n");
        for (SimulationResult.CreditPoint point : representative.creditTrajectory()) {
            csv.append(csv(representative.policy().label())).append(',')
                    .append(representative.seed()).append(',')
                    .append(f(point.timeSeconds())).append(',')
                    .append(f(point.timeSeconds() / 3_600.0)).append(',')
                    .append(f(point.minimum())).append(',')
                    .append(f(point.mean())).append(',')
                    .append(f(point.maximum())).append('\n');
        }
        Files.writeString(raw.resolve("credit_trajectory_ubr_ca.csv"), csv,
                StandardCharsets.UTF_8);
    }

    private void writeTables(ExperimentRunner.ExperimentBundle bundle)
            throws IOException {
        Map<SchedulerPolicy, List<SimulationResult>> all =
                ExperimentRunner.byPolicy(bundle.overall());
        Map<SchedulerPolicy, List<SimulationResult>> ablations =
                ExperimentRunner.byPolicy(bundle.ablations());

        writeTable("table_i_workflow_size_categories",
                List.of("Category", "Number of tasks"),
                List.of(
                        List.of("Small", "50-200"),
                        List.of("Medium", "500-2,000"),
                        List.of("Large", "5,000-50,000")));

        writeTable("table_ii_sensitivity_configurations",
                List.of("Parameter", "Values"),
                List.of(
                        List.of("Delta t (s)", "60, 300, 900"),
                        List.of("H", "1, 2, 4"),
                        List.of("epsilon", "0.01, 0.05, 0.10"),
                        List.of("lambda", "10, 100, 1000")));

        List<List<String>> overall = new ArrayList<>();
        for (SchedulerPolicy policy : ExperimentRunner.MANUSCRIPT_POLICIES) {
            List<SimulationResult> values = all.get(policy);
            overall.add(List.of(policy.label(),
                    stat(values, SimulationResult::cost, 3),
                    percentStat(values, SimulationResult::violationRate, 2),
                    stat(values, r -> r.creditExhaustions(), 2),
                    stat(values, r -> r.migrations(), 2)));
        }
        writeTable("table_iii_overall_performance",
                List.of("Method", "Cost (USD)", "Deadline violations (%)",
                        "Credit exhaustions", "Migration count"),
                overall);
        writeTable("table_iv_overall_performance",
                List.of("Method", "Cost (USD)", "Deadline violations (%)",
                        "Credit exhaustions", "Migration count"),
                overall);

        List<List<String>> deadline = new ArrayList<>();
        for (SchedulerPolicy policy : List.of(SchedulerPolicy.HEFT,
                SchedulerPolicy.KRP, SchedulerPolicy.EAVC, SchedulerPolicy.CCS,
                SchedulerPolicy.CARS, SchedulerPolicy.UBR_CA)) {
            List<SimulationResult> values = all.get(policy);
            deadline.add(List.of(policy.label(),
                    percentStat(values, SimulationResult::violationRate, 2),
                    stat(values, SimulationResult::totalLateness, 1)));
        }
        writeTable("table_iv_deadline_performance",
                List.of("Method", "Violation rate (%)", "Total lateness (s)"), deadline);
        writeTable("table_v_deadline_performance",
                List.of("Method", "Violation rate (%)", "Total lateness (s)"), deadline);

        if (!bundle.stress().isEmpty()) {
            List<List<String>> stressRows = stressTableRows(bundle.stress());
            writeTable("table_v_credit_stress",
                    List.of("Stress", "Method", "Cost (USD)",
                            "Violations (%)", "Exhaustions"), stressRows);
            writeTable("table_ix_credit_stress",
                    List.of("Stress", "Method", "Cost (USD)",
                            "Violations (%)", "Exhaustions"), stressRows);
        }

        List<SimulationResult> noBayes = ablations.get(SchedulerPolicy.UBR_CA_NO_BAYES);
        List<SimulationResult> noMigration =
                ablations.get(SchedulerPolicy.UBR_CA_NO_MIGRATION);
        List<SimulationResult> full = all.get(SchedulerPolicy.UBR_CA);
        List<List<String>> table6 = List.of(
                ablationRow("UBR-CA without migration", noMigration),
                ablationRow("UBR-CA without Bayesian learning", noBayes),
                ablationRow("Full UBR-CA", full));
        writeTable("table_vi_ablation",
                List.of("Variant", "Cost (USD)", "Violations (%)",
                        "Exhaustions", "Migrations"),
                table6);

        List<List<String>> sensitivity = bundle.sensitivity().stream()
                .map(point -> List.of(manuscriptParameter(point.parameter()), point.value(),
                        point.cost().formatted(3),
                        new Statistics.Summary(point.boundExceedanceRate().mean() * 100.0,
                                point.boundExceedanceRate().standardDeviation() * 100.0)
                                .formatted(2),
                        point.throttledVmHours().formatted(3))).toList();
        writeTable("table_vii_sensitivity",
                List.of("Parameter", "Value", "Cost (USD)",
                        "Bound exceedance (%)", "Throttled VM-hours"), sensitivity);
        writeTable("table_viii_sensitivity",
                List.of("Parameter", "Value", "Cost (USD)",
                        "Bound exceedance (%)", "Throttled VM-hours"), sensitivity);

        List<List<String>> statistics = new ArrayList<>();
        List<Double> ubrCost = metric(full, SimulationResult::cost);
        for (SchedulerPolicy policy : List.of(SchedulerPolicy.HEFT,
                SchedulerPolicy.KRP, SchedulerPolicy.CCS, SchedulerPolicy.CARS)) {
            List<Double> baselineCost = metric(all.get(policy), SimulationResult::cost);
            if (ubrCost.size() >= 2 && ubrCost.size() == baselineCost.size()) {
                Statistics.PairedComparison comparison = Statistics.compare(ubrCost,
                        baselineCost);
                statistics.add(List.of("UBR-CA vs " + policy.label(),
                        p(comparison.tTestP()), p(comparison.wilcoxonP()),
                        f(comparison.pairedCohensDz())));
            } else {
                statistics.add(List.of("UBR-CA vs " + policy.label(),
                        "N/A", "N/A", "N/A"));
            }
        }
        writeTable("table_viii_cost_significance",
                List.of("Comparison", "Paired t-test p", "Wilcoxon p",
                        "Paired Cohen's d_z"), statistics);
        writeTable("table_vii_statistical_significance",
                List.of("Comparison", "Paired t-test p", "Wilcoxon p",
                        "Paired Cohen's d_z"), statistics);

        List<List<String>> reliabilityStatistics = new ArrayList<>();
        addPairedComparison(reliabilityStatistics,
                "Deadline violations (%): UBR-CA - HEFT",
                metric(full, r -> 100.0 * r.violationRate()),
                metric(all.get(SchedulerPolicy.HEFT),
                        r -> 100.0 * r.violationRate()));
        addPairedComparison(reliabilityStatistics,
                "Deadline violations (%): UBR-CA - CARS",
                metric(full, r -> 100.0 * r.violationRate()),
                metric(all.get(SchedulerPolicy.CARS),
                        r -> 100.0 * r.violationRate()));
        addPairedComparison(reliabilityStatistics,
                "Credit exhaustions: UBR-CA - CARS",
                metric(full, r -> r.creditExhaustions()),
                metric(all.get(SchedulerPolicy.CARS),
                        r -> r.creditExhaustions()));
        addPairedComparison(reliabilityStatistics,
                "Migrations: UBR-CA - CARS",
                metric(full, r -> r.migrations()),
                metric(all.get(SchedulerPolicy.CARS),
                        r -> r.migrations()));
        writeTable("table_ix_reliability_significance",
                List.of("Paired comparison", "Mean difference",
                        "95% CI", "Paired t-test p",
                        "Wilcoxon p", "Paired Cohen's d_z"),
                reliabilityStatistics);
    }

    private static void addPairedComparison(List<List<String>> rows,
                                            String label,
                                            List<Double> treatment,
                                            List<Double> control) {
        if (treatment.size() < 2 || treatment.size() != control.size()) {
            rows.add(List.of(label, "N/A", "N/A", "N/A", "N/A", "N/A"));
            return;
        }
        Statistics.PairedComparison comparison = Statistics.compare(treatment,
                control);
        rows.add(List.of(label,
                String.format(Locale.ROOT, "%.3f", comparison.meanDifference()),
                String.format(Locale.ROOT, "[%.3f, %.3f]",
                        comparison.confidenceLower(),
                        comparison.confidenceUpper()),
                p(comparison.tTestP()), p(comparison.wilcoxonP()),
                numericOrNa(comparison.pairedCohensDz(), 3)));
    }
    private List<String> ablationRow(String label, List<SimulationResult> values) {
        return List.of(label,
                stat(values, SimulationResult::cost, 3),
                percentStat(values, SimulationResult::violationRate, 2),
                stat(values, r -> r.creditExhaustions(), 2),
                stat(values, r -> r.migrations(), 2));
    }

    private String manuscriptParameter(String parameter) {
        return switch (parameter) {
            case "interval_seconds" -> "Delta t (s)";
            case "lookahead_H" -> "H";
            case "epsilon" -> "epsilon";
            case "lateness_lambda" -> "lambda";
            default -> parameter;
        };
    }

    private List<List<String>> stressTableRows(List<SimulationResult> results) {
        int perStress = results.size() / 3;
        List<List<String>> rows = new ArrayList<>();
        WorkloadGenerator.Stress[] levels = WorkloadGenerator.Stress.values();
        for (int s = 0; s < levels.length; s++) {
            List<SimulationResult> block = results.subList(s * perStress,
                    (s + 1) * perStress);
            Map<SchedulerPolicy, List<SimulationResult>> grouped =
                    ExperimentRunner.byPolicy(block);
            for (SchedulerPolicy policy : List.of(SchedulerPolicy.HEFT,
                    SchedulerPolicy.KRP, SchedulerPolicy.CCS, SchedulerPolicy.CARS,
                    SchedulerPolicy.UBR_CA_NO_MIGRATION, SchedulerPolicy.UBR_CA)) {
                List<SimulationResult> values = grouped.get(policy);
                rows.add(List.of(levels[s].name(), policy.label(),
                        stat(values, SimulationResult::cost, 3),
                        percentStat(values, SimulationResult::violationRate, 2),
                        stat(values, r -> r.creditExhaustions(), 2)));
            }
        }
        return rows;
    }

    private void writeTable(String name, List<String> headers,
                            List<List<String>> rows) throws IOException {
        StringBuilder markdown = new StringBuilder("| ")
                .append(String.join(" | ", headers)).append(" |\n| ");
        markdown.append(String.join(" | ", headers.stream().map(h -> "---").toList()))
                .append(" |\n");
        for (List<String> row : rows) {
            markdown.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        Files.writeString(tables.resolve(name + ".md"), markdown,
                StandardCharsets.UTF_8);

        StringBuilder csvText = new StringBuilder();
        csvText.append(headers.stream().map(ArtifactWriter::csv)
                .reduce((a, b) -> a + "," + b).orElse("")).append('\n');
        for (List<String> row : rows) {
            csvText.append(row.stream().map(ArtifactWriter::csv)
                    .reduce((a, b) -> a + "," + b).orElse("")).append('\n');
        }
        Files.writeString(tables.resolve(name + ".csv"), csvText,
                StandardCharsets.UTF_8);

        String columnSpec = "l" + "r".repeat(Math.max(0, headers.size() - 1));
        StringBuilder latex = new StringBuilder("\\begin{tabular}{")
                .append(columnSpec).append("}\n\\toprule\n")
                .append(headers.stream().map(ArtifactWriter::latex)
                        .reduce((a, b) -> a + " & " + b).orElse(""))
                .append(" \\\\\n\\midrule\n");
        for (List<String> row : rows) {
            latex.append(row.stream().map(ArtifactWriter::latex)
                    .reduce((a, b) -> a + " & " + b).orElse(""))
                    .append(" \\\\\n");
        }
        latex.append("\\bottomrule\n\\end{tabular}\n");
        Files.writeString(tables.resolve(name + ".tex"), latex,
                StandardCharsets.UTF_8);
        System.out.printf("    table %-38s rows=%d%n", name, rows.size());
    }

    private void writeFigures(ExperimentRunner.ExperimentBundle bundle)
            throws IOException {
        Map<SchedulerPolicy, List<SimulationResult>> all =
                ExperimentRunner.byPolicy(bundle.overall());
        List<String> labels = ExperimentRunner.MANUSCRIPT_POLICIES.stream()
                .map(SchedulerPolicy::label).toList();
        double heftCost = summarize(all.get(SchedulerPolicy.HEFT),
                SimulationResult::cost).mean();
        List<Double> normalized = new ArrayList<>();
        List<Double> errors = new ArrayList<>();
        for (SchedulerPolicy policy : ExperimentRunner.MANUSCRIPT_POLICIES) {
            Statistics.Summary value = summarize(all.get(policy), SimulationResult::cost);
            normalized.add(value.mean() / heftCost);
            errors.add(value.standardDeviation() / heftCost);
        }
        Path normalizedCost = figures.resolve("figure_2_normalized_execution_cost");
        ChartWriter.bar(normalizedCost,
                "Normalized workflow execution cost", "Normalized cost (HEFT = 1.0)",
                labels, normalized, errors);
        copyFigure(normalizedCost, figures.resolve("figure_1_normalized_execution_cost"));

        SimulationResult representative = all.get(SchedulerPolicy.UBR_CA).get(0);
        List<Double> time = representative.creditTrajectory().stream()
                .map(p -> p.timeSeconds() / 3_600.0).toList();
        List<Color> palette = ChartWriter.palette();
        Path creditTrajectory = figures.resolve("figure_3_credit_trajectory");
        ChartWriter.line(creditTrajectory,
                "Representative CPU credit trajectory under UBR-CA",
                "Simulation time (hours)", "CPU credits",
                List.of(
                        new ChartWriter.Series("Minimum", time,
                                representative.creditTrajectory().stream()
                                        .map(SimulationResult.CreditPoint::minimum).toList(),
                                palette.get(4)),
                        new ChartWriter.Series("Mean", time,
                                representative.creditTrajectory().stream()
                                        .map(SimulationResult.CreditPoint::mean).toList(),
                                palette.get(0)),
                        new ChartWriter.Series("Maximum", time,
                                representative.creditTrajectory().stream()
                                        .map(SimulationResult.CreditPoint::maximum).toList(),
                                palette.get(2))), false);
        copyFigure(creditTrajectory, figures.resolve("figure_2_credit_trajectory"));

        Path scalability = figures.resolve("figure_7_scheduler_scalability");
        ChartWriter.line(scalability,
                "UBR-CA scheduler runtime versus workflow size",
                "Number of tasks (log scale)", "Scheduler CPU time (s)",
                List.of(new ChartWriter.Series("UBR-CA",
                        bundle.scalability().stream().map(p -> (double) p.tasks()).toList(),
                        bundle.scalability().stream()
                                .map(p -> p.runtimeSeconds().mean()).toList(),
                        palette.get(0))), true);
        copyFigure(scalability, figures.resolve("figure_3_scheduler_scalability"));

        List<Path> sensitivityPanels = writeSensitivityFigures(bundle.sensitivity(), palette);
        ChartWriter.grid(figures.resolve("figure_5_sensitivity_panels"),
                "Sensitivity of UBR-CA to its main control parameters",
                sensitivityPanels,
                List.of("(a) Scheduling interval", "(b) Lookahead horizon",
                        "(c) Risk tolerance", "(d) Lateness penalty"));
        writePosteriorConvergence(bundle.config(), palette);
        if (!bundle.stress().isEmpty()) writeStressFigure(bundle.stress(), palette);
    }

    private List<Path> writeSensitivityFigures(
            List<ExperimentRunner.SensitivityPoint> points, List<Color> palette)
            throws IOException {
        Map<String, List<ExperimentRunner.SensitivityPoint>> grouped =
                new LinkedHashMap<>();
        for (ExperimentRunner.SensitivityPoint point : points) {
            grouped.computeIfAbsent(point.parameter(), ignored -> new ArrayList<>())
                    .add(point);
        }
        int index = 0;
        List<Path> panels = new ArrayList<>();
        for (Map.Entry<String, List<ExperimentRunner.SensitivityPoint>> entry
                : grouped.entrySet()) {
            List<ExperimentRunner.SensitivityPoint> values = entry.getValue();
            List<Double> x = values.stream()
                    .map(p -> Double.parseDouble(p.value())).toList();
            char panel = (char) ('a' + index);
            Path base = figures.resolve("figure_5" + panel + "_sensitivity_"
                    + entry.getKey());
            ChartWriter.line(base,
                    "Sensitivity to " + entry.getKey().replace('_', ' '),
                    entry.getKey().replace('_', ' '), "Mean value",
                    List.of(
                            new ChartWriter.Series("Cost (USD)", x,
                                    values.stream().map(p -> p.cost().mean()).toList(),
                                    palette.get(0)),
                            new ChartWriter.Series("Violation rate", x,
                                    values.stream().map(p -> p.violationRate().mean()).toList(),
                                    palette.get(1)),
                            new ChartWriter.Series("Throttled VM-hours", x,
                                    values.stream().map(p -> p.throttledVmHours().mean()).toList(),
                                    palette.get(4))), false);
            copyFigure(base, figures.resolve("figure_" + (index + 4)
                    + "_sensitivity_" + entry.getKey()));
            panels.add(Path.of(base + ".png"));
            index++;
        }
        return panels;
    }

    private void writePosteriorConvergence(ExperimentConfig config,
                                           List<Color> palette) throws IOException {
        List<Double> samples = new ArrayList<>();
        List<Double> sigma = new ArrayList<>();
        List<Double> halfWidth = new ArrayList<>();
        double z = BayesianEstimator.inverseNormal(1.0 - config.epsilon());
        for (int n = 1; n <= 100; n++) {
            double variance = 1.0 / (1.0 / config.priorVariance()
                    + n / config.observationVariance());
            samples.add((double) n);
            sigma.add(Math.sqrt(variance));
            halfWidth.add(z * Math.sqrt(variance));
        }
        Path convergence = figures.resolve("figure_6_bayesian_convergence");
        ChartWriter.line(convergence,
                "Bayesian posterior uncertainty convergence",
                "Utilization observations", "Posterior uncertainty",
                List.of(new ChartWriter.Series("Posterior standard deviation",
                                samples, sigma, palette.get(0)),
                        new ChartWriter.Series("95% half-width",
                                samples, halfWidth, palette.get(1))), false);
        copyFigure(convergence, figures.resolve("figure_8_bayesian_convergence"));
    }

    private void writeStressFigure(List<SimulationResult> stress,
                                   List<Color> palette) throws IOException {
        int perStress = stress.size() / 3;
        List<ChartWriter.Series> series = new ArrayList<>();
        int color = 0;
        for (SchedulerPolicy policy : List.of(SchedulerPolicy.HEFT,
                SchedulerPolicy.CARS, SchedulerPolicy.UBR_CA_NO_MIGRATION,
                SchedulerPolicy.UBR_CA)) {
            List<Double> y = new ArrayList<>();
            for (int s = 0; s < 3; s++) {
                Map<SchedulerPolicy, List<SimulationResult>> grouped =
                        ExperimentRunner.byPolicy(stress.subList(s * perStress,
                                (s + 1) * perStress));
                y.add(summarize(grouped.get(policy),
                        r -> r.creditExhaustions()).mean());
            }
            series.add(new ChartWriter.Series(policy.label(),
                    List.of(1.0, 2.0, 3.0), y, palette.get(color++)));
        }
        Path stressFigure = figures.resolve("figure_4_credit_stress");
        ChartWriter.line(stressFigure,
                "Credit exhaustion across stress levels",
                "Stress level (1=light, 2=moderate, 3=heavy)",
                "Credit exhaustion events", series, false);
        copyFigure(stressFigure, figures.resolve("figure_9_credit_stress"));
    }

    private void copyFigure(Path sourceBase, Path targetBase) throws IOException {
        for (String extension : List.of(".png", ".svg")) {
            Path source = Path.of(sourceBase + extension);
            Path target = Path.of(targetBase + extension);
            if (Files.exists(source)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void writeSummary(ExperimentRunner.ExperimentBundle bundle)
            throws IOException {
        Map<SchedulerPolicy, List<SimulationResult>> grouped =
                ExperimentRunner.byPolicy(bundle.overall());
        Statistics.Summary ubrCost = summarize(grouped.get(SchedulerPolicy.UBR_CA),
                SimulationResult::cost);
        Statistics.Summary heftCost = summarize(grouped.get(SchedulerPolicy.HEFT),
                SimulationResult::cost);
        Statistics.Summary ubrViolations = summarize(grouped.get(SchedulerPolicy.UBR_CA),
                SimulationResult::violationRate);
        Statistics.Summary heftViolations = summarize(grouped.get(SchedulerPolicy.HEFT),
                SimulationResult::violationRate);
        String summary = """
                # UBR-CA experiment summary

                Generated from %d paired random seeds using %s.

                - UBR-CA cost: %s USD
                - HEFT cost: %s USD
                - Relative UBR-CA cost change versus HEFT: %.2f%%
                - UBR-CA deadline violation rate: %s%%
                - HEFT deadline violation rate: %s%%
                - Relative UBR-CA deadline-violation change versus HEFT: %.2f%%

                The `tables` directory contains CSV, Markdown, and LaTeX versions of
                manuscript Tables I-VIII. The `figures` directory contains PNG and
                editable SVG versions of manuscript Figures 2-7 plus compatibility
                aliases for earlier draft numbering. The `raw` directory preserves
                every per-seed result.

                ## Interpretation boundary

                These values are simulator outputs for the workload source stated
                above. When the source is synthetic, they must be described as
                synthetic/benchmark-shaped results, not as measurements from the
                Alibaba Cluster Trace. Use `--trace <csv>` to regenerate the same
                artifact set from an imported trace.
                """.formatted(bundle.repetitions(), bundle.workloadSource(),
                ubrCost.formatted(3), heftCost.formatted(3),
                100.0 * relativeReduction(heftCost.mean(), ubrCost.mean()),
                new Statistics.Summary(ubrViolations.mean() * 100.0,
                        ubrViolations.standardDeviation() * 100.0).formatted(2),
                new Statistics.Summary(heftViolations.mean() * 100.0,
                        heftViolations.standardDeviation() * 100.0).formatted(2),
                100.0 * relativeReduction(heftViolations.mean(), ubrViolations.mean()));
        Files.writeString(output.resolve("RESULTS.md"), summary,
                StandardCharsets.UTF_8);
    }

    private void writeManuscriptGuide(ExperimentRunner.ExperimentBundle bundle)
            throws IOException {
        Map<SchedulerPolicy, List<SimulationResult>> grouped =
                ExperimentRunner.byPolicy(bundle.overall());
        Statistics.Summary ubrCost = summarize(grouped.get(SchedulerPolicy.UBR_CA),
                SimulationResult::cost);
        Statistics.Summary heftCost = summarize(grouped.get(SchedulerPolicy.HEFT),
                SimulationResult::cost);
        Statistics.Summary carsCost = summarize(grouped.get(SchedulerPolicy.CARS),
                SimulationResult::cost);
        Statistics.Summary ubrExhaustion = summarize(grouped.get(SchedulerPolicy.UBR_CA),
                r -> r.creditExhaustions());
        Statistics.Summary heftExhaustion = summarize(grouped.get(SchedulerPolicy.HEFT),
                r -> r.creditExhaustions());
        Statistics.Summary carsExhaustion = summarize(grouped.get(SchedulerPolicy.CARS),
                r -> r.creditExhaustions());
        Statistics.Summary ubrMigration = summarize(grouped.get(SchedulerPolicy.UBR_CA),
                r -> r.migrations());
        Statistics.Summary carsMigration = summarize(grouped.get(SchedulerPolicy.CARS),
                r -> r.migrations());
        Statistics.Summary ubrViolation = summarize(grouped.get(SchedulerPolicy.UBR_CA),
                SimulationResult::violationRate);
        Statistics.Summary heftViolation = summarize(grouped.get(SchedulerPolicy.HEFT),
                SimulationResult::violationRate);
        ExperimentRunner.ScalePoint largest = bundle.scalability().stream()
                .max(Comparator.comparingInt(ExperimentRunner.ScalePoint::tasks))
                .orElseThrow();
        String guide = """
                # Manuscript integration guide

                ## Provenance

                These numbers use %s and %d paired seeds. State this explicitly.
                They are not Alibaba trace measurements unless this run was invoked
                with `--trace`.

                ## Publication-ready manuscript artifacts

                - Table I: `tables/table_i_workflow_size_categories.tex`
                - Table II: `tables/table_ii_sensitivity_configurations.tex`
                - Table III: `tables/table_iii_overall_performance.tex`
                - Table IV: `tables/table_iv_deadline_performance.tex`
                - Table V: `tables/table_v_credit_stress.tex`
                - Table VI: `tables/table_vi_ablation.tex`
                - Table VII: `tables/table_vii_sensitivity.tex`
                - Table VIII: `tables/table_viii_cost_significance.tex`
                - Figure 2: `figures/figure_2_normalized_execution_cost.svg`
                - Figure 3: `figures/figure_3_credit_trajectory.svg`
                - Figure 4: `figures/figure_4_credit_stress.svg`
                - Figure 5: `figures/figure_5_sensitivity_panels.png`
                - Figure 6: `figures/figure_6_bayesian_convergence.svg`
                - Figure 7: `figures/figure_7_scheduler_scalability.svg`

                Use the SVG files for typesetting when the journal accepts vector
                artwork; otherwise use the 1600x960 PNG files.

                ## Evidence-backed result wording for this run

                > Across %d paired repetitions, UBR-CA obtained a deadline
                > violation rate of %.2f ± %.2f%%, compared with %.2f ± %.2f%%
                > for HEFT. Credit-exhaustion events decreased from %.2f ± %.2f
                > under HEFT to %.2f ± %.2f under UBR-CA (%.2f%% reduction).

                > Relative to the reactive CARS baseline, UBR-CA reduced
                > credit-exhaustion events by %.2f%% and migrations by %.2f%%.

                > Scheduler CPU time was %.4f s for %,d tasks, supporting
                > practical online execution at the tested scale.

                ## Cost claim that requires revision or real-trace confirmation

                In this run UBR-CA cost %s USD, versus %s USD for HEFT and %s USD
                for CARS. That is a %.2f%% cost increase relative to HEFT, not a
                reduction. Do not retain any unconditional lower-execution-cost
                statement for these synthetic results.
                Either:

                1. revise the conclusion to describe the measured
                   cost-safety/deadline trade-off; or
                2. run the provided Alibaba importer on the intended trace and
                   use the resulting trace-backed table if it supports the claim.

                ## Exact-solver boundary

                The internal strict-feasibility diagnostic is not an exact
                branch-and-bound or MILP solver and is deliberately excluded from
                publication tables and figures. Do not present it as an optimal
                baseline. Add an external exact solver only for tractable instances
                if an optimality-gap claim is required.

                ## Statistical wording

                Table VIII currently tests paired execution cost. A positive
                paired Cohen's d_z means UBR-CA cost is higher. Do not describe those
                comparisons as cost improvements. If the paper claims significance
                for deadline violations or exhaustion events, report separate
                metric-specific tests (the raw paired data are in
                `raw/overall_results.csv`).
                """.formatted(bundle.workloadSource(), bundle.repetitions(),
                bundle.repetitions(),
                ubrViolation.mean() * 100.0, ubrViolation.standardDeviation() * 100.0,
                heftViolation.mean() * 100.0, heftViolation.standardDeviation() * 100.0,
                heftExhaustion.mean(), heftExhaustion.standardDeviation(),
                ubrExhaustion.mean(), ubrExhaustion.standardDeviation(),
                100.0 * relativeReduction(heftExhaustion.mean(), ubrExhaustion.mean()),
                100.0 * relativeReduction(carsExhaustion.mean(), ubrExhaustion.mean()),
                100.0 * relativeReduction(carsMigration.mean(), ubrMigration.mean()),
                largest.runtimeSeconds().mean(), largest.tasks(),
                ubrCost.formatted(3), heftCost.formatted(3), carsCost.formatted(3),
                -100.0 * relativeReduction(heftCost.mean(), ubrCost.mean()));
        Files.writeString(output.resolve("MANUSCRIPT_GUIDE.md"), guide,
                StandardCharsets.UTF_8);
    }

    private static Statistics.Summary summarize(List<SimulationResult> values,
                                                ToDoubleFunction<SimulationResult> metric) {
        return Statistics.summarize(metric(values, metric));
    }

    private static List<Double> metric(List<SimulationResult> values,
                                       ToDoubleFunction<SimulationResult> metric) {
        return values.stream().mapToDouble(metric).boxed().toList();
    }

    private static String stat(List<SimulationResult> values,
                               ToDoubleFunction<SimulationResult> metric,
                               int decimals) {
        return summarize(values, metric).formatted(decimals);
    }

    private static String percentStat(List<SimulationResult> values,
                                      ToDoubleFunction<SimulationResult> metric,
                                      int decimals) {
        Statistics.Summary value = summarize(values, metric);
        return new Statistics.Summary(value.mean() * 100.0,
                value.standardDeviation() * 100.0).formatted(decimals);
    }

    private static double relativeReduction(double baseline, double value) {
        return Math.abs(baseline) < 1e-12 ? 0.0 : (baseline - value) / baseline;
    }

    private static String p(double value) {
        if (!Double.isFinite(value)) return "N/A";
        return value < 0.001 ? "<0.001" : String.format(Locale.ROOT, "%.4f", value);
    }

    private static String numericOrNa(double value, int decimals) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%." + decimals + "f", value)
                : "N/A";
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String latex(String value) {
        return value.replace("\\", "\\textbackslash{}")
                .replace("&", "\\&").replace("%", "\\%")
                .replace("_", "\\_").replace("±", "$\\pm$")
                .replace("<", "$<$");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
