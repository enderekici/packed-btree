# Optional SIMD node-search benchmark

**Pre-C2 measurement.** This campaign used source snapshot `405db725807c005ef7b22b39121eca4477285cdf` (`LongBTree.java` SHA-256 `fe383eb9182443f8f7fd1e1ff2c614d131b79dc42237642868b090bfcb21b7bc`), before the accepted linear-search change. The “current default” column below is the binary-search implementation from that snapshot; it is not timing evidence for the final default. See the [guarded C2 confirmation](hillclimb/c2-confirm/README.md) for the final scalar search results.

The corrected four-arm campaign ran on 2026-10-25 using Zulu OpenJDK
25.0.3+9-LTS on one Apple M4 Pro / ARM64 host (macOS Darwin 27.0.0). JMH 1.37
used one thread, two forks, two one-second warmups and three one-second
measurements. Each fork used a fixed 1 GiB heap and G1. The parent JVM and every
fork had `--add-modules=jdk.incubator.vector` enabled.

The four controls were: the exact pre-hook tree source from commit
`91989190df5cf92e4da1965363e9e0650f47cca6` (renamed and kept in test sources),
the current default library, a scalar implementation through the search hook,
and `VectorSearch`. They used identical dense signed keys (`key = 3*i - size`)
and a seeded query stream with one-third expected hits and two-thirds misses at
4,096 and 262,144 mappings. Results are AverageTime in ns/op. Intervals are
JMH's 99.9% confidence intervals over six measured iteration scores.

| Mappings | Pre-hook scalar | Current default | Scalar hook | Vector API |
| ---: | ---: | ---: | ---: | ---: |
| 4,096 | 21.341 [11.288, 31.395] | 21.933 [15.900, 27.966] | 32.087 [22.044, 42.129] | 35.235 [30.742, 39.729] |
| 262,144 | 71.628 [62.872, 80.383] | 76.303 [54.982, 97.624] | 75.004 [46.384, 103.624] | 45.912 [41.822, 50.001] |

The default path's means were 2.8% and 6.5% slower than pre-hook, respectively,
but their confidence intervals overlap at both sizes, so this run does not show
a measured default-path regression. SIMD was 65.2% slower than pre-hook by mean
at 4,096 mappings; those intervals narrowly overlap. At 262,144 mappings, SIMD
was 35.9% faster than pre-hook by mean, and the intervals do not overlap. The
scalar-hook results are noisy, especially at the larger size. This is evidence
for this dense-key lookup fixture on one ARM64 machine and one JDK build; it
does not establish a general speedup, performance on other key distributions,
or parity with BBTree.

The SIMD strategy uses the Java Vector API with signed `GE` comparisons and an
active-lane mask for the partial final vector. Native instruction lowering was
not verified: this JDK has no `hsdis` disassembler, and `PrintAssembly` reported
`Loading hsdis library failed`. The implementation uses Vector API operations;
this report does not claim a particular native SIMD instruction was emitted.

The initial three-arm run is preserved in `simd-dense-jdk25-m4pro.json` as
exploratory data only. Its purported scalar baseline was the post-hook current
default and did not include the pre-hook source; use the corrected four-arm
campaign above for comparisons.

Raw corrected results: [simd-dense-four-arm-jdk25-m4pro.json](simd-dense-four-arm-jdk25-m4pro.json).
The host and run configuration are in
[simd-dense-four-arm-jdk25-m4pro-environment.json](simd-dense-four-arm-jdk25-m4pro-environment.json),
and the exact invocation is in
[simd-dense-four-arm-jdk25-m4pro-command.txt](simd-dense-four-arm-jdk25-m4pro-command.txt).
