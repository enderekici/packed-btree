# C2 linear-search screening results

Raw JMH JSON: `c2-linear-screen.json`. Scores are ns/op; deltas compare linear candidate against the frozen 57ea447 binary baseline. Single-fork screen; results are exploratory and not significance estimates.

| Operation | Size | Keys | Baseline | Candidate | Delta |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 26.640 | 28.275 | +6.1% |
| hit | 4096 | fullWidth | 29.747 | 24.960 | -16.1% |
| hit | 262144 | dense | 98.978 | 97.203 | -1.8% |
| hit | 262144 | fullWidth | 97.270 | 99.093 | +1.9% |
| miss | 4096 | dense | 27.201 | 22.382 | -17.7% |
| miss | 4096 | fullWidth | 27.106 | 25.900 | -4.4% |
| miss | 262144 | dense | 97.545 | 96.330 | -1.2% |
| miss | 262144 | fullWidth | 96.270 | 97.750 | +1.5% |
| range64 | 4096 | dense | 64.146 | 62.461 | -2.6% |
| range64 | 4096 | fullWidth | 63.599 | 63.423 | -0.3% |
| range64 | 262144 | dense | 128.277 | 127.548 | -0.6% |
| range64 | 262144 | fullWidth | 132.922 | 125.122 | -5.9% |
| overwrite | 4096 | dense | 30.145 | 26.060 | -13.5% |
| overwrite | 4096 | fullWidth | 27.758 | 26.842 | -3.3% |
| overwrite | 262144 | dense | 100.982 | 101.585 | +0.6% |
| overwrite | 262144 | fullWidth | 100.529 | 100.536 | +0.0% |
| churn | 4096 | dense | 114.606 | 115.110 | +0.4% |
| churn | 4096 | fullWidth | 114.312 | 115.491 | +1.0% |
| churn | 262144 | dense | 200.729 | 197.886 | -1.4% |
| churn | 262144 | fullWidth | 203.229 | 197.575 | -2.8% |
