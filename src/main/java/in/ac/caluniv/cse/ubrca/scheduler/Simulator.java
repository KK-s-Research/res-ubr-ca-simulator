package in.ac.caluniv.cse.ubrca.scheduler;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.experiment.SimulationResult;
import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.model.VirtualMachine;
import in.ac.caluniv.cse.ubrca.model.Workflow;
import in.ac.caluniv.cse.ubrca.scheduler.policy.MigrationMode;
import in.ac.caluniv.cse.ubrca.scheduler.policy.PlacementContext;
import in.ac.caluniv.cse.ubrca.scheduler.policy.SchedulingPolicy;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Simulator {
    private static final ThreadMXBean THREAD_MX_BEAN =
            ManagementFactory.getThreadMXBean();
    private static final List<VirtualMachine.Type> VM_TYPES = List.of(
            new VirtualMachine.Type("b2.small", 0.40, 2.0, 0.0208,
                    12.0, 72.0, 0.20),
            new VirtualMachine.Type("b4.medium", 0.80, 4.0, 0.0395,
                    24.0, 144.0, 0.40),
            new VirtualMachine.Type("b8.large", 1.60, 8.0, 0.0750,
                    48.0, 288.0, 0.80));

    private final ExperimentConfig config;
    private final SchedulerPolicy policy;
    private final SchedulingPolicy schedulingPolicy;
    private final List<Workflow> workflows;
    private final Map<Integer, Task> taskById = new HashMap<>();
    private final Map<Integer, Workflow> workflowById = new HashMap<>();
    private final List<VirtualMachine> vms = new ArrayList<>();
    private final List<SimulationResult.CreditPoint> trajectory = new ArrayList<>();
    private int nextVmId;
    private int creditExhaustions;
    private double throttledVmSeconds;
    private long robustBoundEvaluations;
    private long robustBoundExceedances;
    private int migrations;
    private int maximumVms;
    private long schedulingNanos;

    public Simulator(ExperimentConfig config, SchedulerPolicy policy,
                     List<Workflow> workflowTemplates) {
        this.config = config;
        this.policy = policy;
        this.schedulingPolicy = policy.createPolicy();
        this.workflows = workflowTemplates.stream()
                .map(w -> w.copy(config.priorVariance())).toList();
        for (Workflow workflow : workflows) {
            workflowById.put(workflow.id, workflow);
            for (Task task : workflow.tasks) taskById.put(task.id, task);
        }
    }

    public SimulationResult run() {
        double time = 0.0;
        int totalTasks = taskById.size();
        int finishedTasks = 0;
        int intervalIndex = 0;
        double maximumDeadline = workflows.stream().mapToDouble(w -> w.deadline)
                .max().orElse(86_400.0);
        double maximumTime = maximumDeadline * 8.0 + totalTasks * config.intervalSeconds();

        while (finishedTasks < totalTasks && time <= maximumTime) {
            long startScheduling = schedulingClockNanos();
            retireIdleVms();
            List<Task> ready = identifyReadyTasks(time);
            ready.sort(Comparator.comparingDouble(
                    (Task t) -> schedulingPolicy.readyPriority(t)).reversed());
            for (Task task : ready) place(task, time);
            migrateCreditCriticalVms(time);
            schedulingNanos += schedulingClockNanos() - startScheduling;

            finishedTasks += executeInterval(time, intervalIndex);
            recordCreditTrajectory(time);
            time += config.intervalSeconds();
            intervalIndex++;
        }
        if (finishedTasks != totalTasks) {
            throw new IllegalStateException("Simulation did not converge: "
                    + finishedTasks + "/" + totalTasks + " tasks finished");
        }

        double cost = vms.stream()
                .mapToDouble(vm -> vm.activeSeconds / 3600.0 * vm.type.pricePerHour())
                .sum();
        int violations = 0;
        double lateness = 0.0;
        double makespan = 0.0;
        for (Workflow workflow : workflows) {
            double completion = workflow.completionTime();
            makespan = Math.max(makespan, completion);
            double late = Math.max(0.0, completion - workflow.deadline);
            if (late > 0.0) violations++;
            lateness += late;
        }
        return new SimulationResult(policy, config.seed(), cost,
                violations / (double) workflows.size(), lateness,
                creditExhaustions, throttledVmSeconds, robustBoundEvaluations,
                robustBoundExceedances, migrations, schedulingNanos / 1e9,
                maximumVms, makespan, List.copyOf(trajectory));
    }

    private static long schedulingClockNanos() {
        return THREAD_MX_BEAN.isCurrentThreadCpuTimeSupported()
                ? THREAD_MX_BEAN.getCurrentThreadCpuTime()
                : System.nanoTime();
    }

    private List<Task> identifyReadyTasks(double time) {
        List<Task> ready = new ArrayList<>();
        for (Workflow workflow : workflows) {
            if (workflow.arrivalTime > time) continue;
            for (Task task : workflow.tasks) {
                if (task.state != Task.State.WAITING) continue;
                boolean predecessorsFinished = task.predecessors.stream()
                        .allMatch(id -> taskById.get(id).state == Task.State.FINISHED);
                if (predecessorsFinished) {
                    task.state = Task.State.READY;
                    ready.add(task);
                }
            }
        }
        return ready;
    }

    private void place(Task task, double time) {
        Candidate best = null;
        for (VirtualMachine vm : vms) {
            if (!vm.active()) continue;
            Candidate candidate = candidate(vm, task, time);
            if (candidate.feasible && (best == null || candidate.score < best.score)) {
                best = candidate;
            }
        }
        if (best == null) {
            VirtualMachine vm = launchVm(task);
            best = candidate(vm, task, time);
            if (!best.feasible) {
                throw new IllegalStateException("No VM type can host task " + task.id);
            }
        }
        assign(task, best.vm, time);
    }

    private Candidate candidate(VirtualMachine vm, Task task, double time) {
        Aggregate current = aggregate(vm.tasks);
        List<Task> projectedTasks = new ArrayList<>(vm.tasks);
        projectedTasks.add(task);
        Aggregate projected = aggregate(projectedTasks);
        double demand = schedulingPolicy.usesRobustCapacity()
                ? projected.worst : projected.mean;
        boolean capacityFeasible = demand <= vm.type.capacity() + 1e-9;
        double required = config.lookaheadHorizon()
                * Math.max(0.0, demand - vm.type.baseline())
                * config.intervalSeconds() / 60.0;
        boolean creditFeasible = !schedulingPolicy.enforcesCreditFeasibility()
                || vm.credits + 1e-9 >= required;
        boolean feasible = capacityFeasible && creditFeasible;
        double score = placementScore(vm, task, current, projected, time);
        return new Candidate(vm, feasible, score);
    }

    private double placementScore(VirtualMachine vm, Task task, Aggregate current,
                                  Aggregate projected, double time) {
        double demand = schedulingPolicy.usesRobustCapacity()
                ? projected.worst : projected.mean;
        double finishEstimate = time + task.remainingSeconds
                * Math.max(1.0, demand / vm.type.capacity());
        double remaining = Math.max(0.0, vm.type.capacity() - demand);
        double incrementalCost = vm.type.pricePerHour()
                * task.remainingSeconds / 3600.0;
        double creditRisk = Math.max(0.0, demand - vm.type.baseline())
                / (vm.credits + 0.1);
        Workflow workflow = workflowById.get(task.workflowId);
        double deadlineRisk = Math.max(0.0, finishEstimate - workflow.deadline);
        PlacementContext context = new PlacementContext(vm, task, workflow, time,
                current.mean, current.worst, projected.mean, projected.worst,
                demand, finishEstimate, remaining, incrementalCost, creditRisk,
                deadlineRisk);
        return schedulingPolicy.score(context, config);
    }

    private VirtualMachine launchVm(Task task) {
        VirtualMachine.Type selected = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (VirtualMachine.Type type : VM_TYPES) {
            VirtualMachine trial = new VirtualMachine(-1, type);
            Aggregate projected = aggregate(List.of(task));
            double demand = schedulingPolicy.usesRobustCapacity()
                    ? projected.worst : projected.mean;
            double required = config.lookaheadHorizon()
                    * Math.max(0.0, demand - type.baseline())
                    * config.intervalSeconds() / 60.0;
            if (demand <= type.capacity()
                    && (!schedulingPolicy.enforcesCreditFeasibility()
                    || trial.credits >= required)) {
                double score = type.pricePerHour() / type.capacity();
                if (score < bestScore) {
                    selected = type;
                    bestScore = score;
                }
            }
        }
        if (selected == null) selected = VM_TYPES.get(VM_TYPES.size() - 1);
        VirtualMachine vm = new VirtualMachine(nextVmId++, selected);
        vms.add(vm);
        maximumVms = Math.max(maximumVms,
                (int) vms.stream().filter(VirtualMachine::active).count());
        return vm;
    }

    private void assign(Task task, VirtualMachine vm, double time) {
        vm.tasks.add(task);
        vm.idleIntervals = 0;
        task.vmId = vm.id;
        task.state = Task.State.RUNNING;
        if (Double.isNaN(task.startTime)) task.startTime = time;
    }

    private Aggregate aggregate(List<Task> tasks) {
        double mean = 0.0;
        double variance = 0.0;
        for (Task task : tasks) {
            mean += estimateMean(task);
            variance += estimateVariance(task);
        }
        double z = BayesianEstimator.inverseNormal(1.0 - config.epsilon());
        return new Aggregate(mean, mean + z * Math.sqrt(Math.max(0.0, variance)));
    }

    private double estimateMean(Task task) {
        return schedulingPolicy.utilizationMean(task);
    }

    private double estimateVariance(Task task) {
        return schedulingPolicy.utilizationVariance(task, config);
    }

    private void migrateCreditCriticalVms(double time) {
        if (schedulingPolicy.migrationMode() == MigrationMode.NONE) return;
        List<VirtualMachine> sources = new ArrayList<>(vms);
        for (VirtualMachine source : sources) {
            if (!source.active() || source.tasks.isEmpty()) continue;
            boolean reactive = schedulingPolicy.migrationMode()
                    == MigrationMode.REACTIVE;
            Aggregate load = aggregate(source.tasks);
            double predicted = predictedCredits(source, load.worst);
            boolean critical = reactive
                    ? source.credits < config.minimumCredits() || source.exhaustedLastInterval
                    : predicted < config.minimumCredits();
            if (!critical) continue;

            double cooldown = recoverySeconds(source);
            if (time - source.lastMigrationTime < cooldown) continue;
            List<Task> candidates = new ArrayList<>(source.tasks);
            candidates.sort(Comparator.comparingDouble(
                    (Task t) -> estimateMean(t) * t.remainingSeconds
                            / (1.0 + t.stateSizeGb)).reversed());
            for (Task task : candidates) {
                if (time - task.lastMigrationTime < cooldown) continue;
                VirtualMachine target = bestMigrationTarget(source, task, time);
                Aggregate sourceLoad = aggregate(source.tasks);
                double expectedDelay = task.remainingSeconds
                        * Math.max(0.0, sourceLoad.worst
                        / Math.max(0.05, source.type.baseline()) - 1.0);
                double migrationOverhead = 30.0 * task.stateSizeGb
                        + 20.0 + 0.01 * task.remainingSeconds;
                if (target != null && (migrationOverhead < expectedDelay
                        || predicted < 0.0)) {
                    source.tasks.remove(task);
                    target.tasks.add(task);
                    task.vmId = target.id;
                    task.remainingSeconds += migrationOverhead;
                    task.lastMigrationTime = time;
                    source.lastMigrationTime = time;
                    migrations++;
                    load = aggregate(source.tasks);
                    predicted = predictedCredits(source, load.worst);
                    if (predicted >= config.minimumCredits()
                            || load.worst <= source.type.baseline()) break;
                }
            }
        }
    }

    private VirtualMachine bestMigrationTarget(VirtualMachine source, Task task,
                                               double time) {
        Candidate best = null;
        for (VirtualMachine vm : vms) {
            if (vm == source || !vm.active()) continue;
            Candidate candidate = candidate(vm, task, time);
            if (candidate.feasible && (best == null || candidate.score < best.score)) {
                best = candidate;
            }
        }
        if (best != null) return best.vm;
        Aggregate sourceLoad = aggregate(source.tasks);
        double penalty = task.remainingSeconds * Math.max(0.0,
                sourceLoad.worst / Math.max(0.05, source.type.baseline()) - 1.0);
        double overhead = 30.0 * task.stateSizeGb + 20.0
                + 0.01 * task.remainingSeconds;
        return overhead < penalty ? launchVm(task) : null;
    }

    private double recoverySeconds(VirtualMachine vm) {
        return Math.max(config.intervalSeconds(),
                (config.safeCredits() - config.minimumCredits())
                        / vm.type.accrualPerMinute() * 60.0);
    }

    private double predictedCredits(VirtualMachine vm, double demand) {
        double horizonMinutes = config.lookaheadHorizon()
                * config.intervalSeconds() / 60.0;
        double projected = vm.credits
                + vm.type.accrualPerMinute() * horizonMinutes
                - Math.max(0.0, demand - vm.type.baseline())
                * horizonMinutes;
        return Math.min(vm.type.maximumCredits(), projected);
    }

    private int executeInterval(double time, int intervalIndex) {
        int completed = 0;
        for (VirtualMachine vm : vms) {
            if (!vm.active()) continue;
            vm.activeSeconds += config.intervalSeconds();
            if (vm.tasks.isEmpty()) {
                vm.idleIntervals++;
                continue;
            }
            Map<Task, Double> observations = new HashMap<>();
            double totalUtilization = 0.0;
            for (Task task : vm.tasks) {
                double value = utilization(task, time, intervalIndex, vm.tasks.size());
                observations.put(task, value);
                totalUtilization += value;
            }
            Aggregate predictiveBound = aggregate(vm.tasks);
            robustBoundEvaluations++;
            if (totalUtilization > predictiveBound.worst + 1e-9) {
                robustBoundExceedances++;
            }
            double minutes = config.intervalSeconds() / 60.0;
            double accrual = vm.type.accrualPerMinute() * minutes;
            double consumption = Math.max(0.0,
                    totalUtilization - vm.type.baseline()) * minutes;
            double available = vm.credits + accrual;
            double speed = 1.0;
            boolean exhausted = consumption > available + 1e-9;
            if (exhausted && totalUtilization > vm.type.baseline()) {
                double burstFraction = Math.max(0.0,
                        Math.min(1.0, available / Math.max(1e-9, consumption)));
                double throttledSpeed = vm.type.baseline() / totalUtilization;
                speed = burstFraction + (1.0 - burstFraction) * throttledSpeed;
                throttledVmSeconds += config.intervalSeconds() * (1.0 - burstFraction);
                if (!vm.exhaustedLastInterval) creditExhaustions++;
            }
            vm.credits = Math.max(0.0, Math.min(vm.type.maximumCredits(),
                    vm.credits + accrual - consumption));
            vm.exhaustedLastInterval = exhausted;

            List<Task> finished = new ArrayList<>();
            for (Task task : vm.tasks) {
                double observation = observations.get(task);
                BayesianEstimator.update(task, observation, config);
                double before = task.remainingSeconds;
                task.remainingSeconds -= config.intervalSeconds() * speed;
                if (task.remainingSeconds <= 1e-9) {
                    double fraction = before
                            / Math.max(1e-9, config.intervalSeconds() * speed);
                    task.completionTime = time + config.intervalSeconds()
                            * Math.min(1.0, fraction);
                    task.state = Task.State.FINISHED;
                    task.vmId = -1;
                    finished.add(task);
                }
            }
            vm.tasks.removeAll(finished);
            completed += finished.size();
            if (vm.tasks.isEmpty()) vm.idleIntervals++;
        }
        return completed;
    }

    private double utilization(Task task, double time, int intervalIndex,
                               int colocatedTasks) {
        double progress = task.progress();
        double base = task.profileMean;
        double phase = switch (task.pattern) {
            case STEP -> progress < 0.45 ? -0.16 : 0.22;
            case RAMP -> -0.18 + 0.36 * progress;
            case BURSTY -> ((intervalIndex + task.id) % 5 <= 1) ? 0.28 : -0.12;
            case TRACE_DRIVEN -> 0.16 * Math.sin(0.71 * intervalIndex
                    + task.id * 0.37) + 0.08 * Math.sin(1.91 * intervalIndex);
        };
        long mixed = mix64(config.seed() ^ ((long) task.id << 32) ^ intervalIndex);
        double noise = (((mixed >>> 11) * 0x1.0p-53) - 0.5) * 0.18;
        double interference = Math.min(0.18, Math.max(0, colocatedTasks - 1) * 0.018);
        return clamp(base + phase + noise + interference, 0.02, 1.0);
    }

    private void retireIdleVms() {
        for (VirtualMachine vm : vms) {
            if (vm.active() && vm.tasks.isEmpty() && vm.idleIntervals >= 1) {
                vm.retired = true;
            }
        }
    }

    private void recordCreditTrajectory(double time) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        int count = 0;
        for (VirtualMachine vm : vms) {
            if (!vm.active()) continue;
            min = Math.min(min, vm.credits);
            max = Math.max(max, vm.credits);
            sum += vm.credits;
            count++;
        }
        if (count > 0) {
            trajectory.add(new SimulationResult.CreditPoint(time,
                    min, sum / count, max));
        }
    }

    private static long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private record Aggregate(double mean, double worst) {}
    private record Candidate(VirtualMachine vm, boolean feasible, double score) {}
}
