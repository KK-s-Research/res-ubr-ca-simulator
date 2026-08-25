package in.ac.caluniv.cse.ubrca.artifact;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.model.Workflow;
import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Materializes the deterministic synthetic task inputs used by the manuscript.
 * Repeated parameter sweeps that use the same workload are represented once.
 */
public final class TaskInputDatasetWriter {
    private static final long SEED_STRIDE = 7_919L;
    private static final int[] FULL_SCALE_SIZES =
            {50, 200, 500, 2_000, 5_000, 10_000, 50_000};
    private static final int[] QUICK_SCALE_SIZES = {50, 200, 500, 2_000};

    private static final String HEADER = String.join(",",
            "scenario", "repetition_index", "seed", "interval_seconds", "stress",
            "configured_workflows", "configured_tasks_per_workflow", "workflow_id",
            "benchmark", "workflow_arrival_seconds", "workflow_deadline_seconds",
            "task_id", "duration_seconds", "profile_mean_vcpu", "pattern",
            "state_size_gb", "criticality", "predecessors");

    public void write(ExperimentConfig base, int repetitions, boolean quick,
                      Path output) throws IOException {
        Files.createDirectories(output);
        List<ManifestRow> manifest = new ArrayList<>();

        manifest.add(writeStandardArchive(output, "default_moderate_interval_300",
                base, repetitions,
                "Overall, ablation, moderate stress, and default H/epsilon/lambda sweeps"));
        manifest.add(writeStandardArchive(output, "stress_light",
                base.withStress(WorkloadGenerator.Stress.LIGHT), repetitions,
                "Light credit-stress sweep"));
        manifest.add(writeStandardArchive(output, "stress_heavy",
                base.withStress(WorkloadGenerator.Stress.HEAVY), repetitions,
                "Heavy credit-stress sweep"));
        manifest.add(writeStandardArchive(output, "interval_60_seconds",
                base.withSensitivity(60.0, base.lookaheadHorizon(), base.epsilon(),
                        base.latenessPenalty()), repetitions,
                "Scheduling-interval sensitivity at 60 seconds"));
        manifest.add(writeStandardArchive(output, "interval_900_seconds",
                base.withSensitivity(900.0, base.lookaheadHorizon(), base.epsilon(),
                        base.latenessPenalty()), repetitions,
                "Scheduling-interval sensitivity at 900 seconds"));
        manifest.add(writeScalabilityArchive(output, base, quick));

        writeManifest(output.resolve("manifest.csv"), manifest);
    }

    private ManifestRow writeStandardArchive(Path output, String scenario,
                                             ExperimentConfig config,
                                             int repetitions, String purpose)
            throws IOException {
        Path file = output.resolve(scenario + ".csv.gz");
        long rows = 0;
        try (BufferedWriter writer = gzipWriter(file)) {
            writer.write(HEADER);
            writer.newLine();
            for (int repetition = 0; repetition < repetitions; repetition++) {
                long seed = config.seed() + repetition * SEED_STRIDE;
                ExperimentConfig seeded = config.withSeed(seed);
                rows += writeWorkloads(writer, scenario, repetition, seeded,
                        WorkloadGenerator.generate(seeded));
            }
        }
        return manifestRow(file, rows, purpose);
    }

    private ManifestRow writeScalabilityArchive(Path output, ExperimentConfig base,
                                                 boolean quick) throws IOException {
        Path file = output.resolve("scalability_measured_runs.csv.gz");
        int[] sizes = quick ? QUICK_SCALE_SIZES : FULL_SCALE_SIZES;
        int repetitions = quick ? 3 : 10;
        long rows = 0;
        try (BufferedWriter writer = gzipWriter(file)) {
            writer.write(HEADER);
            writer.newLine();
            for (int size : sizes) {
                String scenario = "scalability_tasks_" + size;
                for (int repetition = 0; repetition < repetitions; repetition++) {
                    long seed = base.seed() + size + repetition * SEED_STRIDE;
                    ExperimentConfig scaled = base.withScale(1, size).withSeed(seed);
                    rows += writeWorkloads(writer, scenario, repetition, scaled,
                            WorkloadGenerator.generate(scaled));
                }
            }
        }
        return manifestRow(file, rows,
                "Measured scalability inputs; excluded JVM warm-up inputs are omitted");
    }

    private long writeWorkloads(BufferedWriter writer, String scenario,
                                int repetition, ExperimentConfig config,
                                List<Workflow> workflows) throws IOException {
        long rows = 0;
        for (Workflow workflow : workflows) {
            for (Task task : workflow.tasks) {
                writer.write(csv(scenario));
                writer.write(',');
                writer.write(Integer.toString(repetition));
                writer.write(',');
                writer.write(Long.toString(config.seed()));
                writer.write(',');
                writer.write(number(config.intervalSeconds()));
                writer.write(',');
                writer.write(config.stress().name());
                writer.write(',');
                writer.write(Integer.toString(config.workflows()));
                writer.write(',');
                writer.write(Integer.toString(config.tasksPerWorkflow()));
                writer.write(',');
                writer.write(Integer.toString(workflow.id));
                writer.write(',');
                writer.write(csv(workflow.benchmark));
                writer.write(',');
                writer.write(number(workflow.arrivalTime));
                writer.write(',');
                writer.write(number(workflow.deadline));
                writer.write(',');
                writer.write(Integer.toString(task.id));
                writer.write(',');
                writer.write(number(task.durationSeconds));
                writer.write(',');
                writer.write(number(task.profileMean));
                writer.write(',');
                writer.write(task.pattern.name());
                writer.write(',');
                writer.write(number(task.stateSizeGb));
                writer.write(',');
                writer.write(number(task.criticality));
                writer.write(',');
                writer.write(csv(task.predecessors.stream()
                        .map(String::valueOf).reduce((a, b) -> a + "|" + b)
                        .orElse("")));
                writer.newLine();
                rows++;
            }
        }
        return rows;
    }

    private static BufferedWriter gzipWriter(Path file) throws IOException {
        OutputStream output = Files.newOutputStream(file);
        return new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(output, 1 << 16), StandardCharsets.UTF_8),
                1 << 16);
    }

    private static ManifestRow manifestRow(Path file, long rows, String purpose)
            throws IOException {
        return new ManifestRow(file.getFileName().toString(), rows, Files.size(file),
                sha256(file), purpose);
    }

    private static void writeManifest(Path path, List<ManifestRow> rows)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path,
                StandardCharsets.UTF_8)) {
            writer.write("file,data_rows,compressed_bytes,sha256,purpose");
            writer.newLine();
            for (ManifestRow row : rows) {
                writer.write(csv(row.file()));
                writer.write(',');
                writer.write(Long.toString(row.dataRows()));
                writer.write(',');
                writer.write(Long.toString(row.compressedBytes()));
                writer.write(',');
                writer.write(row.sha256());
                writer.write(',');
                writer.write(csv(row.purpose()));
                writer.newLine();
            }
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private record ManifestRow(String file, long dataRows, long compressedBytes,
                               String sha256, String purpose) {}
}
