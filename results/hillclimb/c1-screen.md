> **INVALID TIMING RUN:** The runtime classpath omitted `library/target/classes`, so the candidate production class was not loaded. Do not use these results for a candidate decision. See [harness-classpath-correction.md](harness-classpath-correction.md).

# C1 screening results

Raw JMH JSON: `c1-screen.json`. Single-fork screening only; scores are ns/op and deltas are candidate versus frozen 57ea447 baseline. They are not significance estimates.

| Operation | Size | Keys | Baseline | Candidate | Delta |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 23.312 | 27.100 | +16.2% |
| hit | 4096 | fullWidth | 28.352 | 30.952 | +9.2% |
| hit | 262144 | dense | 97.357 | 97.332 | -0.0% |
| hit | 262144 | fullWidth | 98.382 | 99.036 | +0.7% |
| miss | 4096 | dense | 25.791 | 22.579 | -12.5% |
| miss | 4096 | fullWidth | 26.231 | 28.672 | +9.3% |
| miss | 262144 | dense | 97.659 | 96.965 | -0.7% |
| miss | 262144 | fullWidth | 97.798 | 96.811 | -1.0% |
| range64 | 4096 | dense | 65.330 | 64.131 | -1.8% |
| range64 | 4096 | fullWidth | 62.054 | 64.261 | +3.6% |
| range64 | 262144 | dense | 126.113 | 128.704 | +2.1% |
| range64 | 262144 | fullWidth | 131.458 | 121.135 | -7.9% |
| overwrite | 4096 | dense | 26.951 | 24.751 | -8.2% |
| overwrite | 4096 | fullWidth | 29.773 | 25.364 | -14.8% |
| overwrite | 262144 | dense | 103.095 | 101.492 | -1.6% |
| overwrite | 262144 | fullWidth | 101.750 | 103.351 | +1.6% |
| churn | 4096 | dense | 114.679 | 114.630 | -0.0% |
| churn | 4096 | fullWidth | 113.601 | 114.842 | +1.1% |
| churn | 262144 | dense | 197.114 | 199.506 | +1.2% |
| churn | 262144 | fullWidth | 198.421 | 197.733 | -0.3% |
