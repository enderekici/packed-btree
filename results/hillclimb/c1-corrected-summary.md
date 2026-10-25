# C1 corrected guarded screen

Candidate ran first (candidate JSON/log), then frozen baseline. Both are JDK 25, one fork, 1x250ms warmup/2x250ms measurement, `-foe true`, corrected classpath and per-fork tree/Node fingerprints. Each arm had 20 finite rows; this is coarse screening, not significance evidence.

| Operation | Size | Keys | Baseline ns/op | Candidate ns/op | Delta |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 27.817 | 26.867 | -3.4% |
| hit | 4096 | fullWidth | 23.684 | 24.893 | +5.1% |
| hit | 262144 | dense | 97.455 | 96.463 | -1.0% |
| hit | 262144 | fullWidth | 96.287 | 96.046 | -0.2% |
| miss | 4096 | dense | 23.954 | 29.815 | +24.5% |
| miss | 4096 | fullWidth | 30.219 | 29.668 | -1.8% |
| miss | 262144 | dense | 97.162 | 96.382 | -0.8% |
| miss | 262144 | fullWidth | 97.146 | 99.696 | +2.6% |
| range64 | 4096 | dense | 62.463 | 62.371 | -0.1% |
| range64 | 4096 | fullWidth | 62.545 | 62.959 | +0.7% |
| range64 | 262144 | dense | 124.163 | 123.636 | -0.4% |
| range64 | 262144 | fullWidth | 140.072 | 125.428 | -10.5% |
| overwrite | 4096 | dense | 25.413 | 33.656 | +32.4% |
| overwrite | 4096 | fullWidth | 26.199 | 27.162 | +3.7% |
| overwrite | 262144 | dense | 100.295 | 100.662 | +0.4% |
| overwrite | 262144 | fullWidth | 100.576 | 101.915 | +1.3% |
| churn | 4096 | dense | 116.949 | 116.828 | -0.1% |
| churn | 4096 | fullWidth | 115.020 | 116.927 | +1.7% |
| churn | 262144 | dense | 198.046 | 197.340 | -0.4% |
| churn | 262144 | fullWidth | 202.081 | 197.662 | -2.2% |
