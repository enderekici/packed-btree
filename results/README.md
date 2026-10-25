# Benchmark results index

These campaigns use different source revisions, fixtures, JDKs, and concurrency
models. Treat each report as a bounded experiment, not a universal ranking.
The reports retain raw JMH JSON, setup/teardown checks, source fingerprints, and
limits where available. JMH error columns are 99.9% confidence-interval
half-widths unless the report says otherwise.

## Current implementation and accepted changes

| Question | Result | Evidence |
| --- | --- | --- |
| Does the current default Long tree need its linear node search? | Against the exact binary-search control, the accepted degree-16 linear search was faster in all 20 JDK 25 confirmation cells; the six-cell JDK 17 decisive subset also favored linear. This search campaign predates the packed mutable node layout below; do not multiply the reported gains. | [Search confirmation](hillclimb/c2-confirm/README.md), [ledger](../HILLCLIMB.md) |
| Does packing mutable Long keys and values into one node array reduce footprint? | JOL estimated 4.2% fewer retained bytes. In the JDK 25 latency confirmation, 3/20 intervals favored packed layout, 17 overlapped, and none favored baseline; no cell's mean was over 5% slower. The JDK 17 subset had three overlapping intervals. This is a footprint result, not broad latency improvement or equivalence. | [Long round-three report](hillclimb-round3/long/README.md) |
| Does String covered-subtree traversal help ordered ranges? | Range64 means were 11.3–15.7% lower in three JDK 25 fixtures and 14.9–20.6% lower in the JDK 17 subset. Width-one prefix range was 0.8% slower with overlapping intervals; small-width results remain inconclusive. | [String round-three report](hillclimb-round3/strings/README.md), [String ledger](../HILLCLIMB-STRINGS.md) |
| What did the earlier String search optimization measure? | The previously merged C3 exact-match index and per-node exclusive upper-bound changes had lower point estimates in 16 String-to-String cells; 15/16 99.9% intervals separated. The large common-prefix miss overlapped. JDK 17 hit evidence was favorable; churn and range were inconclusive. | [C3 confirmation](hillclimb-round2/strings/candidate3-confirmation.md) |

The default-search confirmation's measured gains and the packed-layout footprint
result concern different candidate changes. Do not add or multiply their ratios.
The tracked [hill-climb ledgers](../HILLCLIMB.md) and
[String ledger](../HILLCLIMB-STRINGS.md) include rejected candidates and
invalid-run corrections. Round-two Long unrolling, degree 8, and degree 32 were
rejected; round-three Long measured the packed storage layout only.

## Comparisons with other maps

### Original prototype, optimized default, and `TreeMap`

The matched one-million-entry, one-thread JDK 25 comparison used the exact
original prototype, optimized Long default at the recorded feature revision,
and `TreeMap`. It is not a comparison against the current round-three packed
layout; the report records its exact source snapshots.

| Operation | Original B-tree | Optimized B-tree | `TreeMap` |
| --- | ---: | ---: | ---: |
| Hit | 133.205 ± 4.203 ns | 102.202 ± 2.079 ns | 314.419 ± 9.413 ns |
| Miss | 124.875 ± 2.959 ns | 92.795 ± 1.753 ns | 473.725 ± 20.364 ns |
| Changing overwrite | 134.513 ± 2.451 ns | 100.570 ± 2.545 ns | 318.300 ± 12.025 ns |
| Remove/insert churn | 269.535 ± 5.374 ns | 202.745 ± 3.607 ns | 981.186 ± 82.723 ns |
| Range of 64 | 176.491 ± 5.858 ns | 118.179 ± 6.130 ns | 718.979 ± 19.621 ns |

This was one JDK 25.0.3 / Apple M4 Pro fixture, with shuffled dense signed keys
and 2 forks × 3 warmups × 5 measurements. A longer warmup was used for the
focused TreeMap range result after iteration scores changed during the first
run. No memory or concurrent comparison was run. See the [full report](three-way-1m/README.md)
for raw JSON and provenance.

### Hash-map point operations

The one-thread JDK 25 hash-map campaign measured Long hit, above-maximum miss,
overwrite, and remove/insert churn at 16,384 and 1,048,576 positive dense keys.
All tested `HashMap`, `ConcurrentHashMap`, and fastutil open-hash controls were
faster than the measured B-tree on those point workloads. Hash maps do not
provide ordered range traversal; this campaign did not test full-width or
collision-heavy inputs. `ConcurrentHashMap` was measured with one thread, so the
result is not a concurrency-scaling comparison.

See the [hash-map report](hashmaps/report.md), [raw JMH JSON](hashmaps/jdk25-point.json),
and [allocation sample selection](hashmaps/allocation-adjusted.json).

### String maps

The original String-key benchmark matrix compares B-tree, `TreeMap`, hash maps,
and skip-list fixtures for String and Long values. It used JDK 25 and short
single-thread runs; its detailed result is a historical baseline, not a
replacement for the later guarded C3 and covered-range experiments. The report
covers 16K point/range fixtures and a 262K random String hit sample. See the
[String benchmark report](string-benchmarks/REPORT.md) and
[String-key guide](../docs/string-keys.md).

## Concurrency results

These reports answer distinct questions:

- [Concurrent Long C1 confirmation](concurrency-next/report.md) compares the
  earlier concurrent Long baseline with C1's linear search at 262,144 keys and
  1/8-thread read, range, and write workloads. Read-only throughput was about
  41% higher at 1 and 8 threads and ranges 13–19% higher in this fixture.
  Unpaced 7-reader/1-writer throughput was 48.6% lower; paced writers were
  matched near 498k writes/s and readers were faster. C2's packed-node layout
  was rejected: allocation fell about 3.5%, with no coherent throughput gain
  and an inconclusive unpaced writer repeat.
- [Current mutable versus concurrent maps](single-vs-concurrent/README.md)
  compares PR2 mutable Long/String maps to PR3 concurrent Long/String maps at
  one thread. It compares whole implementations, which have different storage,
  path-copy behavior, and (for String) range traversal; it does not isolate
  lock cost or measure multi-reader scalability. On JDK 25, concurrent Long
  overwrite throughput was 77% lower and churn 70% lower. Concurrent String
  random hit and range64 were higher, while overwrite and churn were lower.
  Its JDK 17 subset did not reproduce the JDK 25 String hit or range gains.
- [Concurrent 1/2/4/8-thread data](../docs/concurrency.md#timings) is the
  historical pre-C1 single-key/range workload at one million entries, including
  `ConcurrentSkipListMap` and `ConcurrentHashMap`. The 1M point/range run measured
  the pre-C1 concurrent B-tree implementation:
  its range result beat skip-list range in that run, while point reads were
  slower than `ConcurrentHashMap`. Serialized writer cost rose in mixed
  workloads at 4/8 threads. This is historical and separate from the 262K C1
  comparison above.

The [concurrency ledger](../CONCURRENCY-LEDGER.md) records accepted and
rejected concurrent changes. No campaign establishes BBTree-style
versioned-node scalability or predicts application behavior.

## Optional search implementations

- [SIMD results](SIMD-BENCHMARK.md) are explicitly **pre-linear-search**. Their
  current-default comparison used the old binary-search source snapshot. The
  dense 262K row favored Vector API by mean; intervals and workload scope are in
  the report. It is not timing evidence for the final scalar default or a
  portable win.
- [Compressed-routing results](../docs/compressed-routing.md) used an earlier
  integrated scalar source snapshot. The 1M point run found compressed routing
  3–4% slower on sequential and full-width random keys, inconclusive on common
  prefixes, with about 17% more retained memory. It remains optional and
  experimental. The memory comparison predates the current packed mutable
  Long node layout.
- [Historical prototype results](../RESULTS.md) predate the Maven/package
  reorganization and describe commit `dcb5176`; do not present them as current
  source measurements.

## Verification and reports

| Evidence | Location |
| --- | --- |
| Current combined PR2 + PR3 JDK 17 / JDK 25 SIMD `clean verify`, packaged probes, JAR hashes | [final-integration receipt](final-integration/README.md) |
| Earlier PR2 Long/String-only verification | [historical receipt](final-integration-verification.md) |
| JDK 25 SIMD baseline campaign | [SIMD benchmark report](SIMD-BENCHMARK.md) |
| Default scalar-versus-control diagnostic | [default search report](default-search/README.md) |
| Compressed memory and JMH data | [compressed report](../docs/compressed-routing.md), [`results/compressed/`](compressed/) |
| Original prototype one-million-entry source/command/raw result | [three-way report](three-way-1m/README.md) |
| Raw single-thread concurrent maps | [`results/concurrent/`](concurrent/) |
| Default benchmark source/environment manifests | [`results/`](.) root JSON files |

A reproducible command template and comparison rules are in the
[benchmark guide](../docs/benchmarks.md). Each report is authoritative for its
own raw files, source revision, setup, and limits; no cross-campaign ratio is
implied here.
