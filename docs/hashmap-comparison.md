# Long-key point-operation comparison

This comparison measures the single-thread point-operation cost of `LongBTree`
against `HashMap<Long, Long>`, `ConcurrentHashMap<Long, Long>`, and fastutil's
`Long2LongOpenHashMap`. The two JDK implementations include their normal key and
value boxing. The fastutil implementation is the primitive-key/value control.
The comparison does not measure concurrent access or ordered scans; range
operations are inapplicable to the hash-map implementations.

Each trial builds the same shuffled, dense even-key fixture from a fixed seed.
Keys start at 4096, outside the usual `Long` boxing cache; values use the same
deterministic key-derived mapping. The 16,384- and 1,048,576-entry fixtures are
created in `@Setup(Level.Trial)`. Hash maps are sized for the expected mapping
count before insertion. Timed methods cover successful lookup, an absent key,
overwrite, and churn (one remove plus one insert while keeping cardinality
constant). Hit keys are even values from the fixture. Misses cycle through the
contiguous interval `[2 * size + 4096, 3 * size + 4095]`, which is above the
largest stored key and includes alternating even and odd values.

`Long.hashCode` preserves its low bits for this positive dense-key fixture, so
the power-of-two-sized JDK hash tables see a mixture of empty and occupied
buckets for misses; many odd miss keys land in empty buckets. This is a valid
workload, but it does not cover full-width keys or collision-heavy hash
distributions.

Fixture validation checks a positive hit, a positive miss, and cardinality
before measurement. Teardown checks every surviving key and expected value and
the final cardinality. The teardown scan and setup allocations happen outside
the timed operation, but the last JMH iteration can include teardown in GC
profiler totals. Exclude that teardown allocation when interpreting `gc.alloc`;
do not attribute fixture construction or exhaustive checking to a map operation.

The campaign uses JMH average time, one thread, two forks, two 1-second warmups
and three 1-second measurements per fork, plus `-prof gc`. See
[`results/hashmaps/report.md`](../results/hashmaps/report.md) for measured
results and [`results/hashmaps/jdk25-point.json`](../results/hashmaps/jdk25-point.json)
for the full unadjusted data. The GC allocation adjustment and its excluded
samples are in `results/hashmaps/allocation-adjusted.json`.

## Results

All 32 implementation/operation/size cases completed in 64 forks with no
fixture failures. The point-operation comparison is separate from the ordered
range results in `RESULTS.md`; do not combine their miss measurements or infer
hash-map range performance.
