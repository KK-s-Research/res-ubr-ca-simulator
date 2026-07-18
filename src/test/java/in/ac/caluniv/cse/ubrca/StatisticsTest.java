package in.ac.caluniv.cse.ubrca;

import in.ac.caluniv.cse.ubrca.statistics.Statistics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsTest {
    @Test
    void summaryUsesSampleStandardDeviation() {
        Statistics.Summary summary = Statistics.summarize(List.of(1.0, 2.0, 3.0));
        assertEquals(2.0, summary.mean(), 1e-12);
        assertEquals(1.0, summary.standardDeviation(), 1e-12);
    }

    @Test
    void pairedComparisonDetectsConsistentImprovement() {
        Statistics.PairedComparison comparison = Statistics.compare(
                List.of(1.0, 1.2, 0.9, 1.1, 1.0, 0.8),
                List.of(2.0, 2.1, 1.8, 2.2, 1.9, 2.0));
        assertTrue(comparison.tTestP() < 0.01);
        assertTrue(comparison.cohensD() < -2.0);
    }
}
