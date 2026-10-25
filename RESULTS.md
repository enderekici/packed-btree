# Measured Java packed-tree results

Historical prototype source: commit `dcb51764891551745b1efd9825a9b55e031a5248`.
These measurements precede the Maven module/package reorganization. The source
hash manifest below refers to that commit. Current build and benchmark commands
are in `README.md`; historical timings are not a fresh run of the Maven layout.

Run: 2026-10-25, Apple M4 Pro (arm64), macOS, 64 GiB RAM, Zulu JDK 25.0.3, JMH 1.37.
Two JVM forks, two 1-second warmups and three 1-second measurements per fork, one thread, fixed 1 GiB G1 heap. Shared developer host; not an isolated performance machine. All 40 average-time cases and four sampled-lookup cases completed without failed trial checks.

## Scope and verdict

The primitive-array B-tree is faster and smaller than the tested JDK ordered maps for these fixtures. It also improves on the primitive AVL baseline, so the observed benefit is not solely removal of boxing. This establishes a useful single-threaded ordered-map prototype, not a production-ready BBTree port or an application speedup.

The implementation includes complete insertion, overwrite, deletion with rebalancing, point lookup and ascending ranges. It excludes compressed routing keys, SIMD, optimistic concurrency, off-heap storage, generic object keys/values and upstream formal proofs. No application code, runtime configuration or deployment was changed.

## Average time

Nanoseconds per operation, lower is better. Each cell is mean ± JMH 99.9% error estimate across six measurements. Churn means one removal plus one insertion. Range means scanning and summing 64 adjacent entries.

### 16,384 mappings

| Operation | Primitive B-tree | TreeMap | Skip-list | Primitive AVL |
|---|---:|---:|---:|---:|
| hit | 59.6 ± 4.5 | 67.4 ± 4.2 | 112.9 ± 34.2 | 60.3 ± 7.4 |
| miss | 55.7 ± 3.3 | 149.0 ± 11.3 | 124.6 ± 34.0 | 80.2 ± 1.5 |
| overwrite | 62.6 ± 2.3 | 81.5 ± 2.2 | 117.0 ± 8.5 | 70.6 ± 2.5 |
| churn | 151.5 ± 2.4 | 233.3 ± 9.1 | 439.2 ± 8.2 | 191.9 ± 2.0 |
| range64 | 129.2 ± 3.7 | 477.5 ± 10.2 | 309.5 ± 5.4 | 280.5 ± 4.6 |

### 1,048,576 mappings

| Operation | Primitive B-tree | TreeMap | Skip-list | Primitive AVL |
|---|---:|---:|---:|---:|
| hit | 133.5 ± 5.0 | 269.2 ± 11.3 | 595.8 ± 30.1 | 184.4 ± 9.9 |
| miss | 122.8 ± 3.6 | 473.0 ± 18.9 | 649.4 ± 32.9 | 249.8 ± 14.1 |
| overwrite | 135.7 ± 5.2 | 331.2 ± 13.8 | 678.0 ± 35.2 | 209.8 ± 15.5 |
| churn | 259.1 ± 8.0 | 971.7 ± 142.6 | 1386.0 ± 155.0 | 506.8 ± 73.6 |
| range64 | 273.4 ± 5.9 | 3188.9 ± 94.5 | 2622.2 ± 118.1 | 600.3 ± 7.5 |

### Relative performance at 1,048,576 mappings

| Operation | Faster than TreeMap | Faster than primitive AVL |
|---|---:|---:|
| hit | 2.02x | 1.38x |
| miss | 3.85x | 2.03x |
| overwrite | 2.44x | 1.55x |
| churn | 3.75x | 1.96x |
| range64 | 11.66x | 2.20x |

Ratios compare measured means; they are not confidence bounds. Small differences and results with overlapping intervals are inconclusive. These benchmarks do not measure concurrent scalability.

## Sampled successful-lookup p99

Separate sample-time run at 1,048,576 mappings, otherwise the same fork/warmup/measurement settings. Sampling includes timer overhead; do not compare these numbers directly with the average-mode means.

| Implementation | Sampled p99, ns |
|---|---:|
| Java primitive B-tree | 333 |
| JDK TreeMap | 666 |
| JDK ConcurrentSkipListMap | 1374 |
| fastutil primitive AVL | 417 |

## Retained memory

JOL 0.17 reachable map graph in a fresh JVM per case with a 4 GiB heap. Includes array slack, entry objects and boxed keys/values; excludes the workload key array, JVM overhead, transient garbage and GC headroom. Skip-list tower layout is randomized. This is not RSS. JOL emitted a Serviceability Agent availability warning; graph measurements still completed without elevated access.

| Implementation | Entries | Retained MiB | Bytes/entry | Objects |
|---|---:|---:|---:|---:|
| Java primitive B-tree | 1,048,576 | 26.53 | 26.53 | 149,531 |
| Java primitive B-tree | 16,384 | 0.42 | 27.14 | 2,391 |
| fastutil primitive AVL | 1,048,576 | 40.00 | 40.00 | 1,048,578 |
| fastutil primitive AVL | 16,384 | 0.63 | 40.01 | 16,386 |
| JDK ConcurrentSkipListMap | 1,048,576 | 83.99 | 83.99 | 3,669,675 |
| JDK ConcurrentSkipListMap | 16,384 | 1.31 | 84.13 | 57,428 |
| JDK TreeMap | 1,048,576 | 88.00 | 88.00 | 3,145,729 |
| JDK TreeMap | 16,384 | 1.38 | 88.00 | 49,153 |

At 1M entries: 69.9% less retained memory than TreeMap and 33.7% less than primitive AVL.

## Workload allocation

Bytes per operation at 1,048,576 mappings. The allocation comparison uses the first two measured iterations in each fork (four samples), consistently for all cases. JMH includes trial teardown in the final iteration; teardown scans the fixture and validates tree structure, allocating outside the timed operations. Those allocations must not be attributed to map operations. The full unadjusted profiler output is preserved in average.json; the explicit adjustment and four input samples per case are in allocation-adjusted.json. Timing means use all six samples. Values below 0.01 B/op are profiler noise, not a claim of mathematically zero allocation.

| Operation | Primitive B-tree | TreeMap | Skip-list | Primitive AVL |
|---|---:|---:|---:|---:|
| hit | 0.001 | 0.002 | 0.004 | 0.001 |
| miss | 0.001 | 0.003 | 0.004 | 0.002 |
| overwrite | 0.001 | 48.002 | 48.005 | 0.001 |
| churn | 5.292 | 88.007 | 132.019 | 40.003 |
| range64 | 0.002 | 128.022 | 112.018 | 72.004 |

The initially interrupted run rebuilt fixtures at every iteration and contaminated the GC profiler. It is discarded completely; no timing or allocation data from that run appears in these tables.

## Correctness and review

- 2,820,000 counted ordered/randomized operations, plus exhaustive content checks and boundary cases, passed against TreeMap.
- Every signed long key/value is supported, including both extremes; repeated overwrite/deletion, missing deletion, full drain, refill, ranges and randomized full-width keys were exercised.
- All four benchmark adapters passed direct update/delete/range contract checks.
- A deliberately mismatched reference value confirmed the differential oracle detects an error.
- Every benchmark fork validated cardinality and all surviving keys after its trial; prototype trials also checked ordering, occupancy, equal leaf depth and child references.
- Solo source/benchmark review found no remaining defect in the implemented scope. The setup/profiler issues and their treatment are documented above. No external reviewer or concurrent correctness qualification was run.

## Application consequence

This is worth considering where profiling identifies a large, local, primitive-key ordered index. It does not justify replacing hash maps, distributed state, or application snapshot protocols. The measured fixture uses shuffled dense positive integer keys and fixed-size long values; correctness also covers full-width random keys, but their performance, strings, UUIDs, growing maps, multiple readers/writers, x86, SIMD and an actual application caller are untested.

Before adoption, identify the real ordered-index caller, benchmark its data and operation mix, and qualify the required concurrency/iteration semantics. The current prototype remains standalone.

## Current Maven-layout search follow-up

The tables above are historical prototype results and do not include later
integration features or the default-search optimization. The current degree-16
default uses a linear lower-bound scan; its guarded confirmation against the
binary-search source from commit `57ea447d94078b2a0872569b09bb88986b71f886`,
including JDK 17 and 25 results and retained-memory measurements, is documented
in [`results/hillclimb/c2-confirm/README.md`](results/hillclimb/c2-confirm/README.md)
and tracked in [`HILLCLIMB.md`](HILLCLIMB.md). Those fixture-specific results
are separate from the historical prototype tables above.

A separate matched comparison now measures the original prototype, optimized
default and `TreeMap` at 1,048,576 entries using the same five operations. See
[`results/three-way-1m/README.md`](results/three-way-1m/README.md) for the table,
per-fork provenance checks, raw JSON and fixture limits.

## Evidence

- `results/average.json`: all average-time data, confidence intervals and raw fork samples.
- `results/lookup-sample.json`: sampled lookup distributions.
- `results/allocation-adjusted.json`: all four included allocation samples and adjustment rule.
- `results/memory.json`: retained graph sizes.
- `results/environment.json`: runtime and methodology.
- `results/source-sha256.json`: exact source/build-script hashes used for the benchmarks.
- `results/build.log` and `results/correctness.log`: local build/test output (ignored verbose logs).
- `build/dependencies.sha256`: local downloaded dependency hashes (copied into results for provenance).
