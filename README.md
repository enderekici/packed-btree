# packed-btree

A compact, primitive `long → long` ordered map for Java 17+.

`LongBTree` stores up to 31 mappings per node in primitive arrays. It supports
lookup, insert, overwrite, deletion with rebalancing, and ascending range
traversal, without boxing keys or values. The library has **no runtime
dependencies**.

**Experimental.** `LongBTree` is single-threaded and inspired by
[BBTree](https://github.com/XiangpengHao/bbtree), but is not a port of its
concurrent implementation. `ConcurrentLongBTree` is a separate primitive API
with serialized writers and immutable root snapshots. `StringBTree<V>` provides
string keys and generic, nullable values. `CompressedLongBTree` is an opt-in
variant with 16-bit routing suffixes and higher memory use. Optional SIMD search
is a separate JDK 25 artifact; the default library remains dependency-free and
usable on JDK 17. Long-key trees accept a custom lower-bound strategy, and the
compressed variant uses it on nodes whose keys do not share the same signed high
48 bits. These APIs do not implement `Map` or `NavigableMap`; concurrency does
not use BBTree-style versioned nodes.

## Build

Requires JDK 17 or later; Maven 3.9.16 is provided by the wrapper.

```bash
./mvnw clean verify
```

On Windows, use `mvnw.cmd`. The build runs differential/contract tests, generates
the library, source and Javadoc JARs, and packages the separate runnable JMH
benchmark JAR. CI runs the default build on JDK 17 and also builds/tests the optional SIMD profile on JDK 25.

To build only the library:

```bash
./mvnw -pl library -am clean verify
```

To build and test the optional Vector API module on JDK 25:

```bash
./mvnw -Psimd clean verify
```

The resulting `packed-btree-simd` artifact provides `VectorSearch`; opt in with
`new LongBTree(new VectorSearch())`. Applications using it must run with
`--add-modules jdk.incubator.vector`. It uses signed comparisons and masks the
partial final vector. `CompressedLongBTree` also accepts `VectorSearch` as its
fallback for noncompact nodes; compact nodes use suffix routing.

## Use

The artifact is not yet published to Maven Central. Install it locally:

```bash
./mvnw -pl library -am clean install
```

Then add:

```xml
<dependency>
  <groupId>io.github.enderekici</groupId>
  <artifactId>packed-btree</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import io.github.enderekici.packedbtree.LongBTree;

LongBTree map = new LongBTree();
map.put(10L, 100L);
map.put(20L, 200L);
map.put(10L, 150L);

long value = map.getOrDefault(10L, -1L); // 150
map.forEach(10L, 20L, (key, entryValue) -> {
  System.out.println(key + "=" + entryValue); // 10=150
});
boolean removed = map.remove(20L); // true
int size = map.size(); // 1
```

`StringBTree` orders keys by `String.compareTo`; keys cannot be null, but values
can:

```java
import io.github.enderekici.packedbtree.StringBTree;

StringBTree<Long> byName = new StringBTree<>();
byName.put("alice", null);
Long value = byName.getOrDefault("alice", 42L); // null
byName.forEach("a", "z", (key, entryValue) -> System.out.println(key));
```

For opt-in compact routing suffixes, use `CompressedLongBTree` with the same API.
It retains full keys and adds short routing arrays to its nodes, so the variant
uses more memory; see [the routing notes](docs/compressed-routing.md) for the
design and measured tradeoff.

Every signed `long` is a valid key and value; the library reserves no sentinel.
Keys use signed ascending order. A missing lookup returns the caller's fallback.
Range bounds are half-open: `[fromInclusive, toExclusive)`. Equal or reversed
bounds yield no visits. Because a half-open range cannot include
`Long.MAX_VALUE`, use a point lookup for that key.

`LongBTree` requires external synchronization across every access.
`ConcurrentLongBTree` is a separate primitive API: its writers serialize, while
point reads and snapshot range traversals do not take the writer lock. It path-copies
packed B-tree nodes and publishes root plus size in one volatile state.
Each call is linearizable; separate calls do not share an atomic snapshot. A range keeps the root it
captured when traversal began, so its callback may re-enter or mutate the tree. A long-running callback
retains the old root and reachable nodes until traversal ends. This design does not use BBTree-style
per-node version validation. See [the concurrency report](docs/concurrency.md).

`validate()` is an optional full structural diagnostic. It checks ordering,
occupancy, child references, equal leaf depth and cardinality; keep it out of
hot paths.

## Measurements

Historical prototype measurements at 1,048,576 mappings, JDK 25.0.3, Apple M4
Pro, one thread:

| Measurement | Primitive B-tree | TreeMap | ConcurrentSkipListMap | Primitive AVL |
|---|---:|---:|---:|---:|
| Lookup, ns | 133 | 269 | 596 | 184 |
| Overwrite, ns | 136 | 331 | 678 | 210 |
| Delete + insert, ns | 259 | 972 | 1,386 | 507 |
| Scan 64 entries, ns | 273 | 3,189 | 2,622 | 600 |
| Sampled lookup p99, ns | 333 | 666 | 1,374 | 417 |
| Retained map memory, MiB | 26.5 | 88.0 | 84.0 | 40.0 |

These results belong to prototype commit
[`dcb5176`](https://github.com/enderekici/packed-btree/commit/dcb51764891551745b1efd9825a9b55e031a5248),
before the Maven/package reorganization. They measure shuffled dense integer
keys and fixed-size `long` values on a shared developer machine. They do not
establish gains for strings, UUIDs, other hardware, concurrent workloads or an
application. The primitive AVL control uses fastutil `Long2LongAVLTreeMap`.
Memory is the retained map object graph, not process RSS. Sampled p99 is a
separate run with timer overhead.

See [the full report](RESULTS.md) for confidence intervals, allocation-profiler
handling, limitations and raw evidence. The benchmark module contains all
third-party benchmark dependencies; none enters the library's runtime graph.

The point-operation comparison also shows that all tested hash maps beat
`LongBTree` on its tested hit, miss, overwrite and churn workloads at both
fixture sizes. Those measurements use positive dense keys and above-maximum
misses; they do not cover full-width or collision-heavy hash distributions. See
[the hash-map report](results/hashmaps/report.md) and [workload notes](docs/hashmap-comparison.md). String-key benchmark scope and results are in [the string-key report](results/string-benchmarks/REPORT.md).

## Run the benchmarks

After `./mvnw clean verify`, compare the concurrent API at one, two, four and
eight threads. The point benchmark covers read-only and one-read/one-increment
mixed operations for the concurrent B-tree, `ConcurrentSkipListMap` and
`ConcurrentHashMap`. Read-only probes sample the full shuffled key set; each
mixed-workload thread increments only its own striped partition. Ordered ranges
compare only the B-tree and skip list. The hash map has no ordered range.

```bash
for threads in 1 2 4 8; do
  java -jar benchmarks/target/benchmarks.jar bench.ConcurrentMapBenchmark \
    -p size=1048576 -t "$threads" -wi 2 -i 3 -w 1s -r 1s -f 2 \
    -rf json -rff "results/concurrent/point-${threads}t.json"
  java -jar benchmarks/target/benchmarks.jar bench.ConcurrentRangeBenchmark \
    -p size=1048576 -t "$threads" -wi 2 -i 3 -w 1s -r 1s -f 2 \
    -rf json -rff "results/concurrent/range-${threads}t.json"
done
```

Each run validates point/range observations, full key presence and stable size
outside measured iterations. The point workload writes are real overwrites that
increment values; JMH thread state assigns disjoint key partitions without a
shared cursor. Run each timed matrix when other machine benchmarks are idle. See
the concurrency report for the recorded machine/JDK and complete result set.

After `./mvnw clean verify` for the original single-threaded suite:

```bash
java -jar benchmarks/target/benchmarks.jar -l

java -jar benchmarks/target/benchmarks.jar bench.MapBenchmark \
  -wi 2 -i 3 -w 1s -r 1s -f 2 -t 1 -prof gc \
  -rf json -rff results/average-new.json

java -jar benchmarks/target/benchmarks.jar bench.MapBenchmark.hit \
  -p size=1048576 -bm sample -wi 2 -i 3 -w 1s -r 1s -f 2 -t 1 \
  -rf json -rff results/lookup-sample-new.json

java -Xmx4g -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading \
  -cp benchmarks/target/benchmarks.jar bench.MemoryFootprint btree 1048576
```

For memory measurements, repeat in a fresh JVM with `tree`, `skiplist` and
`primitiveAvl`. Use JDK 25 when comparing against the historical results. JMH
overrides the fork heap to `-Xms1g -Xmx1g` with G1. One churn operation is a
remove plus an insert at constant cardinality; each range sums 64 entries.
Fixture setup occurs once per trial. Trial teardown validates all keys and tree
structure. The GC profiler includes that teardown in the last measured
iteration; use the same exclusion rule documented in the report when comparing
workload allocations.

To reproduce the original source layout and dependency hashes exactly, check
out `dcb5176` in a separate worktree and run its `build.sh`. The committed
`results/source-sha256.json` describes that historical source, not the current
Maven module paths.

## Layout

- `library/`: dependency-free implementation and JUnit differential tests.
- `benchmarks/`: JMH fixtures, adapters, memory probe and adapter contract tests.
- `results/`: historical machine-readable benchmark evidence.
- `RESULTS.md`: measured results and qualification scope.
