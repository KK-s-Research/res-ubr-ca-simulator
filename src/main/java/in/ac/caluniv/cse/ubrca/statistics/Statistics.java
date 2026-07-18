package in.ac.caluniv.cse.ubrca.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Statistics {
    private Statistics() {}

    public record Summary(double mean, double standardDeviation) {
        public String formatted(int decimals) {
            return ("%." + decimals + "f ± %." + decimals + "f")
                    .formatted(mean, standardDeviation);
        }
    }

    public record PairedComparison(double tTestP, double wilcoxonP,
                                   double cohensD) {}

    public static Summary summarize(List<Double> values) {
        if (values.isEmpty()) return new Summary(Double.NaN, Double.NaN);
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = values.size() < 2 ? 0.0 : values.stream()
                .mapToDouble(x -> (x - mean) * (x - mean)).sum() / (values.size() - 1);
        return new Summary(mean, Math.sqrt(variance));
    }

    public static PairedComparison compare(List<Double> treatment,
                                           List<Double> control) {
        if (treatment.size() != control.size() || treatment.size() < 2) {
            throw new IllegalArgumentException("Paired samples must have equal size >= 2");
        }
        List<Double> differences = new ArrayList<>();
        for (int i = 0; i < treatment.size(); i++) {
            differences.add(treatment.get(i) - control.get(i));
        }
        Summary summary = summarize(differences);
        double t = summary.standardDeviation == 0.0
                ? (summary.mean == 0.0 ? 0.0 : Double.POSITIVE_INFINITY)
                : summary.mean / (summary.standardDeviation / Math.sqrt(differences.size()));
        double tP = Double.isInfinite(t) ? 0.0
                : regularizedBeta(differences.size() - 1.0,
                differences.size() - 1.0 + t * t, 0.5 * (differences.size() - 1.0), 0.5);
        double d = summary.standardDeviation == 0.0
                ? 0.0 : summary.mean / summary.standardDeviation;
        return new PairedComparison(clamp(tP), wilcoxon(differences), d);
    }

    private static double wilcoxon(List<Double> rawDifferences) {
        List<Ranked> ranked = new ArrayList<>();
        for (double value : rawDifferences) {
            if (Math.abs(value) > 1e-12) ranked.add(new Ranked(Math.abs(value), value > 0));
        }
        if (ranked.isEmpty()) return 1.0;
        ranked.sort(Comparator.comparingDouble(Ranked::absolute));
        double positive = 0.0;
        int position = 1;
        while (position <= ranked.size()) {
            int end = position;
            double value = ranked.get(position - 1).absolute;
            while (end < ranked.size()
                    && Math.abs(ranked.get(end).absolute - value) < 1e-12) end++;
            double rank = (position + end) / 2.0;
            for (int i = position - 1; i < end; i++) {
                if (ranked.get(i).positive) positive += rank;
            }
            position = end + 1;
        }
        int n = ranked.size();
        double mean = n * (n + 1.0) / 4.0;
        double variance = n * (n + 1.0) * (2.0 * n + 1.0) / 24.0;
        double z = (Math.abs(positive - mean) - 0.5) / Math.sqrt(variance);
        return clamp(2.0 * (1.0 - normalCdf(z)));
    }

    private static double normalCdf(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double density = 0.3989422804014327 * Math.exp(-z * z / 2.0);
        double probability = 1.0 - density * t
                * (0.319381530 + t * (-0.356563782
                + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        return z >= 0 ? probability : 1.0 - probability;
    }

    /*
     * Computes I_x(a,b), used here through the identity for a two-sided
     * Student-t probability. Numerical Recipes continued fraction.
     */
    private static double regularizedBeta(double numerator, double denominator,
                                          double a, double b) {
        double x = numerator / denominator;
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        double logFront = logGamma(a + b) - logGamma(a) - logGamma(b)
                + a * Math.log(x) + b * Math.log(1.0 - x);
        double front = Math.exp(logFront);
        if (x < (a + 1.0) / (a + b + 2.0)) {
            return front * betaFraction(x, a, b) / a;
        }
        return 1.0 - front * betaFraction(1.0 - x, b, a) / b;
    }

    private static double betaFraction(double x, double a, double b) {
        final int maxIterations = 200;
        final double epsilon = 3e-14;
        final double tiny = 1e-300;
        double qab = a + b;
        double qap = a + 1.0;
        double qam = a - 1.0;
        double c = 1.0;
        double d = 1.0 - qab * x / qap;
        if (Math.abs(d) < tiny) d = tiny;
        d = 1.0 / d;
        double h = d;
        for (int m = 1; m <= maxIterations; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < tiny) d = tiny;
            c = 1.0 + aa / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1.0 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x
                    / ((a + m2) * (qap + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < tiny) d = tiny;
            c = 1.0 + aa / c;
            if (Math.abs(c) < tiny) c = tiny;
            d = 1.0 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1.0) < epsilon) break;
        }
        return h;
    }

    private static double logGamma(double x) {
        double[] coefficients = {676.5203681218851, -1259.1392167224028,
                771.32342877765313, -176.61502916214059,
                12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7};
        if (x < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * x))
                    - logGamma(1.0 - x);
        }
        x -= 1.0;
        double sum = 0.99999999999980993;
        for (int i = 0; i < coefficients.length; i++) {
            sum += coefficients[i] / (x + i + 1.0);
        }
        double t = x + coefficients.length - 0.5;
        return 0.5 * Math.log(2.0 * Math.PI) + (x + 0.5) * Math.log(t)
                - t + Math.log(sum);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Ranked(double absolute, boolean positive) {}
}
