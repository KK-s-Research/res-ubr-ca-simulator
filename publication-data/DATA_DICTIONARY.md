# Data dictionary

## Generated task-input archives

Files under `inputs/` are gzip-compressed CSV files. Each data row describes
one generated task; workflow-level fields are repeated so an archive is
self-contained.

| Column | Definition |
|---|---|
| `scenario` | Experiment input set represented by the row. |
| `repetition_index` | Zero-based repetition within that input set. |
| `seed` | Deterministic workload-generation seed. |
| `interval_seconds` | Scheduling interval used for the run. |
| `stress` | Configured synthetic credit-stress regime. |
| `configured_workflows` | Requested number of workflows. |
| `configured_tasks_per_workflow` | Requested nominal tasks per workflow. |
| `workflow_id` | Generated workflow identifier. |
| `benchmark` | Synthetic DAG family assigned to the workflow. |
| `workflow_arrival_seconds` | Workflow release time. |
| `workflow_deadline_seconds` | Absolute workflow deadline. |
| `task_id` | Generated task identifier. |
| `duration_seconds` | Nominal task duration. |
| `profile_mean_vcpu` | Mean vCPU demand specified by the generated profile. |
| `pattern` | Generated utilization-pattern family. |
| `state_size_gb` | Task state size used by migration modeling. |
| `criticality` | Task criticality class. |
| `predecessors` | Vertical-bar-separated predecessor task identifiers. |

`inputs/manifest.csv` records the number of data rows, compressed byte count,
SHA-256 digest, and manuscript purpose of every archive. It is the integrity
index for the retained generated inputs.

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
