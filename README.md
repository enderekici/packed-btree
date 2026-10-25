# Java packed-tree experiment

A BBTree-inspired **single-threaded primitive `long -> long` B-tree**, plus a
repeatable comparison with JDK `TreeMap`, JDK `ConcurrentSkipListMap` and fastutil
`Long2LongAVLTreeMap`. This is a standalone experiment; no SaaS service uses it.

The implementation tests the benefit of storing many primitive keys and values
in each node instead of allocating a tree entry per mapping. It is **not a full
port of Rust BBTree**: it does not implement compressed routing keys, SIMD,
optimistic concurrency, off-heap storage, Rust's API or its verification proofs.
It is not thread-safe and cannot replace a concurrent map without additional work.

Upstream design: <https://bbtree.xiangpeng.systems/post/>.
Upstream implementation: <https://github.com/XiangpengHao/bbtree>.

## Implementation

`LongBTree` stores up to 31 mappings per node, with separate primitive key/value
arrays. Leaves allocate no child array. Insertion splits full nodes; deletion
borrows from siblings or merges underfull nodes and contracts an empty root.
Removed nodes become eligible for ordinary Java garbage collection.

Public operations:

- `getOrDefault(long key, long fallback)`; every signed long key/value is valid.
- `put(long key, long value)`; overwrites retain cardinality.
- `remove(long key)`; returns whether the key existed.
- `size()`.
- `forEach(fromInclusive, toExclusive, visitor)`; ascending half-open range,
  empty when the lower bound is greater than or equal to the upper bound.
  Do not mutate the tree from the visitor. To access `Long.MAX_VALUE` itself,
  use `getOrDefault`; a half-open range cannot include that endpoint.
- `validate()`; exhaustive ordering, occupancy, child-reference, leaf-depth and
  cardinality checks for qualification, excluded from timed workloads.

## Build and qualify

Requires JDK 25, Bash and curl. Production tree code has no dependencies. JMH,
fastutil and JOL are benchmark dependencies, pinned in `build.sh`.

```bash
bash build.sh
```

The script selects installed JDK 25 on macOS. On another host set
`BBTREE_JAVA_HOME` to a JDK 25 installation. It downloads dependencies from Maven
Central into `.deps`, compiles, builds `build/bbtree-java.jar` and executes the
contract/differential tests. Dependency hashes are recorded in
`build/dependencies.sha256`.

Tests compare complete ordered contents, individual lookups, range results,
overwrite behavior, deletion results and size with `TreeMap`. They cover empty
trees, extreme keys and values, ascending/reverse/random insertion, complete
drain and refill, dense randomized mixed operations, and random full-width keys.
The structural validator runs repeatedly during those tests. A deliberately
wrong reference value confirms the comparison detects a positive mismatch.
All four benchmark adapters also undergo direct update/delete/range checks.

## Reproduce the comparison

Run from this directory after building:

```bash
JAVA_HOME="${BBTREE_JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
"$JAVA_HOME/bin/java" -cp 'build/classes:.deps/*' org.openjdk.jmh.Main \
  bench.MapBenchmark -wi 2 -i 3 -w 1s -r 1s -f 2 -t 1 -prof gc \
  -rf json -rff results/average.json > results/average.log 2>&1
```

Each case uses two isolated JVM forks, two one-second warmups and three
one-second measured iterations per fork. The fork heap is fixed at 1 GiB with
G1 GC. Each measured operation uses one thread. JMH records average time,
confidence intervals and allocation. Setup/teardown run once per trial, outside
the timed operations. Setup allocations are outside the measured iterations.
JMH's GC profiler still includes trial teardown in the final iteration: the
reported allocation comparison therefore uses only the first two measured
iterations of each fork, for every implementation and operation. All six
iterations remain in the timing means, and the unadjusted profiler values are
preserved in the raw JSON. Teardown performs a complete key scan and structural
validation; its allocations are not workload allocations.

Fixtures use 16,384 and 1,048,576 mappings. Even keys start at 1024 (outside the
usual `Long` cache); values are three times their keys. A fixed seeded shuffle
controls both insertion order and a repeating random traversal of **all** keys.
This measures dense integer keys in shuffled order, not uniformly random
64-bit key spacing, strings, UUIDs or a captured Evam workload.

Operations are lookup hit, lookup miss (odd keys), overwrite, churn (one remove
and one insert toggling key parity at constant cardinality), and an ordered
64-entry range sum. One churn operation is **two map mutations**. Every range
contains exactly 64 mappings, including at the top of the key space. Returned
results are consumed by JMH. Each trial checks fixture size, surviving
keys and, for the prototype, all structural invariants.

The JDK adapters include the boxing needed for their generic map API. The
fastutil primitive AVL baseline helps distinguish avoiding boxing from changing
tree layout. `ConcurrentSkipListMap` is measured with a single thread: these
results say nothing about multi-thread scaling or equivalence of guarantees.

For retained object-graph size, run each implementation in a fresh JVM:

```bash
"$JAVA_HOME/bin/java" -Xmx4g -Djdk.attach.allowAttachSelf=true \
  -XX:+EnableDynamicAgentLoading -cp 'build/classes:.deps/*' \
  bench.MemoryFootprint btree 1048576
```

Repeat with `tree`, `skiplist` and `primitiveAvl`. JOL counts the reachable map
graph, including boxed entries/keys/values, node arrays and unused array slots.
It excludes the workload key array, JVM overhead, transient allocations and GC
headroom. This is not process RSS or production pod memory.

Lookup tail latency is measured separately, using the same fixtures and fork
settings:

```bash
"$JAVA_HOME/bin/java" -cp 'build/classes:.deps/*' org.openjdk.jmh.Main \
  bench.MapBenchmark.hit -p size=1048576 -bm sample \
  -wi 2 -i 3 -w 1s -r 1s -f 2 -t 1 \
  -rf json -rff results/lookup-sample.json > results/lookup-sample.log 2>&1
```

Sampling adds timer overhead, so sampled p99 and average-mode means come from
different runs and should not be subtracted to infer a latency distribution.

Measured results and limitations are in `RESULTS.md`; raw JMH output is kept in
`results/`. Performance findings apply only to the tested JVM, CPU, fixtures and
single-threaded operations. Upstream BBTree's Rust benchmark numbers are not a
baseline in this experiment.
