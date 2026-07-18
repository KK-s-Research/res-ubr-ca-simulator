package in.ac.caluniv.cse.ubrca;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.experiment.SimulationResult;
import in.ac.caluniv.cse.ubrca.model.Workflow;
import in.ac.caluniv.cse.ubrca.scheduler.SchedulerPolicy;
import in.ac.caluniv.cse.ubrca.scheduler.Simulator;
import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorTest {
    @Test
    void everyPolicyCompletesAWorkflowAndReturnsFiniteMetrics() {
        ExperimentConfig config = ExperimentConfig.defaults(Path.of("output"))
                .withScale(2, 12).withSeed(42);
        List<Workflow> workflows = WorkloadGenerator.generate(config);
        for (SchedulerPolicy policy : SchedulerPolicy.values()) {
            SimulationResult result = new Simulator(config, policy, workflows).run();
            assertTrue(Double.isFinite(result.cost()), policy.name());
            assertTrue(result.cost() > 0.0, policy.name());
            assertTrue(result.violationRate() >= 0.0
                    && result.violationRate() <= 1.0, policy.name());
            assertFalse(result.creditTrajectory().isEmpty(), policy.name());
        }
    }

    @Test
    void generatedDagHasUniqueTaskIdsAndAllDependenciesPointBackward() {
        ExperimentConfig config = ExperimentConfig.defaults(Path.of("output"))
                .withScale(4, 30).withSeed(7);
        List<Workflow> workflows = WorkloadGenerator.generate(config);
        long unique = workflows.stream().flatMap(w -> w.tasks.stream())
                .map(t -> t.id).distinct().count();
        long total = workflows.stream().mapToLong(w -> w.tasks.size()).sum();
        assertEquals(total, unique);
        assertTrue(workflows.stream().flatMap(w -> w.tasks.stream())
                .allMatch(t -> t.predecessors.stream().allMatch(p -> p < t.id)));
    }
}
