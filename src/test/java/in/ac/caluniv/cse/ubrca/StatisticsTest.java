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
        assertTrue(comparison.pairedCohensDz() < -2.0);
        assertTrue(comparison.confidenceUpper() < 0.0);
        assertTrue(comparison.confidenceLower() < comparison.meanDifference());
        assertTrue(comparison.meanDifference() < comparison.confidenceUpper());
    }

    @Test
    void identicalPairedSamplesHaveNoDefinedInferentialTests() {
        Statistics.PairedComparison comparison = Statistics.compare(
                List.of(1.0, 2.0, 3.0, 4.0),
                List.of(1.0, 2.0, 3.0, 4.0));
        assertEquals(0.0, comparison.meanDifference(), 1e-12);
        assertEquals(0.0, comparison.confidenceLower(), 1e-12);
        assertEquals(0.0, comparison.confidenceUpper(), 1e-12);
        assertTrue(Double.isNaN(comparison.tTestP()));
        assertTrue(Double.isNaN(comparison.wilcoxonP()));
        assertTrue(Double.isNaN(comparison.pairedCohensDz()));
    }}
