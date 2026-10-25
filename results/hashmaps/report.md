# LongBTree versus hash maps: single-thread point operations

Measured 2026-10-25 on Apple M4 Pro (arm64), macOS 27.0, 64 GiB RAM, Zulu JDK 25.0.3, JMH 1.37. Average time, one thread, two forks, two 1-second warmups and three 1-second measurements per fork, fixed 1 GiB G1 heap, `-prof gc`. All 32 cases, 64 fork runs, and 192 measured iteration samples completed with no failed trials or fixture assertions.

Each cell is mean ± JMH 99.9% error in ns/op. Lower is faster. `HashMap` is boxed and not thread-safe; `ConcurrentHashMap` is boxed and thread-safe. Both were measured with one thread. Fastutil is the primitive boxing control. Churn is one remove plus one insert. Hit keys are shuffled dense evens outside the usual Long cache, with a fixed seed and common values. Misses cycle through `[2 * size + 4096, 3 * size + 4095]`, above the largest stored key; the interval alternates even and odd values. Hash maps are pre-sized. Setup and teardown validate the fixture; trial teardown can contaminate the final GC profiler sample in each fork.

`Long.hashCode` preserves its low bits for this positive dense-key fixture. With power-of-two-sized tables, misses therefore see a mixture of empty and occupied buckets, and many odd misses map to empty buckets. This is a valid dataset, but it does not cover full-width keys or collision-heavy distributions.

## 16,384 mappings

| Operation | LongBTree | HashMap<Long,Long> | ConcurrentHashMap<Long,Long> | fastutil Long2LongOpenHashMap |
|---|---:|---:|---:|---:|
| hit | 61.1 ± 1.9 | 4.4 ± 0.1 | 5.5 ± 0.0 | 2.6 ± 0.0 |
| miss | 12.3 ± 0.3 | 2.4 ± 0.0 | 2.9 ± 0.1 | 2.7 ± 0.2 |
| overwrite | 65.6 ± 3.5 | 7.6 ± 0.2 | 12.2 ± 0.2 | 3.6 ± 0.2 |
| churn | 154.3 ± 3.4 | 14.0 ± 0.2 | 17.8 ± 1.1 | 24.2 ± 2.1 |

### GC allocation at 16,384 mappings

| Operation | LongBTree | HashMap | ConcurrentHashMap | fastutil open hash map |
|---|---:|---:|---:|---:|
| hit | ≈0 | 24.0 | 24.0 | ≈0 |
| miss | ≈0 | 24.0 | 24.0 | ≈0 |
| overwrite | ≈0 | 48.0 | 48.0 | ≈0 |
| churn | 1.6 | 104.0 | 104.0 | ≈0 |

## 1,048,576 mappings

| Operation | LongBTree | HashMap<Long,Long> | ConcurrentHashMap<Long,Long> | fastutil Long2LongOpenHashMap |
|---|---:|---:|---:|---:|
| hit | 135.1 ± 3.8 | 20.5 ± 1.3 | 27.1 ± 1.3 | 12.4 ± 0.4 |
| miss | 17.7 ± 0.4 | 2.5 ± 0.0 | 2.8 ± 0.1 | 13.3 ± 0.5 |
| overwrite | 141.0 ± 6.0 | 40.1 ± 1.7 | 45.1 ± 1.8 | 11.3 ± 0.9 |
| churn | 269.6 ± 10.0 | 30.8 ± 1.9 | 37.0 ± 1.7 | 50.1 ± 4.2 |

### GC allocation at 1,048,576 mappings

| Operation | LongBTree | HashMap | ConcurrentHashMap | fastutil open hash map |
|---|---:|---:|---:|---:|
| hit | ≈0 | 24.0 | 24.0 | ≈0 |
| miss | ≈0 | 24.0 | 24.0 | ≈0 |
| overwrite | ≈0 | 48.0 | 48.0 | ≈0 |
| churn | 0.1 | 104.0 | 104.0 | ≈0 |

GC allocation is B/op from four samples per case: the first two measured iterations in each fork. The final measured iteration in each fork is excluded because level-trial teardown validates all entries immediately afterward. Values below 0.01 B/op are profiler noise. Raw unadjusted six-sample results are in `jdk25-point.json`; included and excluded GC samples are in `allocation-adjusted.json`.

Ordered range operations are inapplicable to hash maps. These point-operation measurements do not establish range behavior, concurrent scalability, or application-level impact. The run is on a shared developer machine; small mean differences and overlapping error estimates are inconclusive.

On this fixture, every tested hash map is faster than LongBTree for the four point operations at both sizes. The point-operation results do not measure the ordered traversal behavior that distinguishes LongBTree from these hash maps.

JMH emitted a JDK 25 `sun.misc.Unsafe.objectFieldOffset` terminal-deprecation warning. All cases completed successfully. No runner crashes or fixture assertion failures occurred.
