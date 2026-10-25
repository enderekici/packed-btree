# C3 degree-8 guarded screen

Candidate ran first, then frozen degree-16 binary baseline. JDK 25, one fork, 1x250ms warmup/2x250ms measurement, `-foe true`, per-fork tree/Node fingerprints, 20 finite rows per arm. Screening only; no significance claim.

| Operation | Size | Keys | Baseline ns/op | Candidate ns/op | Delta |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 23.242 | 27.056 | +16.4% |
| hit | 4096 | fullWidth | 26.426 | 30.517 | +15.5% |
| hit | 262144 | dense | 98.254 | 109.496 | +11.4% |
| hit | 262144 | fullWidth | 96.080 | 110.492 | +15.0% |
| miss | 4096 | dense | 27.365 | 30.331 | +10.8% |
| miss | 4096 | fullWidth | 30.318 | 28.775 | -5.1% |
| miss | 262144 | dense | 96.734 | 109.396 | +13.1% |
| miss | 262144 | fullWidth | 97.484 | 109.626 | +12.5% |
| range64 | 4096 | dense | 64.595 | 82.506 | +27.7% |
| range64 | 4096 | fullWidth | 60.652 | 83.436 | +37.6% |
| range64 | 262144 | dense | 121.695 | 209.157 | +71.9% |
| range64 | 262144 | fullWidth | 128.591 | 208.663 | +62.3% |
| overwrite | 4096 | dense | 27.624 | 32.081 | +16.1% |
| overwrite | 4096 | fullWidth | 34.627 | 34.289 | -1.0% |
| overwrite | 262144 | dense | 100.126 | 113.651 | +13.5% |
| overwrite | 262144 | fullWidth | 101.173 | 113.402 | +12.1% |
| churn | 4096 | dense | 113.630 | 119.797 | +5.4% |
| churn | 4096 | fullWidth | 112.786 | 121.975 | +8.1% |
| churn | 262144 | dense | 197.559 | 210.996 | +6.8% |
| churn | 262144 | fullWidth | 197.656 | 221.798 | +12.2% |
