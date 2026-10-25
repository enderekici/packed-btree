# Candidate 3 short screen

JDK 25.0.3, one fork, one 250ms warmup, two 250ms measurements; `-foe true`; 60 rows covering five operations, candidate and baseline, 16K/262K, short/random/prefix, String values. Source SHA-256 `94853ac9b62ff038696271a4b5de80c6110887e4ecf639f400f38767a60ab94e`; loaded class hashes were guarded and match the manifest in [confirmation report](candidate3-confirmation.md).

Screen estimates were lower in most cells. One 16K random overwrite cell was 10.7% slower, so the longer confirmation added that case with arm order reversed. The confirmation in the linked report resolved it at 13.1% faster. Short-screen raw results are in [candidate3-screen.json](candidate3-screen.json), and full log in `/tmp/string-candidate3-screen.log`.

This screen is directional only; its single fork and two short iterations do not establish significance.
