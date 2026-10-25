# Concurrency-next ledger

Base: `603b6f5b0ebcdddf5a5dab15b9a3f0f71c704340` (`origin/main` at task start).

## Protocol decisions

- Retain serialized writers and one volatile immutable-root/size state. Readers capture one state and traverse without the writer lock.
- Do not add root CAS, per-node versions, or sharded mutation. This design is not BBTree-style node-version concurrency.
- Concurrent string mapping uses path-copy ownership. Published keys, values arrays, and child arrays never change. A range callback retains one root snapshot and may reenter/mutate the map. Values are object references; snapshots do not deep-copy mutable values.
- Persistent merge copies right-node contents into a writer-private left node and never clears or rewrites the shared right node. Tail cleanup is permitted only on private nodes.
- Long candidate C1 changes only the concurrent node search from binary to linear scan. The original concurrent implementation remains frozen as a separate benchmark baseline. No packed-node C2 until C1 is measured and reviewed.

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
- Decision: C1 meets the pre-agreed retention condition on the confirmation matrix. No C2 packed-layout candidate has been implemented or measured.

Raw evidence and tabulated results: [`results/concurrency-next/report.md`](results/concurrency-next/report.md) and its sibling JSON/log files. JDK 17 targeted tests passed (9 tests, 0 failures/errors/skipped); benchmark module package build passed. Full clean verification on JDK 17 and 25 and packaged consumer checks remain pending.
