# Candidate 3 confirmation

Date: 2026-10-25. Host CPU: Apple M4 Pro. JDK: Zulu OpenJDK 25.0.3. JMH 1.37. Source base: `94adfd0e37a1168e80635a9124fac6f82bd8d1e5`. Candidate source SHA-256: `94853ac9b62ff038696271a4b5de80c6110887e4ecf639f400f38767a60ab94e`.

C3 combines early equality return in `Node.index` with one local exclusive upper-bound index per visited node. For exact matches, `index` returns the bitwise complement of the position; get, put, delete, inclusive range start, and exclusive range end decode that form. The range endpoint decode keeps a key equal to `toExclusive` excluded.

## Setup and controls

JDK 25 `-Psimd -pl simd -am clean test-compile` passed; JDK 17 `-pl library test -Dtest=StringBTreeTest` passed 5/5. The all-operation candidate/baseline smoke passed for 16K short String values with setup, range, deletion/refill, churn and teardown checks enabled. A deliberate wrong candidate hash had already been shown to fail under `-foe true` with `Unexpected loaded class` (see candidate 1 screen report).

Every confirmation fork verified code source and SHA for both outer and nested node classes. Candidate `StringBTree` SHA was `a2bbd446d6ea76d2ef3d3c88a7ea7407a2d6f5ca7d51d8258eabf2d4768dbfe8`; candidate `StringBTree$Node` SHA was `da2cb1f658cf4e590dc70be859aa76c4ad37c1b6934a86a6585ff73bd79cd14b`. Frozen baseline `CurrentBaselineStringBTree` and `$Node` hashes were `dc021d2cc18f322bd792e3140b2711b9af8744b696979158df3f2ed1929f60b2` and `33867d8c3f6097e93ef9b5c65415f9292ebc5d41ae01a14badf12223f9331ae8`. Candidate classes loaded from `library/target/classes`; baseline from `simd/target/test-classes`. Classpath started `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar`.

Four finite runs, each with two forks, three 1-second warmups, five 1-second measurements, and `-foe true`, produced 32 JSON result rows. Arm order alternated by fixture to reduce order bias: 16K short candidate/baseline, 262K random baseline/candidate, 262K prefix candidate/baseline, then 16K random overwrite-only baseline/candidate. All values were prebuilt Strings, all query Strings held independent backing storage, and no map-only operation created keys or values.

Each JDK25 invocation used `bench.StringTreeHillclimbBenchmark.*`, `-p size=... -p shape=... -p valueType=string`, and `-wi 3 -w 1s -i 5 -r 1s -f 2 -foe true -rf json`, with the per-fork `string.expected.candidate.{tree,node}.{location,sha256}` and `string.expected.baseline.{tree,node}.{location,sha256}` properties listed above. The four selectors were: 16K short/all five operations; 262K random/all five; 262K prefix/all five; and 16K random/overwrite only. Run order was candidate/baseline, baseline/candidate, candidate/baseline, baseline/candidate. JMH JSON records include the JVM arguments and class fingerprints. Full launch output is in `/tmp/string-c3-confirm-short16.log`, `/tmp/string-c3-confirm-random262.log`, `/tmp/string-c3-confirm-prefix262.log`, and `/tmp/string-c3-confirm-random16-overwrite.log`; raw records are the adjacent JSON files.

## Results

All values are ns/op; `±` is the JMH 99.9% confidence-interval half-width. Gain is candidate point estimate versus baseline (`1 - candidate/baseline`); it is descriptive, not a claim that intervals prove causality.

| Size | Shape | Operation | C3 | Baseline | Gain |
| ---: | --- | --- | ---: | ---: | ---: |
| 16K | short | hit | 94.299 ± 3.014 | 112.181 ± 2.730 | 15.9% |
| 16K | short | miss | 101.355 ± 3.246 | 109.123 ± 4.499 | 7.1% |
| 16K | short | overwrite | 117.071 ± 2.842 | 132.865 ± 1.349 | 11.9% |
| 16K | short | churn | 258.732 ± 12.680 | 283.386 ± 11.876 | 8.7% |
| 16K | short | range64 | 375.575 ± 3.607 | 428.013 ± 3.483 | 12.3% |
| 16K | random | overwrite | 118.954 ± 3.034 | 136.921 ± 1.415 | 13.1% |
| 262K | random | hit | 288.309 ± 3.111 | 323.320 ± 5.567 | 10.8% |
| 262K | random | miss | 305.404 ± 3.608 | 319.622 ± 3.966 | 4.4% |
| 262K | random | overwrite | 308.134 ± 3.110 | 340.779 ± 3.487 | 9.6% |
| 262K | random | churn | 560.110 ± 11.027 | 599.654 ± 10.891 | 6.6% |
| 262K | random | range64 | 799.353 ± 11.720 | 950.760 ± 11.669 | 15.9% |
| 262K | prefix | hit | 428.245 ± 5.829 | 443.366 ± 8.699 | 3.4% |
| 262K | prefix | miss | 397.215 ± 9.543 | 414.404 ± 7.942 | 4.1% |
| 262K | prefix | overwrite | 433.326 ± 9.482 | 467.127 ± 9.056 | 7.2% |
| 262K | prefix | churn | 739.616 ± 20.436 | 779.057 ± 16.968 | 5.1% |
| 262K | prefix | range64 | 1061.495 ± 24.129 | 1590.299 ± 101.986 | 33.3% |

The isolated 16K random overwrite control resolves the short-screen outlier in the improving direction: C3 was 13.1% lower than baseline. All 16 confirmation comparisons have lower C3 point estimates. Range gains are the largest and most consistent. These are single-host/JDK25 results with fixed fixtures; no String-to-Long timing was run. A limited JDK17 performance subset is reported below.

The committed raw data are `candidate3-confirm-short16.json`, `candidate3-confirm-random262.json`, `candidate3-confirm-prefix262.json`, and `candidate3-confirm-random16-overwrite.json`. The short screen remains lower-confidence and is not used for the confirmation conclusions.

## Additional controls and scope

The matched TreeMap control used the same 262K random String/String fixture, operation methods,
prebuilt data, JDK25 runtime, class-hash guards, and 2 forks × 3×1s warmup × 5×1s measurement.
Candidate hit averaged 289.504±6.389 ns/op (99.9% CI 283.115–295.893), versus TreeMap at
304.989±7.322 (297.667–312.311), a 5.1% candidate advantage for this fixture. Baseline was
322.659±6.830 (315.829–329.489). This point-operation result is fixture-specific. For range64,
the initial three-arm run had a TreeMap phase shift, so one pre-authorized range-only repeat used
8×1s warmup and 5×1s measurement: candidate 796.392±16.795 (779.597–813.187), baseline
950.634±17.071 (933.563–967.705), TreeMap 989.373±35.585 (953.788–1024.958). Candidate and
baseline/TreeMap intervals were disjoint in the stationary repeat. Initial matched data and repeat
JSON are `candidate3-confirm-treemap-random262.json` and `candidate3-range-repeat.json`; logs are
in `/tmp/string-c3-confirm-treemap-random262.log` and `/tmp/string-c3-range-repeat.log`.

A JDK17 subset on the 262K random fixture ran hit, churn, and range64 for candidate and baseline
(2 forks × 3×1s warmup × 3×1s measurement; six rows, exit 0). Hit was 212.582±15.865 ns/op for
C3 versus 248.296±13.321 for baseline, with disjoint intervals. Churn (466.873±22.017 vs
496.880±19.104) and range64 (629.421±21.268 vs 691.533±51.848) intervals overlapped, so these
JDK17 results are inconclusive beyond the hit comparison. Raw JSON is
`candidate3-jdk17-decisive.json`; its `jvmArgs` records the exact guards: candidate tree/node
SHA-256 `fe423f0d9d612567820fe384cc7ecf1987a70621dbdf9c58c824228b8da03fd5` /
`19b4fd06c5b39149233a7eaeb762c4df2489ca50af768ceb2b32771d7f53a96b`, baseline tree/node
`7230dc997c26b6395f7013a747838f0dd5e63dacbc1ecda8e1581566eac324b4` /
`ff6a10deb1b87faf15c57da78227b4600e889e6b86674b4a3008868ffb6039df`. The JDK17 log is
`/tmp/string-c3-jdk17-decisive.log`.

JOL GraphLayout estimates at 262K entries were identical between baseline and C3: 1,085,819
reachable objects and 31,255,640 bytes for random keys; 1,085,819 objects and 39,644,248 bytes
for common-prefix keys. JOL could not attach Instrumentation and used HotspotUnsafe estimates, so
these are estimates rather than measured retained heap. Receipt: `/tmp/string-c3-jol.log`.

The JDK17 `-pl library package` Maven run completed BUILD SUCCESS and 15 tests (StringBTreeTest
5, LongBTreeTest 6, ConcurrentLongBTreeTest 4; zero failures/errors/skips), producing the library,
source, and Javadoc jars. Log: `/tmp/string-c3-jdk17-package.log`. No separate packaged consumer
probe was run in this track; integration owns final clean verification and probe. No String→Long
performance timing was run. Benchmark conclusions apply only to the tested String→String fixtures,
operations, JDKs, and sizes.
