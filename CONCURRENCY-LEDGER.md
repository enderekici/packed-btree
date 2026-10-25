# Concurrency-next ledger

Base: `603b6f5b0ebcdddf5a5dab15b9a3f0f71c704340` (`origin/main` at task start).

## Protocol decisions

- Retain serialized writers and one volatile immutable-root/size state. Readers capture one state and traverse without the writer lock.
- Do not add root CAS, per-node versions, or sharded mutation. This design is not BBTree-style node-version concurrency.
- Concurrent string mapping uses path-copy ownership. Published keys, values arrays, and child arrays never change. A range callback retains one root snapshot and may reenter/mutate the map. Values are shared object references; snapshots do not deep-copy them, so callers must synchronize mutations to mutable values.
- Persistent merge copies right-node contents into a writer-private left node and never clears or rewrites the shared right node. Tail cleanup is permitted only on private nodes.
- Long candidate C1 changes only the concurrent node search from binary to linear scan. The original concurrent implementation remains frozen as a separate benchmark baseline. C2 is a separately measured keys-first packed key/value layout against frozen C1; its retention remains under root review.

## Advice provenance

Astra and Fable independently advised retaining snapshot publication and serialized writers. Astra called out `StringBTree` merge clearing right-node arrays/count; this port does not mutate shared right nodes. Fable advised testing Long linear scan before a packed layout, adding a guarded baseline, and using monitor/ThreadPark JFR evidence before making causal contention claims. Raw GC samples must remain unless a demonstrated receipt invalidates them.

## Test receipts

- JDK 17 targeted run: `ConcurrentStringBTreeTest,ConcurrentLongBTreeTest`; 9 tests, 0 failures, 0 errors, 0 skipped. Log: `/tmp/concurrency-next-targeted-j17.log`.
- String bounded history uses a 32-entry split-boundary seed including `a` and `b`, seven operations, and a full-map range. It includes nullable values and positive/impossible histories.
- Long bounded history retains a real `remove(32)` alongside overlapping writes and a range over 28..37. The checker validates complete range contents against `TreeMap`; executor futures use timeouts and always shut down.
- Long reader stress checks exact key sequences and only allows values written by the known deterministic writers.

## Frozen benchmark source

`benchmarks/src/main/java/bench/concurrent/ConcurrentLongBTreeBaseline.java` is the baseline from the base revision's `ConcurrentLongBTree.java`, with only package and class/constructor names changed for side-by-side loading. Baseline source SHA-256: `27f52ba8dcca097322273b8746dbda8079a2720a002aade62353903fd1de64d4`. Candidate source SHA-256 after C1: `30ce404d7db054daea03d3ae830e57f176f9114f38fe1aa160755cb7991a7f22`.

## C1 exploratory screen

- JMH 1.37, Azul JDK 25.0.3, Apple M4 Pro (14 cores reported), 262,144 keys, fixed seed and shuffled insertion order. 13 workload/thread configurations × baseline/C1; two forks, one 1s warmup and two 1s measurements per fork. Aggregate throughput is reported in operations/second. Full matrix: 26 primary results plus the separate paced SWMR screen, 28 primary results total.
- Baseline source is the base revision source (SHA-256 `7c12dfa5a68d3233f7d9ec2eb31b8e4d4283e24fb8354d76c0498bf234284a9c`), with only package/class/constructor renamed in the benchmark copy. Each fork checks selected outer/Node/State bytecode SHA and CodeSource; the negative guard exits 1 on a deliberately wrong hash. Selected class receipts are embedded in each JSON file's fork args and printed in logs.
- Exploratory candidate deltas, C1 relative to baseline: read-only +40.2%/+42.2%/+40.8% at 1/4/8 threads; disjoint changing writes +2.9%/+8.1%/+7.0%; hot-key puts -0.2%/-0.9%/-0.7%; 64-entry ranges +18.6%/+20.1%/+20.7%. Treat these as short-screen evidence, not a final performance verdict.
- Unpaced 7-reader/1-writer SWMR at 8 threads: aggregate operations/s was 262.7M baseline vs 158.9M C1 (-39.5%). Its `EVENTS` counters are raw sums, not rates; dividing by the four one-second measured samples gives approximate baseline read/write rates 262.94M/1.22M per second and C1 155.76M/4.62M per second. Preserve the tradeoff; do not collapse it into an overall win/loss.
- Added a separate paced SWMR workload targeting 500k puts/s with a 2us deadline and bounded late catch-up. The 2-fork screen observed 497,548 baseline and 497,537 C1 writes/s, and 307.17M vs 690.99M reads/s. `OPERATIONS` AuxCounters report rates directly. This short screen demonstrates that both arms ran at a closely matched writer rate.
- No monitor-contention cause is claimed from the throughput table. A separate early JFR diagnostic used an intermediate harness and is not included as screen evidence.

## C1 confirmation screen

- Root-authorized 20 primary rows: read/range/disjoint/hot at 1 and 8 threads, plus unpaced and paced SWMR at 8. Two forks, 3×1s warmups and 5×1s measurements, JDK 25.0.3. Arm order alternated by fixture. Every JSON has exactly two finite primary scores; every log has 12 selected-class/CodeSource guard receipts and no JMH failures.
- Long source, transformed baseline source, and harness hashes did not change between `confirmation-source-before.sha256` and `confirmation-source-after.sha256`.
- C1 confirmation deltas: read-only +41.9%/+40.7%, range64 +19.2%/+13.4%, disjoint changing writes +7.2%/+8.3%, hot-key puts -4.0%/-1.1% at 1/8 threads. Hot-write 99.9% intervals overlap the baseline; no material normal-write penalty was observed.
- Unpaced 7R:1W remains a reported tradeoff: aggregate throughput -48.6%; `OPERATIONS` counters read 274.29M/s to 136.82M/s and write 1.24M/s to 4.80M/s.
- Paced 7R:1W matched writers at 497,963 vs 497,719 writes/s and observed 309.94M vs 699.02M reads/s. Keep this distinct from unpaced results.
- Decision: C1 meets the pre-agreed retention condition on the confirmation matrix. C2's exploratory screen and allocation probe are recorded below; do not interpret the one-fork screen as confirmation.

## C2 exploratory screen

- C2 packs each node's key prefix and value suffix into one `long[62]`; keys occupy slots 0–30 for linear search and values slots 31–61. Child arrays, path-copy ownership, and the writer/read protocol are unchanged. The benchmark compares this implementation to a frozen copy of accepted C1, not the original base.
- JDK 25.0.3, 262,144 mappings, fixed shuffled fixture, one fork, 1×1s warmup and 2×1s measurements. Four point/range workloads ran at 1/4/8 threads; unpaced and 500k/s paced SWMR ran at 8 threads. The 14 JSON files contain 28 finite primary rows, with selected outer/Node/State SHA and shaded CodeSource checked in every fork. The deliberately wrong baseline SHA control exited 1. Source SHA files before/after the screen match.

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

The unpaced SWMR writer rates differed substantially (C1 4.82M/s, C2 1.40M/s), so aggregate throughput is not a fair paired result; keep it as an observed run, not an advantage. Paced writer rates were matched at 497,337 and 497,712 writes/s, with reader throughput -1.5% for C2. A separate warmed single-thread `ThreadMXBean` probe measured 3,362.08 bytes/put for C1 and 3,243.68 for C2 over 100,000 precomputed-key overwrites, then verified all 262,144 mappings and called `validate()`. This is a 3.5% allocation reduction.

The short screen has no coherent throughput gain and no point/range row regressed by more than 5%. A reversed-order, 2-fork/3-warmup/5-measure unpaced SWMR repeat measured C2 at 5.069M writes/s ±0.049M (99.9% CI), while C1 measured 2.978M ±2.808M (99.9% CI); C1's forks split from 4.72–4.76M writes/s and ~143M reads/s to 1.21–1.22M writes/s and ~447M reads/s. C2 stayed around 5.04–5.13M writes/s and 130–138M reads/s. The intervals overlap widely, so the follow-up is inconclusive on writer capacity. Root rejected C2 adoption because the 3.5% allocation reduction had no coherent throughput gain and the unpaced result remained unstable; this is not a finding of established harm. Source hashes before/after match. Raw data is in sibling `c2_screen_*.json/.log` and `c2_unpaced_confirm.json/.log`; allocation and smoke receipts are under `receipts/concurrency-next-c2-*.log`. The final keys-first patch is saved as `c2-rejected-keys-first.patch`; an earlier interleaved-array draft was not used for the final measurements.

## Final verification

- Final product source is C1 Long at SHA-256 `30ce404d7db054daea03d3ae830e57f176f9114f38fe1aa160755cb7991a7f22` plus ConcurrentString from commit `a7a9492`. C2 is not in the library source or product JAR.
- JDK 17 `clean verify`: exit 0; 24 tests passed (20 library, 4 benchmark), no failures/errors/skips. Receipt: `receipts/concurrency-next-final-clean-verify-j17.log`.
- JDK 25 `-Psimd clean verify`: exit 0; 27 tests passed (20 library, 4 benchmark, 3 SIMD), no failures/errors/skips. Receipt: `receipts/concurrency-next-final-clean-verify-j25-simd.log`.
- Final JDK 17 guarded subset against the original baseline: 6 primary rows for read-only at 1 thread (+40.8%), range64 at 1 thread (+21.4%), and disjoint changing writes at 8 threads (+7.8%); two forks, 3×1s warmups and 3×1s measurements. The Long source and benchmark class hashes are captured before/after the run.
- The packaged consumer was compiled with `javac --release 17` under JDK 17.0.19 and JDK 25.0.3, then run under both runtimes against the same JDK 25-built library JAR (SHA-256 `bc00dda114f76c1ba208a94f359c4f7da3fa88ee7f493e414c4d5c7956d777f1`). Both runs exited 0 and checked Long snapshot callback mutation, String null/fallback behavior, compare-equal key identity, NUL/surrogate UTF-16 ordering, callback reentry/mutation, and tree validation. `jdeps --multi-release 17 -s` reports only `java.base`.

Raw evidence and tabulated results: [`results/concurrency-next/report.md`](results/concurrency-next/report.md) and its sibling JSON/log files. Final JDK 17 and JDK 25 source hashes matched before and after each full build. JDK 17 `clean verify` exited 0 with 24 tests (20 library, 4 benchmark); JDK 25 `-Psimd clean verify` exited 0 with 27 tests (20 library, 4 benchmark, 3 SIMD), with no failures/errors/skips.
