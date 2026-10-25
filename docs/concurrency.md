# Concurrent B-tree

`ConcurrentLongBTree` and `ConcurrentStringBTree<V>` are separate concurrent APIs.
The existing `LongBTree` and `StringBTree<V>` remain single-threaded and unchanged.
The concurrent Long API stores primitive long keys and values. The concurrent String
API orders non-null keys with `String.compareTo`, permits null values, and retains the
original stored key object when a compare-equal key is overwritten. Its `remove`
result reports presence even when the mapping's value is null.

## Publication model

A writer takes the instance monitor, copies the root, and copies each node before
changing its arrays, count, or child references. Split, borrow, and merge modify
only writer-private nodes. Unchanged subtrees are shared but never changed. The
writer publishes one volatile `State(root, size)` only after the mutation is
complete. This makes the root and cardinality one coherent snapshot and gives
all pre-publication node/array writes a happens-before edge to readers.

Point reads and `size()` capture one published state and do not acquire the
writer monitor. Each method is linearizable. Multiple separate method calls do
not form one transaction. `forEach` captures one root and walks it for the full
traversal, so callbacks can re-enter or mutate the live map. A long callback
keeps its old root and reachable nodes alive until it returns. Writers are
serialized; this is not BBTree's per-node version-validation protocol.

A write copies a bounded number of B-tree nodes along the tree height and any
sibling touched by rebalancing. The fanout remains 31 entries per node. The
mutation work is O(log n) tree levels with O(31) array copies per touched node;
it does not copy the entire tree per operation.

`ConcurrentStringBTree` follows the same ownership and publication protocol.
The snapshot is shallow: generic values are retained by reference, so later
mutations of a value object can be observed through an older tree snapshot.
Each range traversal retains one root for its full callback sequence. Callbacks
may perform nested reads or writes; long callbacks keep old reachable nodes alive.

## Verification

The concurrent tests include full-width key boundaries, a sequential differential
case, 40 small overlapping histories checked against an enumerated sequential
oracle (`get`, `put`, `remove`, `size`, and range), and explicit legal/illegal
oracle controls. A six-thread start-barrier workload overlaps point/range readers
with disjoint key insertion, deletion, and shared-key overwrite. A paused range
holds an old root while a writer empties the tree, rebuilds it, and then resumes;
its emitted pairs must exactly match the pre-mutation snapshot. Callback mutation
and nested traversal are also exercised.

Clean Maven `verify` is run on JDK 17 and JDK 25. The standalone consumer probe
compiles against the built library JAR and runs the primitive lookup, `size`, and
re-entrant snapshot callback APIs.

## Timings

The JMH source is in `benchmarks/src/main/java/bench/ConcurrentMapBenchmark.java`
and `ConcurrentRangeBenchmark.java`. Point workloads compare read-only and one
read plus one overwrite using `ConcurrentLongBTree`, `ConcurrentSkipListMap`, and
`ConcurrentHashMap`. Ordered 64-entry range workloads compare only the B-tree and
skip list. Run at 1, 2, 4, and 8 threads, using the same shuffled 1,048,576-key
fixture and two forks, with two 1-second warmups and three 1-second measurements.
That is 24 point combinations and 8 ordered-range combinations (32 total), or
192 seconds measured plus 128 seconds of warmup iterations (320 seconds total)
before fixture setup and validation. Read-only
point operations sample the full key set. In the
mixed case, each JMH thread writes only its own striped subset of that same set;
this avoids key-level write races while measuring serialized-writer contention.
Each mixed invocation reads one thread-owned key and increments its value, so the
write always changes the stored value without cross-thread same-key races. Range
setup asserts exactly 64 entries and the correct sum at the first, middle, and last
valid start positions. JMH
thread state uses private xorshift cursors; no global atomic cursor is in the
measured path. Results verify sampled point reads, range sums, full key presence,
stable cardinality and B-tree structure outside measured iterations. Raw JSON
goes in `results/concurrent/`.

The exclusive run completed 2026-10-25 14:45:55–14:52:56 UTC on an Apple M4 Pro
(14 logical CPUs), macOS 27.0, Azul OpenJDK 25.0.3. JMH exited 0 for all 32
configurations. The table reports average time in ns/op (lower is faster); raw
JMH JSON is retained under `results/concurrent/`.

| Threads | B-tree read | Skip-list read | Hash read | B-tree mixed | Skip-list mixed | Hash mixed | B-tree range64 | Skip-list range64 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 202.630 | 794.888 | 42.442 | 722.911 | 1241.567 | 198.558 | 373.526 | 2671.278 |
| 2 | 215.540 | 1174.482 | 51.105 | 848.119 | 1045.193 | 165.090 | 403.996 | 2985.651 |
| 4 | 277.714 | 1022.285 | 46.771 | 2461.959 | 1168.665 | 148.584 | 361.370 | 1400.557 |
| 8 | 193.082 | 820.800 | 49.008 | 3570.974 | 1007.872 | 165.071 | 375.764 | 1360.553 |

The snapshot B-tree's ordered range workload was faster than the skip list in
this run. Its point reads were slower than `ConcurrentHashMap`, and mixed point
cost rose sharply at 4 and 8 threads. Writers serialize by design; this timing
does not quantify how much monitor waiting contributes to that cost. These results
describe this implementation and machine only; they do not establish BBTree
node-version scalability or predict other hardware/workloads.

## Concurrent Long C1 confirmation

A separate guarded benchmark compares the original concurrent Long implementation
with C1, which changes node lookup from binary to linear scan while preserving the
serialized-writer and snapshot-reader protocol. This uses 262,144 mappings on the
same M4 Pro/JDK 25.0.3 machine, two forks, 3×1s warmups and 5×1s measurements.
This is a distinct fixture from the 1,048,576-key table above.

| Workload | Threads | Original ops/s | C1 ops/s | Change |
|---|---:|---:|---:|---:|
| Read only | 1 | 9,844,544 | 13,969,435 | +41.9% |
| Read only | 8 | 67,833,888 | 95,474,319 | +40.7% |
| Range of 64 | 1 | 4,225,782 | 5,038,087 | +19.2% |
| Range of 64 | 8 | 29,728,108 | 33,716,915 | +13.4% |
| Disjoint changing puts | 1 | 3,114,280 | 3,337,748 | +7.2% |
| Disjoint changing puts | 8 | 2,428,840 | 2,631,400 | +8.3% |
| Hot-key puts | 1 | 9,252,384 | 8,881,241 | -4.0% |
| Hot-key puts | 8 | 6,247,473 | 6,177,237 | -1.1% |

The hot-write 99.9% intervals overlap. C1's unpaced 7-reader/1-writer test at
eight threads measured 274.29M reads/s and 1.24M writes/s for the original,
versus 136.82M reads/s and 4.80M writes/s for C1. Preserve this workload tradeoff;
the higher writer count does not cancel the lower reader rate. In a separate paced
test with matched writer rates near 498k/s, readers measured 309.94M/s original
and 699.02M/s C1. These are workload-specific observations and do not establish
a causal lock-contention explanation. Raw JSON, logs, guards, and full limitations
are in [the run report](../results/concurrency-next/report.md).

The packed key/value layout was measured separately against C1. It reduced bytes
allocated per overwrite by about 3.5%, with no coherent point/range throughput gain.
Its one-fork unpaced screen suggested a writer-rate drop, but a reversed-order
two-fork repeat had a wide overlapping interval and did not confirm that effect.
The packed layout was not adopted because the modest allocation reduction had no
coherent throughput gain and the unpaced result remained unstable; this is not a
finding of established harm. The keys-first patch and measurements are kept as
rejected-candidate evidence in the [run report](../results/concurrency-next/report.md).

Final `clean verify` passed on JDK 17 (24 tests) and JDK 25 with `-Psimd` (27
tests). A packaged consumer probe compiled with `--release 17` and ran on both
JDK 17 and JDK 25 against the same JDK 25-built library JAR. It exercised Long
snapshot mutation and String null/fallback, key identity, UTF-16 ordering and
callback reentry. `jdeps --multi-release 17 -s` reports only `java.base`.
