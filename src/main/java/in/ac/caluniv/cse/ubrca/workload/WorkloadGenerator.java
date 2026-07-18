package in.ac.caluniv.cse.ubrca.workload;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.model.Workflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class WorkloadGenerator {
    public enum Pattern { STEP, RAMP, BURSTY, TRACE_DRIVEN }
    public enum Stress { LIGHT, MODERATE, HEAVY }

    private WorkloadGenerator() {}

    public static List<Workflow> generate(ExperimentConfig config) {
        Random random = new Random(config.seed());
        List<Workflow> workflows = new ArrayList<>();
        int nextTaskId = 0;
        String[] benchmarks = {"Montage", "Epigenomics", "LIGO", "Alibaba-shaped"};
        for (int w = 0; w < config.workflows(); w++) {
            int jitter = Math.max(2, config.tasksPerWorkflow() / 5);
            int count = Math.max(8, config.tasksPerWorkflow()
                    + random.nextInt(2 * jitter + 1) - jitter);
            String benchmark = benchmarks[w % benchmarks.length];
            double arrival = w * config.intervalSeconds() * 1.5;
            List<Task> tasks = generateTasks(nextTaskId, w, count, benchmark,
                    config.stress(), config.priorVariance(), random);
            nextTaskId += count;
            double criticalPath = criticalPath(tasks);
            double deadline = arrival + criticalPath * config.deadlineFactor();
            workflows.add(new Workflow(w, benchmark, arrival, deadline, tasks));
        }
        return workflows;
    }

    private static List<Task> generateTasks(int startId, int workflowId, int count,
                                            String benchmark, Stress stress,
                                            double priorVariance, Random random) {
        List<Task> tasks = new ArrayList<>(count);
        double stressShift = switch (stress) {
            case LIGHT -> -0.12;
            case MODERATE -> 0.04;
            case HEAVY -> 0.18;
        };
        int width = Math.max(3, (int) Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            List<Integer> predecessors = predecessors(startId, i, count, width,
                    benchmark, random);
            double duration = 450.0 + random.nextDouble() * 2_400.0;
            if (benchmark.equals("LIGO")) duration *= 1.25;
            if (benchmark.equals("Montage")) duration *= 0.75;
            double base = 0.26 + random.nextDouble() * 0.42 + stressShift;
            double utilization = clamp(base, 0.12, 0.94);
            Pattern pattern = Pattern.values()[Math.floorMod(i + workflowId, 4)];
            double stateGb = 0.1 + random.nextDouble() * 2.2;
            double criticalityPlaceholder = duration;
            tasks.add(new Task(startId + i, workflowId, predecessors, duration,
                    utilization, pattern, stateGb, criticalityPlaceholder,
                    priorVariance));
        }
        return withUpwardRanks(tasks, priorVariance);
    }

    private static List<Integer> predecessors(int start, int i, int count, int width,
                                              String benchmark, Random random) {
        List<Integer> p = new ArrayList<>(3);
        if (i == 0) return p;
        switch (benchmark) {
            case "Montage" -> {
                int layer = i / width;
                if (layer > 0) {
                    int previousStart = Math.max(0, (layer - 1) * width);
                    int previousEnd = Math.min(i, layer * width);
                    p.add(start + previousStart + random.nextInt(previousEnd - previousStart));
                    if (random.nextDouble() < 0.28 && previousEnd - previousStart > 1) {
                        int candidate = start + previousStart
                                + random.nextInt(previousEnd - previousStart);
                        if (!p.contains(candidate)) p.add(candidate);
                    }
                }
            }
            case "Epigenomics" -> {
                p.add(start + i - 1);
                if (i > 3 && i % 5 == 0) p.add(start + i - 3);
            }
            case "LIGO" -> {
                p.add(start + i - 1);
                if (i > 2 && random.nextDouble() < 0.35) p.add(start + i - 2);
            }
            default -> {
                if (random.nextDouble() < 0.72) {
                    p.add(start + random.nextInt(i));
                }
                if (i > 2 && random.nextDouble() < 0.22) {
                    int candidate = start + random.nextInt(i);
                    if (!p.contains(candidate)) p.add(candidate);
                }
            }
        }
        return p;
    }

    private static double criticalPath(List<Task> tasks) {
        Map<Integer, Double> finish = new HashMap<>();
        double max = 0.0;
        for (Task task : tasks) {
            double predecessorFinish = task.predecessors.stream()
                    .mapToDouble(id -> finish.getOrDefault(id, 0.0)).max().orElse(0.0);
            double value = predecessorFinish + task.durationSeconds;
            finish.put(task.id, value);
            max = Math.max(max, value);
        }
        return max;
    }

    /**
     * Imports a simple trace/DAG CSV. Required columns:
     * workflow_id,task_id,arrival_time,duration,cpu_utilization,predecessors.
     * Optional columns: benchmark,deadline,pattern,state_size_gb.
     * Predecessors are pipe-separated task IDs.
     */
    public static List<Workflow> importCsv(Path path, ExperimentConfig config)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IOException("Trace CSV is empty: " + path);
            String[] headers = splitCsv(headerLine);
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                index.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
            }
            for (String required : List.of("workflow_id", "task_id", "arrival_time",
                    "duration", "cpu_utilization", "predecessors")) {
                if (!index.containsKey(required)) {
                    throw new IOException("Missing required CSV column: " + required);
                }
            }
            Map<Integer, List<Task>> byWorkflow = new LinkedHashMap<>();
            Map<Integer, Double> arrivals = new HashMap<>();
            Map<Integer, Double> explicitDeadlines = new HashMap<>();
            Map<Integer, String> names = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] value = splitCsv(line);
                int workflowId = integer(value, index, "workflow_id");
                int taskId = integer(value, index, "task_id");
                double arrival = decimal(value, index, "arrival_time");
                double duration = decimal(value, index, "duration");
                double utilization = clamp(decimal(value, index, "cpu_utilization"),
                        0.01, 1.0);
                List<Integer> predecessors = new ArrayList<>();
                String predecessorText = text(value, index, "predecessors", "");
                if (!predecessorText.isBlank()) {
                    for (String item : predecessorText.split("\\|")) {
                        predecessors.add(Integer.parseInt(item.trim()));
                    }
                }
                Pattern pattern = Pattern.valueOf(text(value, index, "pattern",
                        "TRACE_DRIVEN").toUpperCase(Locale.ROOT));
                double stateSize = Double.parseDouble(text(value, index,
                        "state_size_gb", "0.5"));
                Task task = new Task(taskId, workflowId, predecessors, duration,
                        utilization, pattern, stateSize, duration,
                        config.priorVariance());
                byWorkflow.computeIfAbsent(workflowId, ignored -> new ArrayList<>())
                        .add(task);
                arrivals.put(workflowId, arrival);
                String deadlineText = text(value, index, "deadline", "");
                if (!deadlineText.isBlank()) {
                    explicitDeadlines.merge(workflowId,
                            Double.parseDouble(deadlineText), Math::max);
                }
                names.put(workflowId, text(value, index, "benchmark", "Imported trace"));
            }
            List<Workflow> workflows = new ArrayList<>();
            for (Map.Entry<Integer, List<Task>> entry : byWorkflow.entrySet()) {
                int id = entry.getKey();
                List<Task> tasks = entry.getValue().stream()
                        .sorted((a, b) -> Integer.compare(a.id, b.id)).toList();
                double deadline = explicitDeadlines.getOrDefault(id, Double.NaN);
                if (!Double.isFinite(deadline) || deadline <= arrivals.get(id)) {
                    deadline = arrivals.get(id) + criticalPath(tasks)
                            * config.deadlineFactor();
                }
                tasks = withUpwardRanks(tasks, config.priorVariance());
                workflows.add(new Workflow(id, names.get(id), arrivals.get(id),
                        deadline, tasks));
            }
            return workflows;
        }
    }

    /**
     * Computes HEFT-style upward ranks for a zero-communication DAG.
     *
     * <p>The original HEFT rank uses average computation and communication
     * costs. This simulator does not model inter-VM transfer costs between DAG
     * tasks, so the communication component is zero and the upward rank becomes
     * duration(task) + max upward-rank(child). The resulting value is stored in
     * {@link Task#criticality} and is used by HEFT and the common ready queue.</p>
     */
    private static List<Task> withUpwardRanks(List<Task> tasks,
                                              double priorVariance) {
        Map<Integer, Task> byId = new HashMap<>();
        Map<Integer, List<Integer>> children = new HashMap<>();
        for (Task task : tasks) {
            byId.put(task.id, task);
            children.computeIfAbsent(task.id, ignored -> new ArrayList<>());
        }
        for (Task task : tasks) {
            for (int predecessor : task.predecessors) {
                children.computeIfAbsent(predecessor, ignored -> new ArrayList<>())
                        .add(task.id);
            }
        }
        Map<Integer, Double> ranks = new HashMap<>();
        List<Task> ranked = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            double rank = upwardRank(task.id, byId, children, ranks);
            ranked.add(new Task(task.id, task.workflowId, task.predecessors,
                    task.durationSeconds, task.profileMean, task.pattern,
                    task.stateSizeGb, rank, priorVariance));
        }
        return ranked;
    }

    private static double upwardRank(int taskId, Map<Integer, Task> byId,
                                     Map<Integer, List<Integer>> children,
                                     Map<Integer, Double> ranks) {
        Double cached = ranks.get(taskId);
        if (cached != null) return cached;
        Task task = byId.get(taskId);
        if (task == null) return 0.0;
        double childMaximum = 0.0;
        for (int childId : children.getOrDefault(taskId, List.of())) {
            childMaximum = Math.max(childMaximum,
                    upwardRank(childId, byId, children, ranks));
        }
        double rank = task.durationSeconds + childMaximum;
        ranks.put(taskId, rank);
        return rank;
    }

    private static int integer(String[] value, Map<String, Integer> index, String name) {
        return Integer.parseInt(value[index.get(name)].trim());
    }

    private static double decimal(String[] value, Map<String, Integer> index, String name) {
        return Double.parseDouble(value[index.get(name)].trim());
    }

    private static String text(String[] value, Map<String, Integer> index,
                               String name, String fallback) {
        Integer i = index.get(name);
        return i == null || i >= value.length || value[i].isBlank()
                ? fallback : value[i].trim();
    }

    private static String[] splitCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else current.append(c);
        }
        values.add(current.toString());
        return values.toArray(String[]::new);
    }

    private static double clamp(double x, double low, double high) {
        return Math.max(low, Math.min(high, x));
    }
}
