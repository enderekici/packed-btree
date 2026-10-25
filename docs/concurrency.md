# Concurrent B-tree

`ConcurrentLongBTree` is a separate API. The existing `LongBTree` remains
single-threaded and unchanged.

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

A write copies a bounded number of packed nodes along the tree height and any
sibling touched by rebalancing. The fanout remains 31 entries per node. The
mutation work is O(log n) tree levels with O(31) array copies per touched node;
it does not copy the entire tree per operation.

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
cost rose sharply at 4 and 8 threads because writers serialize. These results
describe this implementation and machine only; they do not establish BBTree
node-version scalability or predict other hardware/workloads.
