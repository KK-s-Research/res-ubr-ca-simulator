package in.ac.caluniv.cse.ubrca;

import in.ac.caluniv.cse.ubrca.artifact.ArtifactWriter;
import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.experiment.ExperimentRunner;
import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;

import java.nio.file.Path;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.help) {
            usage();
            return;
        }
        ExperimentConfig config = ExperimentConfig.defaults(options.output);
        if (options.quick) config = config.withScale(4, 35);
        if (options.large) config = config.withScale(40, 1_000);
        if (options.workflows != null || options.tasksPerWorkflow != null) {
            config = config.withScale(
                    options.workflows == null ? config.workflows() : options.workflows,
                    options.tasksPerWorkflow == null
                            ? config.tasksPerWorkflow() : options.tasksPerWorkflow);
        }
        if (options.deadlineFactor != null) {
            config = config.withDeadlineFactor(options.deadlineFactor);
        }
        if (options.stress != null) {
            config = config.withStress(options.stress);
        }
        System.out.printf("UBR-CA reproducibility suite (%s mode)%n",
                options.quick ? "quick" : options.large ? "large" : "full");
        System.out.println("Output: " + options.output.toAbsolutePath());
        System.out.printf("Java: %s%n", System.getProperty("java.version"));
        System.out.printf("Repetitions: %d paired seed(s)%n", options.repetitions);
        System.out.printf("Default parameters: Delta t=%.0fs, H=%d, epsilon=%.2f, "
                        + "lambda=%.0f, prior variance=%.4f, observation variance=%.4f%n",
                config.intervalSeconds(), config.lookaheadHorizon(), config.epsilon(),
                config.latenessPenalty(), config.priorVariance(),
                config.observationVariance());
        if (options.trace == null) {
            System.out.printf("Workload: synthetic benchmark-shaped DAGs "
                            + "(%d workflows, ~%d tasks/workflow, stress=%s)%n",
                    config.workflows(), config.tasksPerWorkflow(), config.stress());
        } else {
            System.out.println("Workload trace: " + options.trace.toAbsolutePath());
        }
        ExperimentRunner runner = new ExperimentRunner(config,
                options.repetitions, options.quick, options.trace);
        if (options.scalabilityOnly) {
            new ArtifactWriter(options.output).writeScalabilityStudy(
                    runner.runScalability());
            System.out.println("Complete. Scalability results: "
                    + options.output.resolve("raw/scalability_results.csv")
                    .toAbsolutePath());
            return;
        }
        ExperimentRunner.ExperimentBundle bundle = runner.run();
        new ArtifactWriter(options.output).write(bundle);
        System.out.println("Complete. Results: "
                + options.output.resolve("RESULTS.md").toAbsolutePath());
    }
    private static void usage() {
        System.out.println("""
                UBR-CA simulator

                java -jar target/res-ubr-ca-simulator-1.0.0.jar [options]

                  --full             20-seed manuscript experiment (default)
                  --large            stress-scale experiment (40 workflows, ~1,000 tasks each)
                  --quick            small 3-seed verification run
                  --repetitions N    override repetitions
                  --workflows N      override workflow count
                  --tasks N          override nominal tasks per workflow
                  --stress LEVEL     LIGHT, MODERATE, or HEAVY
                  --deadline-factor X override deadline factor
                  --output DIR       artifact directory (default: output)
                  --trace FILE.csv   import real trace/DAG data
                  --scalability-only  run only the replicated scalability study
                  --help             show this message
                """);
    }

    private static final class Options {
        private boolean quick;
        private boolean large;
        private int repetitions = 20;
        private Path output = Path.of("output");
        private Path trace;
        private boolean help;
        private boolean scalabilityOnly;
        private Integer workflows;
        private Integer tasksPerWorkflow;
        private WorkloadGenerator.Stress stress;
        private Double deadlineFactor;

        private static Options parse(String[] args) {
            Options value = new Options();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--quick" -> {
                        value.quick = true;
                        value.large = false;
                        value.repetitions = 3;
                    }
                    case "--full" -> {
                        value.quick = false;
                        value.large = false;
                        value.repetitions = 20;
                    }
                    case "--large" -> {
                        value.quick = false;
                        value.large = true;
                        value.repetitions = 20;
                    }
                    case "--repetitions" -> value.repetitions =
                            Integer.parseInt(requireValue(args, ++i, "--repetitions"));
                    case "--workflows" -> value.workflows =
                            Integer.parseInt(requireValue(args, ++i, "--workflows"));
                    case "--tasks" -> value.tasksPerWorkflow =
                            Integer.parseInt(requireValue(args, ++i, "--tasks"));
                    case "--stress" -> value.stress = WorkloadGenerator.Stress.valueOf(
                            requireValue(args, ++i, "--stress").toUpperCase());
                    case "--deadline-factor" -> value.deadlineFactor =
                            Double.parseDouble(requireValue(args, ++i, "--deadline-factor"));
                    case "--output" -> value.output =
                            Path.of(requireValue(args, ++i, "--output"));
                    case "--trace" -> value.trace =
                            Path.of(requireValue(args, ++i, "--trace"));
                    case "--scalability-only" -> value.scalabilityOnly = true;                    default -> throw new IllegalArgumentException(
                            "Unknown option: " + args[i] + " (use --help)");
                }
            }
            if (value.repetitions < 1) {
                throw new IllegalArgumentException("repetitions must be >= 1");
            }
            if (value.workflows != null && value.workflows < 1) {
                throw new IllegalArgumentException("workflows must be >= 1");
            }
            if (value.tasksPerWorkflow != null && value.tasksPerWorkflow < 8) {
                throw new IllegalArgumentException("tasks must be >= 8");
            }
            if (value.deadlineFactor != null && value.deadlineFactor <= 0.0) {
                throw new IllegalArgumentException("deadline factor must be > 0");
            }
            return value;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return args[index];
        }
    }
}
