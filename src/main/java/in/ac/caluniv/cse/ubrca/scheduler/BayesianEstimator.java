package in.ac.caluniv.cse.ubrca.scheduler;

import in.ac.caluniv.cse.ubrca.config.ExperimentConfig;
import in.ac.caluniv.cse.ubrca.model.Task;

public final class BayesianEstimator {
    private BayesianEstimator() {}

    public static void update(Task task, double observation,
                              ExperimentConfig config) {
        task.samples++;
        task.sampleSum += observation;
        task.lastObservation = observation;
        double priorPrecision = 1.0 / config.priorVariance();
        double observationPrecision = task.samples / config.observationVariance();
        task.posteriorVariance = 1.0 / (priorPrecision + observationPrecision);
        task.posteriorMean = task.posteriorVariance
                * (task.profileMean * priorPrecision
                + task.sampleSum / config.observationVariance());
        task.posteriorMean = clamp(task.posteriorMean, 0.0, 1.0);
    }

    /**
     * Acklam's rational approximation of the inverse normal CDF.
     */
    public static double inverseNormal(double probability) {
        if (probability <= 0.0 || probability >= 1.0) {
            throw new IllegalArgumentException("Probability must be in (0,1)");
        }
        double[] a = {-39.69683028665376, 220.9460984245205,
                -275.9285104469687, 138.3577518672690,
                -30.66479806614716, 2.506628277459239};
        double[] b = {-54.47609879822406, 161.5858368580409,
                -155.6989798598866, 66.80131188771972,
                -13.28068155288572};
        double[] c = {-0.007784894002430293, -0.3223964580411365,
                -2.400758277161838, -2.549732539343734,
                4.374664141464968, 2.938163982698783};
        double[] d = {0.007784695709041462, 0.3224671290700398,
                2.445134137142996, 3.754408661907416};
        double pLow = 0.02425;
        double pHigh = 1.0 - pLow;
        if (probability < pLow) {
            double q = Math.sqrt(-2.0 * Math.log(probability));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q
                    + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
        }
        if (probability <= pHigh) {
            double q = probability - 0.5;
            double r = q * q;
            return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r
                    + a[4]) * r + a[5]) * q
                    / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r
                    + b[4]) * r + 1.0);
        }
        double q = Math.sqrt(-2.0 * Math.log(1.0 - probability));
        return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q
                + c[4]) * q + c[5])
                / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
