# Concurrent Long C1 screen

This was a bounded exploratory comparison of the frozen base implementation and C1's linear node search. Each JMH invocation is one point operation or one 64-entry range; scores are aggregate throughput in operations/second. Raw two-fork measurements, confidence intervals, JVM args, and guard receipts are in the adjacent `screen_*.json` files. Matching console logs are adjacent `.log` files.

Environment: JMH 1.37, Azul OpenJDK 25.0.3, Apple M4 Pro (14 logical CPUs reported by `sysctl`), 262,144 mappings, fixed query seeds by thread index, fixed-seed shuffled insertion order, 2 forks, 1×1s warmup + 2×1s measurements. Screen started 2026-10-25 20:22:32 GMT and completed 20:25:48 GMT. Each configuration checked the selected outer class, `$Node`, `$State`, and their shaded-JAR CodeSource before fixture setup. The bad-hash guard control exited 1.

| Workload | Threads | Base ops/s | C1 ops/s | Change |
|---|---:|---:|---:|---:|
| Read only | 1 | 9,822,449 | 13,775,792 | +40.2% |
| Read only | 4 | 35,479,214 | 50,446,555 | +42.2% |
| Read only | 8 | 68,156,972 | 95,981,343 | +40.8% |
| Disjoint changing puts | 1 | 3,284,689 | 3,379,535 | +2.9% |
| Disjoint changing puts | 4 | 2,509,355 | 2,711,936 | +8.1% |
| Disjoint changing puts | 8 | 2,488,355 | 2,661,989 | +7.0% |
| Hot-key puts | 1 | 9,431,708 | 9,413,684 | -0.2% |
| Hot-key puts | 4 | 6,065,001 | 6,012,986 | -0.9% |
| Hot-key puts | 8 | 5,991,078 | 5,950,043 | -0.7% |
| Range of 64 | 1 | 4,387,557 | 5,202,480 | +18.6% |
| Range of 64 | 4 | 16,478,434 | 19,787,347 | +20.1% |
| Range of 64 | 8 | 29,728,866 | 35,874,617 | +20.7% |
| Unpaced SWMR, 7 readers + 1 writer | 8 | 262,657,138 | 158,925,844 | -39.5% |

Disjoint writes use thread-owned interleaved key stripes and teardown checks the exact per-key write count. Hot-key puts replace values without a read/modify/write claim. Range results check every key/value pair in the snapshot. Cardinality is checked after each trial. The separate unpaced SWMR counter type was `AuxCounters.EVENTS`; dividing its four one-second measured sample totals by four yields approximate rates: baseline 262.94M reads/s and 1.22M writes/s; C1 155.76M reads/s and 4.62M writes/s. These rates are reader/writer counts, not the 7:1 thread ratio.

The separate paced SWMR run uses a 2us per-write deadline, bounded catch-up, and `AuxCounters.OPERATIONS`, which reports rates directly:

| Arm | Aggregate ops/s | Reads/s | Writes/s |
|---|---:|---:|---:|
| Base | 307,662,576 | 307,167,855 | 497,548 |
| C1 | 691,486,188 | 690,990,648 | 497,537 |

The paced writer target was 500,000 writes/s and both arms measured within 0.01% of each other. This short screen has wide read-throughput uncertainty (two forks); it is a matched-rate comparison, not a final performance gate.

The short screen is favorable for C1 on read-only and range workloads, near-neutral on writes, and materially lower on unpaced mixed readers. Preserve that observed mixed-workload tradeoff. It does not establish a causal contention mechanism or a general concurrency scalability claim. The early JFR diagnostic ran before harness cleanup and is excluded from these results.

## C1 confirmation matrix

The root-authorized confirmation used 2 forks, 3×1s warmups and 5×1s measurements. Arm order alternated per fixture. It ran 20 primary result rows: read/range/disjoint/hot at 1 and 8 threads, then unpaced and paced SWMR at 8. The run started 2026-10-25 20:30:55 GMT and completed at 20:36:21 GMT. All 10 JSON files have exactly two finite primary scores, and all logs contain 12 selected-class/CodeSource guard receipts across two forks.

| Workload | Threads | Base ops/s | C1 ops/s | Change |
|---|---:|---:|---:|---:|
| Read only | 1 | 9,844,544 | 13,969,435 | +41.9% |
| Read only | 8 | 67,833,888 | 95,474,319 | +40.7% |
| Range of 64 | 1 | 4,225,782 | 5,038,087 | +19.2% |
| Range of 64 | 8 | 29,728,108 | 33,716,915 | +13.4% |
| Disjoint changing puts | 1 | 3,114,280 | 3,337,748 | +7.2% |
| Disjoint changing puts | 8 | 2,428,840 | 2,631,400 | +8.3% |
| Hot-key puts | 1 | 9,252,384 | 8,881,241 | -4.0% |
| Hot-key puts | 8 | 6,247,473 | 6,177,237 | -1.1% |
| Unpaced SWMR, 7 readers + 1 writer | 8 | 275,520,438 | 141,619,595 | -48.6% |
| Paced SWMR, 7 readers + 1 writer | 8 | 310,434,671 | 699,511,820 | +125.3% |

The unpaced confirmation counters are normalized `OPERATIONS` rates: baseline 274.29M reads/s and 1.24M writes/s; C1 136.82M reads/s and 4.80M writes/s. The paced confirmation writers measured 497,963 vs 497,719 writes/s, essentially matched to the 500k/s target; readers measured 309.94M vs 699.02M reads/s. This preserves the substantial unpaced mixed-read tradeoff as a separate outcome.

`confirmation-source-before.sha256` and `confirmation-source-after.sha256` are identical for the Long candidate, frozen baseline, and benchmark harness. C1 met the pre-agreed retention criterion: confirmed read/range gains, no material normal-write penalty, and an explicitly reported unpaced SWMR tradeoff.

## Packed-layout C2 exploratory screen

C2 stores keys in the first 31 slots and values in the final 31 slots of one `long[62]` per node. Its child arrays and persistent path-copy protocol are unchanged. This comparison uses accepted C1 as the frozen baseline. It ran on JDK 25.0.3 with 262,144 mappings, one fork, one 1s warmup and two 1s measurements. Four workloads ran at 1/4/8 threads; both SWMR workloads ran at 8 threads. Each JSON has two finite primary rows; each corresponding log has six selected-class and shaded-CodeSource guard receipts. The source hashes before and after are identical.

| Workload | Threads | C1 ops/s | C2 ops/s | Change |
|---|---:|---:|---:|---:|
| Read only | 1 | 13,902,879 | 13,884,512 | -0.1% |
| Read only | 4 | 50,527,605 | 50,526,389 | 0.0% |
| Read only | 8 | 93,451,720 | 96,338,402 | +3.1% |
| Disjoint changing puts | 1 | 3,483,913 | 3,499,238 | +0.4% |
| Disjoint changing puts | 4 | 2,729,023 | 2,751,933 | +0.8% |
| Disjoint changing puts | 8 | 2,692,617 | 2,718,065 | +0.9% |
| Hot-key puts | 1 | 9,476,727 | 9,685,109 | +2.2% |
| Hot-key puts | 4 | 6,139,158 | 5,933,542 | -3.3% |
| Hot-key puts | 8 | 5,936,665 | 5,908,938 | -0.5% |
| Range of 64 | 1 | 5,261,735 | 5,414,720 | +2.9% |
| Range of 64 | 4 | 19,623,893 | 20,613,191 | +5.0% |
| Range of 64 | 8 | 37,415,536 | 35,685,789 | -4.6% |
| Unpaced SWMR | 8 | 142,808,620 | 408,466,991 | not comparable |
| Paced SWMR | 8 | 716,306,091 | 705,272,621 | -1.5% |

The first unpaced SWMR pair was not balanced: C1 wrote 4.82M values/s and C2 1.40M/s. Root requested a longer, reversed-order repeat. That run produced C2 at 5.069M writes/s ±0.049M (99.9% CI) and C1 at 2.978M ±2.808M (99.9% CI). C1's two forks split sharply: 4.72–4.76M writes/s with about 143M reads/s in fork 1, then 1.21–1.22M writes/s with about 447M reads/s in fork 2. C2 stayed around 5.04–5.13M writes/s and 130–138M reads/s in both forks. The wide C1 interval overlaps C2; this follow-up is inconclusive about writer capacity and makes no attribution claim. Raw results: `c2_unpaced_confirm.json/.log`; source hashes before/after match.

Paced writes were matched at 497,337 and 497,712/s; C2 readers were 1.5% lower. A separate warmed single-thread `ThreadMXBean` probe measured 3,362.08 bytes/put for C1 and 3,243.68 for C2 over 100,000 precomputed-key overwrites, then validated every mapping and the tree (3.5% lower allocation). The short point/range screen shows no coherent throughput improvement and no regression above 5%; the capacity follow-up did not settle the unpaced writer result. Root rejected C2 adoption because the allocation reduction had no coherent throughput gain and the unpaced result remained unstable. This is not a finding of established harm. The final keys-first patch is preserved as `c2-rejected-keys-first.patch`; an earlier interleaved-array draft was not used for final measurements.

Verification receipts:

- `receipts/concurrency-next-targeted-j17.log`: JDK 17 `ConcurrentStringBTreeTest` + `ConcurrentLongBTreeTest`, 9 tests, 0 failures/errors/skipped.
- `receipts/concurrency-next-bench-package-j17.log`: JDK 17 reactor package build through `benchmarks` with tests skipped; benchmark sources compiled and shaded.
- `receipts/concurrency-next-guard-negative.log`: deliberately invalid selected-class SHA rejected with exit 1.
- `receipts/concurrency-next-workload-smoke.log`: all five unpaced workloads, both arms, at 8 threads; no JMH failures.
- `receipts/concurrency-next-paced-smoke.log`: paced SWMR smoke; guards passed and measured writer rates were 494,778/494,340 writes/s.
- `receipts/concurrency-next-c2-targeted-j17.log`: after the packed-layout change, JDK 17 targeted run passed 9 tests, 0 failures/errors/skipped.
- `receipts/concurrency-next-c2-package-j17.log`: benchmark module compiled and shaded on JDK 17.
- `receipts/concurrency-next-c2-guard-*.log`: baseline/candidate selected-class guards passed; deliberately wrong SHA rejected with exit 1.
- `receipts/concurrency-next-c2-allocation-*.log`: per-arm single-writer allocation probe and post-probe complete mapping validation.
- `receipts/concurrency-next-c2-smoke.log`: all six workloads at 8 threads across both arms; no JMH failures.
- `c2_unpaced_confirm.json/.log`: reversed-order two-fork C2 unpaced repeat; overlapping intervals and large C1 fork dispersion make the capacity result inconclusive.

## Final checks

- JDK 17 `clean verify`: exit 0, 24 tests passed (20 library, 4 benchmark), 0 failures/errors/skipped. Long source SHA matched the accepted C1 commit.
- JDK 25 `-Psimd clean verify`: exit 0, 27 tests passed (20 library, 4 benchmark, 3 SIMD), 0 failures/errors/skipped.
- Final guarded JDK 17 subset against the original baseline: six primary rows, two forks, 3×1s warmups and 3×1s measurements. Read-only t1 +40.8%, range64 t1 +21.4%, disjoint changing puts t8 +7.8%. Raw files are `final17_{read_t1,range_t1,disjoint_t8}.json/.log`; source/class hashes are in adjacent `final17-*.sha256` and `final17-class-hashes.json` files.
- Packaged consumer compiled with `javac --release 17` under both JDK 17.0.19 and JDK 25.0.3 and ran under both runtimes against the same final JDK 25-built library JAR (SHA-256 `bc00dda114f76c1ba208a94f359c4f7da3fa88ee7f493e414c4d5c7956d777f1`). It checked Long callback snapshot mutation and String null/fallback, compare-equal key identity, UTF-16 NUL/surrogate ordering, callback reentry/mutation, and validation. `jdeps --multi-release 17 -s` reports only `java.base`.
- JDK 17 and JDK 25 source hashes matched before/after their full builds. JDK 25 library class and artifact hashes are recorded in `final-j25-library-class-hashes.json`.
