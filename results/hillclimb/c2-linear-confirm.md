> **INVALID TIMING RUN:** The runtime classpath omitted `library/target/classes`, so the candidate production class was not loaded. Do not use these results for a candidate decision. See [harness-classpath-correction.md](harness-classpath-correction.md).

# C2 linear-search confirmation results

Raw JMH JSON: `c2-linear-confirm.json`. Scores are ns/op; deltas compare linear candidate against the frozen 57ea447 binary baseline. Two forks, 3x1s warmup/5x1s measurement; score error is JMH error estimate.

| Operation | Size | Keys | Baseline | Candidate | Delta |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 20.681 ± 2.544 | 19.975 ± 4.432 | -3.4% |
| hit | 4096 | fullWidth | 19.279 ± 4.343 | 20.417 ± 3.899 | +5.9% |
| hit | 262144 | dense | 97.124 ± 0.691 | 99.568 ± 0.892 | +2.5% |
| hit | 262144 | fullWidth | 98.866 ± 2.679 | 100.763 ± 2.786 | +1.9% |
| miss | 4096 | dense | 20.179 ± 4.277 | 21.218 ± 3.621 | +5.2% |
| miss | 4096 | fullWidth | 20.566 ± 3.749 | 19.873 ± 5.781 | -3.4% |
| miss | 262144 | dense | 97.803 ± 0.523 | 99.963 ± 1.392 | +2.2% |
| miss | 262144 | fullWidth | 99.024 ± 0.573 | 100.142 ± 1.559 | +1.1% |
| range64 | 4096 | dense | 61.992 ± 2.784 | 61.942 ± 3.220 | -0.1% |
| range64 | 4096 | fullWidth | 62.997 ± 3.938 | 63.182 ± 3.580 | +0.3% |
| range64 | 262144 | dense | 125.240 ± 6.164 | 124.166 ± 4.589 | -0.9% |
| range64 | 262144 | fullWidth | 125.396 ± 9.835 | 125.155 ± 6.466 | -0.2% |
| overwrite | 4096 | dense | 21.223 ± 4.114 | 23.269 ± 5.378 | +9.6% |
| overwrite | 4096 | fullWidth | 22.713 ± 4.960 | 21.243 ± 5.112 | -6.5% |
| overwrite | 262144 | dense | 103.096 ± 0.695 | 103.607 ± 2.081 | +0.5% |
| overwrite | 262144 | fullWidth | 104.010 ± 1.385 | 104.693 ± 0.836 | +0.7% |
| churn | 4096 | dense | 114.031 ± 3.194 | 120.602 ± 1.980 | +5.8% |
| churn | 4096 | fullWidth | 112.692 ± 1.552 | 116.176 ± 0.625 | +3.1% |
| churn | 262144 | dense | 195.698 ± 2.028 | 204.306 ± 0.763 | +4.4% |
| churn | 262144 | fullWidth | 198.533 ± 1.190 | 205.636 ± 3.436 | +3.6% |
