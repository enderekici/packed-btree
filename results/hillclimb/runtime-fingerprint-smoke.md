# Runtime fingerprint guard smoke

The corrected runtime classpath `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar` was used with `-foe true`.

- Positive case: the C1 candidate tree+Node resolved from `library/target/classes`; the frozen baseline tree+Node resolved from `simd/target/test-classes`. The per-fork setup printed all four expected SHA-256 fingerprints. JMH emitted two finite score rows and exited 0. Logs and JSON: `/tmp/hillclimb-fingerprint-smoke-foe.log`, `/tmp/hillclimb-fingerprint-smoke-foe.json`.
- Negative case: candidate tree expected hash was deliberately set to `0000`. Per-fork setup printed the actual candidate hash and failed before measurement. `-foe true` made JMH exit 1. Log: `/tmp/hillclimb-guard-negative-foe.log`.
- Exact hashes and bytecode fingerprint are recorded in `harness-classpath-correction.md`.
