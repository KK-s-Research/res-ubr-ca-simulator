# UBR-CA Java reproducibility project

This Maven/Java 21 project implements the manuscript's Unified
Bayesian-Robust Credit-Aware Scheduler (UBR-CA) as a discrete-event
simulator. It executes workflow DAGs on simulated burstable VMs, learns
task CPU demand online, enforces robust capacity and multi-step credit
constraints, migrates containers proactively, evaluates the manuscript
baselines and ablations, and generates every table and figure directly
from raw per-seed results.

## Reproduce the manuscript experiment

Requirements: Java 21 and Maven 3.9 or newer.

```bash
mvn clean test package
java -Djava.awt.headless=true -jar target/res-ubr-ca-simulator-1.0.0.jar --full
```

The full mode uses 20 paired random seeds, as specified in the paper.
The exact publication datasets are versioned under `publication-data/`, including
the per-seed raw CSV files, derived table CSV files, experiment configuration,
seed schedule, provenance notes, and data dictionary.

The exact generated workflow and task inputs can be regenerated without running
the schedulers:

```bash
java -jar target/res-ubr-ca-simulator-1.0.0.jar \
  --full --export-task-inputs --output publication-data
```

This writes compressed CSV archives and a SHA-256 manifest under
`publication-data/inputs/`.
For a fast verification:

```bash
java -Djava.awt.headless=true -jar target/res-ubr-ca-simulator-1.0.0.jar \
  --quick --output output-quick
```

For a larger publication-scale experiment:

```bash
java -Djava.awt.headless=true -jar target/res-ubr-ca-simulator-1.0.0.jar \
  --large --output output-large
```

The scale can also be overridden directly:

```bash
java -Djava.awt.headless=true -jar target/res-ubr-ca-simulator-1.0.0.jar \
  --full --workflows 16 --tasks 180 --stress MODERATE --output output-large
```

PowerShell users may run:

```powershell
.\scripts\run-experiments.ps1 -Mode full -Output output
```

## Generated artifacts

The output directory contains:

- `raw/overall_results.csv`: every seed and method used in Tables IV,
  V, and VII and Figure 1.
- `raw/ablation_results.csv`: no-migration and no-Bayesian variants.
- `raw/sensitivity_results.csv`: all values from manuscript Table III.
- `raw/scalability_results.csv`: sizes from 50 to 50,000 tasks.
- `tables/`: manuscript Tables I-VIII in CSV, Markdown, and LaTeX
  (`booktabs`) formats, with compatibility aliases for older draft
  numbering.
- `figures/`: PNG and editable SVG plots for manuscript Figures 2-7,
  including the combined sensitivity panel required by the July 9 draft.
- `scripts/plot_publication_figures.py`: generated Python/matplotlib
  plotting script for cleaner journal-ready PNG, SVG, and PDF figures.
- `figures-python/`: created when the Python plotting script is run.
- `experiment_config.json`: the complete run configuration and workload
  provenance.
- `RESULTS.md`: a concise result summary and interpretation boundary.

The generated `output*` directories remain ignored because they contain
duplicate figures, scripts, and temporary artifacts. The compact, authoritative
CSV dataset used by the manuscript is committed separately in
`publication-data/`.

Sensitivity to the scheduling interval is reported using throttled VM-hours,
a duration-based metric that is comparable across interval lengths; exhaustion
episode counts remain available in the raw CSV files.

All reported table entries are `mean ± sample standard deviation`.
Paired t-tests, Wilcoxon signed-rank tests, and paired Cohen's *d* use
seed-matched samples.

## Generate improved Python figures

After any experiment run, generate cleaner publication figures with:

```bash
python output-large/scripts/plot_publication_figures.py --output output-large
```

The Python script reads the generated raw CSV files and writes
high-resolution PNG, SVG, and PDF figures to `output-large/figures-python`.
It requires `matplotlib` and `numpy`.

## Implemented methods

- HEFT-style earliest-finish placement
- Kubernetes resource packing (KRP)
- Energy-aware VM consolidation (EA-VC)
- Chance-constrained scheduling (CCS)
- Credit-aware reactive scheduling (CARS)
- internal strict-feasibility diagnostic (excluded from publication comparisons because it is not an exact branch-and-bound solver)
- UBR-CA without credit-aware migration
- UBR-CA without Bayesian learning
- full UBR-CA

The implementation follows Equations (16)-(18) for the conjugate
Gaussian update, Equation (23) for aggregate worst-case utilization,
Equations (25) and (28) for robust feasibility, Equation (53) for credit
prediction, and Algorithm 1 for the online control loop.

Credit is measured in vCPU-minutes. CPU demand is measured in vCPU.
Time is measured in seconds. VM types, prices, baseline fractions,
initial credit balances, and accrual rates are declared in
`Simulator.java` so they can be replaced with the exact cloud region and
instance family used in a deployment.

## Workloads and real trace import

Without `--trace`, the suite produces benchmark-shaped Montage,
Epigenomics, LIGO, and Alibaba-shaped DAGs with the four utilization
models specified in the paper: step, ramp, bursty, and trace-driven.
Those runs are synthetic experiments and must be described that way.
They must not be presented as Alibaba trace measurements.

To replay a real or preprocessed trace:

```bash
java -jar target/res-ubr-ca-simulator-1.0.0.jar --full \
  --trace data/alibaba-workflows.csv --output output-alibaba
```

Required CSV columns:

```text
workflow_id,task_id,arrival_time,duration,cpu_utilization,predecessors
```

Optional columns:

```text
benchmark,deadline,pattern,state_size_gb
```

`predecessors` is a pipe-separated list of task IDs (for example
`12|15`). `pattern` is one of `STEP`, `RAMP`, `BURSTY`, or
`TRACE_DRIVEN`. See `data/example-trace.csv`.

## Reproducibility notes

- A workload is generated once per seed and cloned for every method, so
  comparisons are paired.
- Utilization noise is a deterministic function of seed, task, and
  interval. A scheduling method cannot receive a different random trace
  merely because it made a different number of random calls.
- Raw results are never overwritten with hand-entered manuscript
  values.
- The exact global mixed-integer model is NP-hard. The internal strict-feasibility
  diagnostic is not an exact branch-and-bound or MILP solver and is excluded
  from all publication comparisons.
- The simulator models CPU as the bottleneck, matching the manuscript.
  Memory, storage, image-pull delay, and provider-specific unlimited-mode
  surcharge are outside the stated model.

## Source layout

```text
src/main/java/in/ac/caluniv/cse/ubrca/
  Main.java                 command-line entry point
  Simulator.java            Algorithm 1 and burstable-VM simulation
  BayesianEstimator.java    posterior update and normal quantile
  WorkloadGenerator.java    benchmark DAGs and CSV trace importer
  ExperimentRunner.java     paired repetitions and study design
  Statistics.java           t-test, Wilcoxon test, Cohen's d
  ArtifactWriter.java       raw data and manuscript tables
  ChartWriter.java          dependency-free PNG/SVG charts
```
