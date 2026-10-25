# C4 degree-32 guarded screen and retained footprint

Frozen baseline ran first, then candidate. JDK 25, one fork, 1x250ms warmup/2x250ms measurement, `-foe true`, per-fork tree/Node fingerprints, 20 finite rows each. This is a screen, not significance evidence.

| Operation | Size | Keys | Baseline ns/op | Candidate ns/op | Delta |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 26.541 | 27.375 | +3.1% |
| hit | 4096 | fullWidth | 24.600 | 27.880 | +13.3% |
| hit | 262144 | dense | 97.000 | 97.901 | +0.9% |
| hit | 262144 | fullWidth | 99.082 | 97.286 | -1.8% |
| miss | 4096 | dense | 22.195 | 23.386 | +5.4% |
| miss | 4096 | fullWidth | 22.621 | 26.867 | +18.8% |
| miss | 262144 | dense | 96.412 | 94.696 | -1.8% |
| miss | 262144 | fullWidth | 96.919 | 96.065 | -0.9% |
| range64 | 4096 | dense | 61.660 | 58.247 | -5.5% |
| range64 | 4096 | fullWidth | 64.806 | 58.057 | -10.4% |
| range64 | 262144 | dense | 122.875 | 108.071 | -12.0% |
| range64 | 262144 | fullWidth | 118.458 | 108.944 | -8.0% |
| overwrite | 4096 | dense | 27.988 | 24.857 | -11.2% |
| overwrite | 4096 | fullWidth | 30.140 | 24.088 | -20.1% |
| overwrite | 262144 | dense | 101.328 | 99.635 | -1.7% |
| overwrite | 262144 | fullWidth | 100.787 | 98.874 | -1.9% |
| churn | 4096 | dense | 115.190 | 123.944 | +7.6% |
| churn | 4096 | fullWidth | 113.878 | 125.388 | +10.1% |
| churn | 262144 | dense | 199.256 | 333.418 | +67.3% |
| churn | 262144 | fullWidth | 198.222 | 315.421 | +59.1% |
