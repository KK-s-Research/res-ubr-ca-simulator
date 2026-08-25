# Publication dataset

This directory contains the complete numerical dataset used for the manuscript
results, tables, and figures for the 20-seed synthetic UBR-CA experiment.
No external production trace was used. The workload inputs were generated
deterministically by the committed `WorkloadGenerator` from the configuration
and seed rules recorded here.

## Contents

- `config/experiment_config.json`: default manuscript configuration and
  generation timestamp.
- `config/seed_scheme.csv`: seed schedule for every experiment family.
- `raw/overall_results.csv`: six publication policies over 20 paired seeds.
- `raw/ablation_results.csv`: two ablations plus full UBR-CA over 20 seeds.
- `raw/stress_results.csv`: light, moderate, and heavy stress results.
- `raw/sensitivity_results.csv`: per-seed parameter-sweep results.
- `raw/sensitivity_summary.csv`: means and sample standard deviations for the
  parameter sweeps.
- `raw/scalability_results.csv`: ten-run scheduler thread-CPU-time summaries
  after three excluded JVM warm-up runs.
- `raw/credit_trajectory_ubr_ca.csv`: representative UBR-CA VM-credit
  trajectory used in the manuscript figure.
- `tables/*.csv`: derived CSV versions of all manuscript tables, including
  compatibility aliases retained for draft numbering.
- `DATA_DICTIONARY.md`: definitions, units, and interpretation boundaries.

## Reproduction

From the repository root:

```bash
mvn clean test package
java -Djava.awt.headless=true \
  -jar target/res-ubr-ca-simulator-1.0.0.jar \
  --full --output output-reproduced
python output-reproduced/scripts/plot_publication_figures.py \
  --output output-reproduced
```

The generator uses Java's deterministic `Random` sequence and the seed rule in
`config/seed_scheme.csv`. Each workload is generated once per seed and cloned
for every policy, preserving paired comparisons. The generated task definitions
are therefore reproducible from source, configuration, and seed without storing
millions of redundant task rows.

Floating-point results may differ in the final digits across JVMs or hardware.
The manuscript does not use the `scheduler_runtime_seconds` fields from the
overall, ablation, stress, or sensitivity files. Scheduler scalability is
reported only from `scalability_results.csv`, which contains replicated thread
CPU-time measurements.

## Provenance boundary

The words Montage, Epigenomics, LIGO, and Alibaba-shaped identify synthetic DAG
families implemented by the generator. They do not mean that an Alibaba or
other production trace was replayed. `data/example-trace.csv` documents the
optional importer format and was not used for the reported manuscript results.
