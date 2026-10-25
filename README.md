# packed-btree

A compact, primitive `long → long` ordered map for Java 17+.

`LongBTree` stores up to 31 mappings per node in primitive arrays. It supports
lookup, insert, overwrite, deletion with rebalancing, and ascending range
traversal, without boxing keys or values. The library has **no runtime
dependencies**.

**Experimental and single-threaded.** It is inspired by
[BBTree](https://github.com/XiangpengHao/bbtree), but is not a port of its
concurrent implementation. SIMD, compressed routing keys, concurrent access and
generic object keys/values are not implemented. It does not implement `Map` or
`NavigableMap`.

## Build

Requires JDK 17 or later; Maven 3.9.16 is provided by the wrapper.

```bash
./mvnw clean verify
```

On Windows, use `mvnw.cmd`. The build runs differential/contract tests, generates
the library, source and Javadoc JARs, and packages the separate runnable JMH
benchmark JAR. CI runs the build on JDK 17 and JDK 25.

To build only the library:

```bash
./mvnw -pl library -am clean verify
```

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

Every signed `long` is a valid key and value; the library reserves no sentinel.
Keys use signed ascending order. A missing lookup returns the caller's fallback.
Range bounds are half-open: `[fromInclusive, toExclusive)`. Equal or reversed
bounds yield no visits. Because a half-open range cannot include
`Long.MAX_VALUE`, use a point lookup for that key. Do not mutate the tree from a
range callback. Access from multiple threads requires external synchronization
covering every operation and traversal.

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

## Run the benchmarks

After `./mvnw clean verify`:

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
