# Revised experimental section

Use `section_vii_experimental_setup_results_discussion.tex` to replace
the manuscript's current Section VII.

The section requires these LaTeX packages:

```latex
\usepackage{amsmath}
\usepackage{booktabs}
\usepackage{graphicx}
```

Figure paths assume that the main manuscript is compiled from the
repository root. If the section is copied into another project, copy
`output/figures/*.png` with it and update the `\includegraphics` paths.

The rewrite includes:

- the experimental protocol and workload provenance;
- benchmark, baseline, metric, and statistical definitions;
- eight populated tables with no `XX` placeholders;
- nine included figure panels;
- quantitative cost, deadline, credit-stress, ablation, sensitivity,
  Bayesian-convergence, and scalability analyses;
- corrected interpretation of effect-size direction; and
- limitations covering synthetic workloads, the B&B proxy, CPU-only
  modeling, and hardware-dependent runtime.

The reported synthetic results show a 1.25% UBR-CA cost premium over
HEFT, not a cost reduction. Do not restore the previous lower-cost
claim unless a real trace-backed rerun supports it.
