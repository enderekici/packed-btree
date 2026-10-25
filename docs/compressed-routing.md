# Compressed routing keys

`CompressedLongBTree` is an opt-in variant. `LongBTree` keeps scalar signed-long
lower-bound search by default.

Each compressed node retains canonical `long[]` keys and has a `short[]` routing
array. When the node's first and last keys share their signed high 48 bits, the
node records that prefix and copies each low 16-bit suffix into the routing
array. A lookup compares the signed prefix first, then uses unsigned suffix
comparisons. If the node spans prefixes, it uses scalar `long[]` search by
default. Since keys in a node are sorted, matching endpoint prefixes proves all
keys in that node share the same prefix. `CompressedLongBTree(SearchStrategy)`
accepts a fallback for noncompact nodes; compact nodes continue to use their
suffix arrays. `LongBTree(SearchStrategy)` applies a custom lower bound to every
node.

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

A fresh integrated JOL comparison at 1,048,576 mappings measured the current
branch against the exact pre-hook scalar control and the compressed variant.
The current scalar and pre-hook control had equal retained size for all tested
distributions. Compression retained about 4.7 MB more and about 49,000 more
objects in these fixtures:

| Keys | Pre-hook control | Integrated scalar | Integrated compressed |
| --- | ---: | ---: | ---: |
| Sequential | 27,843,544 B (26.55 B/map) | 27,843,544 B (26.55 B/map) | 32,561,176 B (31.05 B/map) |
| Full-width random | 27,777,192 B (26.49 B/map) | 27,777,192 B (26.49 B/map) | 32,484,264 B (30.98 B/map) |
| Common prefix | 27,843,544 B (26.55 B/map) | 27,843,544 B (26.55 B/map) | 32,561,176 B (31.05 B/map) |

The measurements used JDK 25.0.3 on an Apple M4 Pro, 1 GiB initial / 4 GiB
maximum heap, one fresh JVM per tree and distribution, and `GraphLayout` retained
object graphs. Object counts and all raw rows, including the earlier track-level
measurements, are in `results/compressed/memory.csv`. The JOL attach warning did
not prevent graph measurement. These results quantify retained memory only;
they do not establish lookup performance.

The JMH point-hit run measured scalar versus compressed routing for 1,048,576
mappings, with two forks, three 1-second warmups and five 1-second measurements
per fork on the same JDK 25.0.3 / Apple M4 Pro host:

| Keys | Scalar | Compressed | Change |
| --- | ---: | ---: | ---: |
| Sequential | 140.5 ± 4.1 ns/op | 145.0 ± 3.9 ns/op | 3.2% slower |
| Full-width random | 140.0 ± 2.8 ns/op | 145.6 ± 3.8 ns/op | 4.0% slower |
| Common prefix | 152.4 ± 32.8 ns/op | 147.9 ± 8.6 ns/op | 3.0% faster, inconclusive |

JMH's reported errors are 99.9% confidence intervals. The common-prefix
intervals overlap substantially, so this run does not establish a speedup.
Compressed routing was slower for the other two distributions and used about
17% more retained memory in all three. Treat this as an experimental tradeoff,
not a general optimization. The exact run settings, machine details, source
hashes, console log, and raw JSON are in `results/compressed/`.
