# Mutable versus concurrent B-tree comparison

This is a one-thread head-to-head of the latest mutable APIs from PR2 commit
`3022634d48a4c70a78ab8ff62ccd16a635d5252d` and concurrent APIs from PR3 commit
`443451135a51165986b0727115b12cd14a20d2af`. It measures whole-map implementations,
not the isolated cost of a lock: the Long implementations use different node
layouts and write paths, and the String implementations also differ in their
range traversal algorithms. It says nothing by itself about multi-reader
scalability; earlier concurrent-only multi-thread results are separate evidence.

The 38 primary rows are paired by workload and fixture: Long 262,144 entries
with hit, above-maximum miss, overwrite, remove/insert churn, and range64;
Long 16,384 with hit, overwrite, range64; random String at both sizes with all
five operations at 262,144 and hit/overwrite/range64 at 16,384; and prefix String
at 262,144 with hit/overwrite/range64. Each pair has one mutable and one
concurrent row. The harness uses one thread, 2 forks, 3×1s warmups and 5×1s
measurements. Arm order alternates per pair. Both arms use fixed-seed shuffled
insertion and query/range order, prebuilt equal-but-distinct String queries,
prebuilt alternating values, constant-cardinality churn, and cached range
consumers. Range checks count on each call; setup checks exact first/middle/last
keys, values and ordering, and teardown checks every mapping, absent churn mate,
cardinality and structural validation.

Each fork prints the loaded class CodeSource and SHA-256 for the selected outer
class, Node, and concurrent State where present. Setup rejects a source directory
or class hash mismatch. The benchmark jar contains the normal module classes,
so the runtime classpath places two isolated override directories before it:
one compiled from PR2 mutable sources and one from PR3 concurrent sources. Only
the selected map is instantiated in a trial. The deliberately wrong-hash control
must fail with `-foe true` before its benchmark runs.

## Reproduction

In the repo worktree with both referenced Git objects available, export the four
source files to temporary directories without changing project sources:

```sh
TMPROOT=$(mktemp -d /tmp/svc-compare.XXXXXX)
mkdir -p "$TMPROOT/pr2-src/io/github/enderekici/packedbtree" \
  "$TMPROOT/pr3-src/io/github/enderekici/packedbtree" \
  "$TMPROOT/mutable-classes" "$TMPROOT/concurrent-classes"
git show 3022634d48a4c70a78ab8ff62ccd16a635d5252d:library/src/main/java/io/github/enderekici/packedbtree/LongBTree.java > "$TMPROOT/pr2-src/io/github/enderekici/packedbtree/LongBTree.java"
git show 3022634d48a4c70a78ab8ff62ccd16a635d5252d:library/src/main/java/io/github/enderekici/packedbtree/StringBTree.java > "$TMPROOT/pr2-src/io/github/enderekici/packedbtree/StringBTree.java"
git show 443451135a51165986b0727115b12cd14a20d2af:library/src/main/java/io/github/enderekici/packedbtree/ConcurrentLongBTree.java > "$TMPROOT/pr3-src/io/github/enderekici/packedbtree/ConcurrentLongBTree.java"
git show 443451135a51165986b0727115b12cd14a20d2af:library/src/main/java/io/github/enderekici/packedbtree/ConcurrentStringBTree.java > "$TMPROOT/pr3-src/io/github/enderekici/packedbtree/ConcurrentStringBTree.java"
JDK17=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home
"$JDK17/bin/javac" --release 17 -g -d "$TMPROOT/mutable-classes" "$TMPROOT"/pr2-src/io/github/enderekici/packedbtree/*.java
"$JDK17/bin/javac" --release 17 -g -d "$TMPROOT/concurrent-classes" "$TMPROOT"/pr3-src/io/github/enderekici/packedbtree/*.java
```

Compile both isolated source sets with the exact `javac --release 17 -g` command
and JDK recorded in `source-and-class-hashes.txt`. Run JMH with classpath
`/tmp/svc-mutable-classes:/tmp/svc-concurrent-classes:benchmarks/target/benchmarks.jar`,
JDK 25, `-bm thrpt -t 1 -f 2 -wi 3 -i 5 -w 1s -r 1s -foe true -rf json`, and
the complete `tree.guard.*` property set captured in `runner-commands.txt`. Each
pair's invocations, raw JSON and fork logs are stored beside this file. Replace
the two `/tmp/svc-*` classpath entries with `$TMPROOT/mutable-classes` and
`$TMPROOT/concurrent-classes`, then recompute class SHA values and use them in
the guard properties from `runner-commands.txt`. Set
`tree.guard.mutable.location` and `tree.guard.concurrent.location` to those same
two class directories as well as updating the class hashes.

Raw rates are the measured quantity. Do not convert throughput to average
latency without labelling it reciprocal throughput; if doing so, invert the
confidence interval bounds as well as the mean. Long misses are above-maximum
keys, while String misses append a near-key suffix, so their rates are not a
cross-key-type comparison. Each table cell reports mean ± JMH score error.

## Results

JDK 25.0.3 results below are ops/s; cells show the mean ± JMH-reported 99.9%
confidence-interval half-width.
Raw JSON retains confidence intervals and per-fork scores. The change column
is the concurrent/mutable ratio of paired means. The run used a single JMH worker thread, so these
rows compare current whole-map implementations and do not measure concurrent
reader scalability.

| Workload | Mutable ops/s | Concurrent ops/s | Change |
|---|---:|---:|---:|
| Long hit, 262k | 12,834,911 ± 219,263 | 12,549,251 ± 148,985 | -2.2% |
| Long above-max miss, 262k | 56,933,978 ± 2,742,866 | 58,861,083 ± 2,013,681 | +3.4% |
| Long overwrite, 262k | 12,364,398 ± 136,994 | 2,842,929 ± 14,720 | -77.0% |
| Long remove/insert churn, 262k | 5,947,668 ± 152,342 | 1,808,604 ± 22,915 | -69.6% |
| Long range64, 262k | 6,378,079 ± 145,125 | 5,914,450 ± 85,277 | -7.3% |
| Long hit, 16k | 32,166,508 ± 1,096,030 | 29,815,898 ± 1,777,764 | -7.3% |
| Long overwrite, 16k | 24,659,896 ± 1,025,518 | 6,339,635 ± 62,129 | -74.3% |
| Long range64, 16k | 9,148,859 ± 123,517 | 8,616,160 ± 151,210 | -5.8% |
| String random hit, 262k | 3,195,646 ± 126,698 | 4,293,893 ± 147,811 | +34.4% |
| String random suffix miss, 262k | 3,080,896 ± 61,011 | 4,078,676 ± 118,302 | +32.4% |
| String random overwrite, 262k | 3,083,439 ± 51,140 | 1,815,956 ± 20,251 | -41.1% |
| String random remove/insert churn, 262k | 1,592,774 ± 47,780 | 1,115,167 ± 33,230 | -30.0% |
| String random range64, 262k | 739,708 ± 10,481 | 1,390,429 ± 38,058 | +88.0% |
| String random hit, 16k | 9,559,717 ± 189,982 | 9,398,185 ± 176,986 | -1.7% |
| String random overwrite, 16k | 8,258,281 ± 87,374 | 4,553,996 ± 47,238 | -44.9% |
| String random range64, 16k | 2,680,879 ± 31,089 | 2,320,407 ± 21,517 | -13.4% |
| String prefix hit, 262k | 2,274,973 ± 84,420 | 3,010,156 ± 70,712 | +32.3% |
| String prefix overwrite, 262k | 2,053,312 ± 58,523 | 1,510,445 ± 13,885 | -26.4% |
| String prefix range64, 262k | 651,886 ± 17,019 | 1,152,291 ± 24,707 | +76.8% |


The separate JDK 17 subset used the same 262,144-entry Long and random-String
fixtures, hit/overwrite/range64, 2 forks and 3×1s warmups plus 3×1s measurements.
This is a runtime cross-check, not a full JDK 17 matrix. The String read gains
seen on JDK 25 did not reproduce on JDK 17; these are workload/runtime-specific
whole-implementation results.

| Workload | Mutable ops/s | Concurrent ops/s | Change |
|---|---:|---:|---:|
| Long hit | 13,511,289 ± 301,702 | 12,780,318 ± 478,036 | -5.4% |
| Long overwrite | 11,967,065 ± 153,793 | 2,855,584 ± 47,551 | -76.1% |
| Long range64 | 6,165,236 ± 151,941 | 5,839,738 ± 205,615 | -5.3% |
| String random hit | 4,346,652 ± 190,020 | 4,194,142 ± 476,332 | -3.5% |
| String random overwrite | 3,883,321 ± 166,871 | 1,721,905 ± 58,268 | -55.7% |
| String random range64 | 1,483,412 ± 46,415 | 1,339,461 ± 52,209 | -9.7% |


## Allocation and retained graph probes

A separate bounded ThreadMXBean probe warmed and measured 100,000 sequential
operations per workload on a 262,144-entry primary fixture. It is not a JMH
steady-state allocation result: overwrite is bounded to the same deterministic
prefix and churn alternates through that prefix. Read and range operations
measured zero allocated bytes/op. Writes allocate as expected for path-copy
updates; values below are bytes/op from this bounded probe.

| Type | Operation | Mutable B/op | Concurrent B/op |
|---|---|---:|---:|
| Long | overwrite | 0 | 3,372.5 |
| Long | remove/insert churn | 1,197.5 | 8,524.9 |
| Random String | overwrite | 0 | 2,184.1 |
| Random String | remove/insert churn | 817.1 | 5,536.2 |

JOL GraphLayout totals for the same 262,144-entry fixture were 6,659,696 B
mutable and 6,954,360 B concurrent Long; random String 31,270,840 B and
31,270,856 B; prefix String 39,659,448 B and 39,659,464 B. These are reachable
map graphs with zero active snapshots, excluding local key/query fixture arrays.
JOL could not attach Instrumentation on this host, so it used HotSpot Unsafe
with guessed compressed-reference layout; treat totals as estimates. No result
measures process RSS or retained historical roots during active snapshots.
