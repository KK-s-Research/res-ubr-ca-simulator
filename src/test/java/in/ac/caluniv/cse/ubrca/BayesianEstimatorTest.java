package in.ac.caluniv.cse.ubrca;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Task;
import in.ac.caluniv.cse.ubrca.scheduler.BayesianEstimator;
import in.ac.caluniv.cse.ubrca.workload.WorkloadGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BayesianEstimatorTest {
    @Test
    void conjugateUpdateMatchesClosedFormEquations() {
        ExperimentConfig config = ExperimentConfig.defaults(Path.of("output"));
        Task task = new Task(1, 1, List.of(), 100, 0.5,
                WorkloadGenerator.Pattern.STEP, 0.5, 1.0,
                config.priorVariance());
        BayesianEstimator.update(task, 0.8, config);
        BayesianEstimator.update(task, 0.6, config);

        double expectedVariance = 1.0 / (1.0 / config.priorVariance()
                + 2.0 / config.observationVariance());
        double expectedMean = expectedVariance
                * (0.5 / config.priorVariance()
                + 1.4 / config.observationVariance());
        assertEquals(expectedVariance, task.posteriorVariance, 1e-12);
        assertEquals(expectedMean, task.posteriorMean, 1e-12);
    }

    @Test
    void normalQuantileMatchesNinetyFivePercentOneSidedValue() {
        assertEquals(1.6448536, BayesianEstimator.inverseNormal(0.95), 1e-6);
        assertTrue(BayesianEstimator.inverseNormal(0.99)
                > BayesianEstimator.inverseNormal(0.95));
    }
}
