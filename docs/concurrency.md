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
320 seconds of measured iterations before fixture setup and validation. Read-only
point operations sample the full key set. In the
mixed case, each JMH thread writes only its own striped subset of that same set;
this avoids key-level write races while measuring serialized-writer contention.
Each mixed invocation reads one thread-owned key and increments its value, so the
write always changes the stored value without cross-thread same-key races. JMH
thread state uses private xorshift cursors; no global atomic cursor is in the
measured path. Results verify sampled point reads, range sums, full key presence,
stable cardinality and B-tree structure outside measured iterations. Raw JSON
goes in `results/concurrent/`.

Timing results will be appended after the scheduled exclusive run. The setup
builds and tests the benchmark source but does not substitute for measured
results.
