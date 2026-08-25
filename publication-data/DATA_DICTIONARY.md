# Data dictionary

## Common per-seed result columns

| Column | Definition |
|---|---|
| `policy` | Scheduling policy evaluated on the paired workload seed. |
| `seed` | Deterministic workload and utilization seed. |
| `cost_usd` | Simulated VM price multiplied by active duration, in USD. |
| `violation_rate` | Fraction of workflows completing after their deadlines. |
| `total_lateness_seconds` | Sum of positive workflow deadline overruns. |
| `credit_exhaustions` | Count of intervals entering credit-deficient execution. |
| `throttled_vm_hours` | Integrated credit-deficient VM exposure in hours. |
| `robust_bound_evaluations` | Active VM intervals for which predictive coverage was evaluated. |
| `robust_bound_exceedances` | Evaluations where realized aggregate utilization exceeded the pre-update bound. |
| `bound_exceedance_rate` | `robust_bound_exceedances / robust_bound_evaluations`. |
| `migrations` | Completed task migrations. |
| `scheduler_runtime_seconds` | Diagnostic scheduler timing; not used for manuscript scalability claims. |
| `maximum_vms` | Maximum concurrently active VMs. |
| `makespan_seconds` | Completion time of the final workflow. |
| `note` | Optional provenance or run annotation. |

## File-specific columns

- `stress_results.csv`: `stress` is `LIGHT`, `MODERATE`, or `HEAVY`.
- `sensitivity_results.csv`: `parameter` and `value` identify the varied
  control while all unspecified controls retain their default values.
- `sensitivity_summary.csv`: `_mean` and `_sd` fields are the arithmetic mean
  and sample standard deviation over 20 paired seeds.
- `scalability_results.csv`: `tasks` is total task count; `repetitions` is the
  number of measured runs; runtime fields are mean and sample standard
  deviation of scheduler thread CPU time.
- `credit_trajectory_ubr_ca.csv`: `time_seconds` and `time_hours` identify the
  scheduling epoch; `minimum`, `mean`, and `maximum` are credits across active
  VMs. Credits are measured in vCPU-minutes.

## Statistical interpretation

All manuscript uncertainty summaries are mean plus or minus one sample standard
deviation across seeds. Inferential comparisons are paired by seed. When every
paired difference is zero, the paired t-test, Wilcoxon signed-rank test, and
paired Cohen's d-z are undefined and are reported as `N/A`.
