# packed-btree

A Java library for ordered maps backed by compact B-tree nodes. It is inspired
by [BBTree](https://bbtree.xiangpeng.systems/post/), but is an independent
implementation, not a port of BBTree's version-validated concurrent protocol.
The core targets Java 17 and has no runtime dependencies.

## Choose an implementation

| Type | Key/value | Threading contract | Good fit |
| --- | --- | --- | --- |
| `LongBTree` | primitive `long` → `long` | Mutable; synchronize externally if shared | Ordered primitive keys and ranges with compact storage. |
| `StringBTree<V>` | non-null `String` → `V` | Mutable; synchronize externally if shared | String ordering with arbitrary reference values, including null. |
| `ConcurrentLongBTree` | primitive `long` → `long` | Writers serialize; reads use immutable published roots | Concurrent readers with ordered ranges and serialized updates. |
| `ConcurrentStringBTree<V>` | non-null `String` → `V` | Writers serialize; reads use immutable published roots | Concurrent String-key access with reference values. |

The types do not implement `Map` or `NavigableMap`. Long keys use signed
ascending order. Every `long` is a valid key or value; there is no reserved
sentinel, and `getOrDefault` returns the supplied fallback only for an absent
key. Ranges are half-open `[fromInclusive, toExclusive)`, so a range cannot
include `Long.MAX_VALUE`; use a point lookup for it.

Mutable `LongBTree` nodes have degree 16 and hold up to 31 mappings in one
`long[62]`: keys occupy the first 31 slots and values start at offset 31. Its
default node search is a scalar linear lower-bound scan. `ConcurrentLongBTree`
uses separate key and value arrays. String keys use
`String.compareTo` ordering (lexicographic UTF-16 code units). Empty strings are
valid; null keys are rejected. Values are held by reference. Updating a key
that compares equal keeps the originally stored String key object.

## Example

```java
import io.github.enderekici.packedbtree.LongBTree;
import io.github.enderekici.packedbtree.StringBTree;

LongBTree scores = new LongBTree();
scores.put(Long.MIN_VALUE, 10);
scores.put(7, 20);
long score = scores.getOrDefault(7, -1); // 20
scores.forEach(Long.MIN_VALUE, 8,
    (key, value) -> System.out.println(key + ": " + value));

StringBTree<String> names = new StringBTree<>();
names.put("ada", "Ada Lovelace");
names.put("unknown", null);
String name = names.getOrDefault("ada", "fallback"); // "Ada Lovelace"
String nullable = names.getOrDefault("unknown", "fallback"); // null
names.forEach("a", "z",
    (key, value) -> System.out.println(key + ": " + value));
```

The mutable maps are not thread-safe; guard every access with the same external
lock if sharing one. Do not modify a mutable map from its range callback.

The concurrent maps path-copy changed nodes and publish a new root and size as
one state. Writers use a monitor, while point reads and `size()` read the
published state without taking that monitor. Each call is linearizable, but
separate calls do not share an atomic snapshot. Each range traversal captures
one root and keeps it for the callback sequence; a callback may re-enter or
mutate the live map, and a slow callback retains the captured nodes until it
returns. These are shallow snapshots: the maps share value references and do
not deep-copy values, so callers must synchronize later mutations to values.
See
[concurrency semantics](docs/concurrency.md).

```java
import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import io.github.enderekici.packedbtree.ConcurrentStringBTree;

ConcurrentLongBTree counters = new ConcurrentLongBTree();
counters.put(1, 10);
counters.forEach(0, 3, (key, value) -> counters.put(2, 20));
// The range keeps its starting root; a later lookup sees key 2.

ConcurrentStringBTree<String> labels = new ConcurrentStringBTree<>();
labels.put("a", "first");
labels.forEach("a", "z", (key, value) -> labels.put("b", "second"));
```

## Optional Long search variants

`CompressedLongBTree` keeps full keys and adds 16-bit routing suffixes for nodes
whose keys share a signed high-48-bit prefix. Other nodes fall back to scalar or
custom search. In an earlier pre-packed 1M fixture, compressed routing retained
about 17% more memory and had 3–4% slower point hits on sequential and
full-width random keys; the common-prefix result was inconclusive. Treat it as experimental. Details and source
scope are in [compressed routing](docs/compressed-routing.md).

The separate `packed-btree-simd` artifact provides `VectorSearch` via the Java
Vector API, available on JDK 25. Add `--add-modules jdk.incubator.vector` when
compiling and running consumers. It plugs into `LongBTree(SearchStrategy)` and
is also a fallback for noncompact compressed nodes. Its benchmark compares an
earlier binary-search snapshot, so it does not establish a portable benefit for
the current scalar default. Install the optional artifact locally with
`./mvnw -Psimd clean install`; add it to a consumer alongside the core dependency:

The SIMD artifact is not published to Maven Central; this local install is its
only documented distribution path.

```xml
<dependency>
  <groupId>io.github.enderekici</groupId>
  <artifactId>packed-btree-simd</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Construct it with:

```java
import io.github.enderekici.packedbtree.LongBTree;
import io.github.enderekici.packedbtree.simd.VectorSearch;

LongBTree vectorTree = new LongBTree(new VectorSearch());
```

See the [SIMD report](results/SIMD-BENCHMARK.md).

## Install the released core library

The `0.1.0` core release is available from Maven Central. Its coordinates and
public verification details are in the [release record](docs/releases/0.1.0.md).

```xml
<dependency>
  <groupId>io.github.enderekici</groupId>
  <artifactId>packed-btree</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Build and install the development snapshot locally

The checked-out source remains `0.1.0-SNAPSHOT`; that development version is
not published to Maven Central. Install the core artifact to your local Maven
repository:

```bash
./mvnw -pl library -am clean install
```

Use this coordinate only with that local installation:

```xml
<dependency>
  <groupId>io.github.enderekici</groupId>
  <artifactId>packed-btree</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Run the core build and tests with JDK 17 or later:

```bash
./mvnw clean verify
```

Build the optional Vector API module with JDK 25:

```bash
./mvnw -Psimd clean verify
```

`validate()` performs a full structural diagnostic; use it in tests and
operational checks, not on hot paths. Choose this library when ordered traversal
and primitive Long storage matter, then benchmark the actual application
workload against its alternatives.

## Measurements

Benchmark results are fixture-specific and come from distinct source snapshots
and workload shapes. The [results index](results/README.md) summarizes accepted
and rejected search experiments, original-versus-optimized and JDK-map
comparisons, String ranges, and concurrent workloads. It links raw data,
verification receipts, and each report's limits. See also the
[benchmark guide](docs/benchmarks.md).

## License and releases

This project is distributed under the [Apache License 2.0](LICENSE). The
release process and its Central Portal manual-publish step are documented in
[docs/releasing.md](docs/releasing.md). See the
[0.1.0 release record](docs/releases/0.1.0.md) for its publication status and
artifact details.
