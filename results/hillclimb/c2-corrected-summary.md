# C2 corrected guarded screen

Frozen baseline arm ran first; linear candidate ran second. JDK 25, one fork, 1x250ms warmup/2x250ms measurement, `-foe true`, corrected classpath, per-fork tree/Node fingerprints, and 20 finite JSON rows per arm. This is a coarse screen, not significance evidence.

| Operation | Size | Keys | Baseline ns/op | Candidate ns/op | Delta |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 23.989 | 8.912 | -62.8% |
| hit | 4096 | fullWidth | 26.839 | 9.036 | -66.3% |
| hit | 262144 | dense | 97.092 | 67.437 | -30.5% |
| hit | 262144 | fullWidth | 98.035 | 68.334 | -30.3% |
| miss | 4096 | dense | 22.702 | 9.070 | -60.0% |
| miss | 4096 | fullWidth | 28.043 | 9.097 | -67.6% |
| miss | 262144 | dense | 97.233 | 68.496 | -29.6% |
| miss | 262144 | fullWidth | 96.944 | 69.364 | -28.4% |
| range64 | 4096 | dense | 63.934 | 49.122 | -23.2% |
| range64 | 4096 | fullWidth | 64.793 | 49.652 | -23.4% |
| range64 | 262144 | dense | 123.987 | 98.743 | -20.4% |
| range64 | 262144 | fullWidth | 121.719 | 94.544 | -22.3% |
| overwrite | 4096 | dense | 27.437 | 10.048 | -63.4% |
| overwrite | 4096 | fullWidth | 27.020 | 10.332 | -61.8% |
| overwrite | 262144 | dense | 100.702 | 70.129 | -30.4% |
| overwrite | 262144 | fullWidth | 101.591 | 71.174 | -29.9% |
| churn | 4096 | dense | 116.332 | 86.276 | -25.8% |
| churn | 4096 | fullWidth | 114.529 | 85.617 | -25.2% |
| churn | 262144 | dense | 199.253 | 144.656 | -27.4% |
| churn | 262144 | fullWidth | 198.913 | 146.160 | -26.5% |
