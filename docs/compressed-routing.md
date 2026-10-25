# Compressed routing keys

`CompressedLongBTree` is an opt-in variant. `LongBTree` retains its current node
layout and scalar signed-long lower-bound search.

Each compressed node retains canonical `long[]` keys and has a `short[]` routing
array. When the node's first and last keys share their signed high 48 bits, the
node records that prefix and copies each low 16-bit suffix into the routing
array. A lookup compares the signed prefix first, then uses unsigned suffix
comparisons. If the node spans prefixes, it falls back to scalar `long[]`
search. Since keys in a node are sorted, matching endpoint prefixes proves all
keys in that node share the same prefix.

The short values alone are not keys: full keys remain authoritative for
collision checks, values, ranges, splits, deletes, and validation. Route metadata
is recomputed after every key mutation and structural operation (insert, split,
predecessor/successor replacement, erase, borrow, and merge). `validate()` checks
that each compact flag, prefix, and suffix still matches the full keys.

This representation does not reduce total memory. It retains the full-key
arrays and adds one short array per node, plus a prefix and compact flag. The
benchmark compares lookup latency and retained object-graph size against the
scalar tree for sequential, full-width random, and common-prefix keys. Results
will be recorded under `results/compressed/` after an isolated timing run.

## Measurement

The JOL retained-object-graph measurement at 1,048,576 mappings showed identical
pre-change and current scalar size. The compressed variant retained roughly
4.7 MB more and added about 49,000 objects (the per-node route arrays):

| Keys | Pre-change scalar | Current scalar | Compressed |
| --- | ---: | ---: | ---: |
| Sequential | 27,843,544 B (26.55 B/map) | 27,843,544 B | 32,561,176 B (31.05 B/map) |
| Full-width random | 27,777,192 B (26.49 B/map) | 27,777,192 B | 32,484,264 B (30.98 B/map) |
| Common prefix | 27,843,544 B (26.55 B/map) | 27,843,544 B | 32,561,176 B (31.05 B/map) |

The run used JDK 25.0.3 on an Apple M4 Pro, 1 GiB initial / 4 GiB maximum
heap, and fresh JVMs for each measurement. `GraphLayout` reported retained bytes
and object counts; its Serviceability Agent attach warning did not prevent the
JOL graph measurements. Raw rows are in `results/compressed/memory.csv`.

Timed lookup measurements are pending the exclusive JMH window. The fixture is
`bench.RoutingBenchmark`; run `hit` for both search modes and all three
distributions. No speedup is assumed.
