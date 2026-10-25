# Long-map hill-climb round 2

Current baseline: feature commit `94adfd0e37a1168e80635a9124fac6f82bd8d1e5`,
which already uses linear lower-bound search at degree 16. Frozen control is
`simd/src/test/java/bench/Round2BaselineLongBTree.java`, cloned from that
commit's `library/src/main/java/io/github/enderekici/packedbtree/LongBTree.java`.
Reverse-normalizing package/class names is byte-for-byte exact. The control
class is expected from `simd/target/test-classes`; candidate `LongBTree` must
load from `library/target/classes`. Every fork checks tree and Node source
location and bytecode hashes. JMH uses JDK 25 at
`/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home`, G1, a
1 GiB fixed heap and `-foe true`; smoke and timed results must have expected
finite row counts. The runtime classpath is library classes, simd test classes,
then the shaded benchmark JAR, in that order.

The selected-map fixture is `Round2LinearUnrolledBenchmark` and reuses
`SearchScreenFixture`'s deterministic 4,096/262,144 dense/full-width key sets,
misses and 4,096 64-entry range windows. Each JMH trial constructs only one
map. Point queries cycle all fixture keys. Overwrite updates both map and
expected values; churn removes/reinserts absent alternates at fixed cardinality
and updates the expected key/value arrays. Setup checks known hits/misses and
first/middle/highest-rank range windows of exactly 64 entries. Teardown checks
all keys and values, size and tree invariants. Measurements are exploratory
screens unless explicitly labelled confirmation.

The shared targeted correctness command was
`JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml -pl simd -am -Psimd -Dtest=LongBTreeTest,VectorSearchTest,Round2LongBTreeDifferentialTest test`.
Each screen used
`$JAVA_HOME/bin/java -cp "$PWD/library/target/classes:$PWD/simd/target/test-classes:$PWD/benchmarks/target/benchmarks.jar" org.openjdk.jmh.Main 'bench.Round2<CANDIDATE>Benchmark.*' -p size=4096,262144 -p distribution=dense,fullWidth -p implementation=baseline,candidate -wi 1 -w 250ms -i 2 -r 250ms -f 1 -foe true -jvmArgsAppend "$JVMARGS" -rf json -rff /tmp/hillclimb-r2-<candidate>-screen.json`.
`JVMARGS` set `--add-modules=jdk.incubator.vector -Xms1g -Xmx1g -XX:+UseG1GC`
and the eight `bench.expected.{baseline,candidate}.{tree,node}.{location,sha256}`
properties. Smoke used the same command with 4096/dense, one 100ms warmup and
one 100ms measurement. The JMH output and raw JSON named above are the actual
receipts.

| Candidate | Outer tree SHA-256 | Node SHA-256 |
| --- | --- | --- |
| Frozen 94adfd0 control | `8d066b5d882be85d0a3f5c22465846f0295aa35103908bda4e2408fd5a4ecfbd` | `44e61ca9a42a39f0993587e345eb9c002ad7ba856cd34c5b0ea76fc66acd200b` |
| C5 unrolled degree 16 | `c364250453c1b10f9995ed22a9a8f1f126318b0055fb34cb921f8bf4c3d55ed3` | `72885160c716b12d85ccb6a20c7b258c8ca389c759a5b92b015fd52221cd9814` |
| C6 degree 8 linear | `b66d5d4a6bda40ea2e2536bc936c2fd60fb46eb1e5faf889ffef22e44489fe63` | `3799e6840f611af155ebf3463db9909e1c17f500bfdd2b3311646d9195ed1e04` |
| C7 degree 32 linear | `32abc1ac7257977c56075a10a290657ed52058e6d42869e4ace6078bb2111142` | `7b015946ce07cae040ec9bcf5135fc8694bf0a1063fa746319140bd9a77b3980` |

## Candidate C5 — four-at-a-time linear lower bound

- Hypothesis: keep degree 16 and signed lower-bound semantics, but compare up
to four adjacent keys before repeating the loop. This may reduce loop-control
work while preserving early exit.
- Candidate changes only `Node.index(long)` in `LongBTree`; no node fields,
public API, strategy behavior, compressed routing or default dependency graph
change. Candidate source SHA-256 before compile:
`05bb179fa2ee22eeee7d744ad69dca1e417ea7038c9651b713e221a7bbcd9c05`.
- Frozen control normalized source SHA-256: `9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503`; renamed file SHA-256:
`3be9f5b356351945d4f712167f27d3eac3ea469cad9af811fb58e758766cdb36`.
- Before timing: run `LongBTreeTest` and `VectorSearchTest` on JDK 25
`-Psimd`; compile baseline and candidate; record actual tree/Node SHA-256 and
`javap` evidence for both. Run an all-arm smoke with positive and deliberately
wrong-hash guards; wrong-hash execution must fail with `-foe true`.
- Screen: five operations × two sizes × dense/full-width keys × baseline and
candidate; one fork, 1×250ms warmup/2×250ms measure. Treat as exploratory.
- Confirmation only if screen gives a coherent improvement: paired blocks for
size×distribution, both arms and five operations, two forks, at least
3×1s warmup/5×1s measurement, with arm order alternating between blocks. Include
JDK 17 decisive checks if broad JDK 25 evidence supports retaining C5.
- Retained layout is unchanged by source inspection; no JOL measurement yet.
- Correctness: final targeted JDK 25 `-Psimd` build passed 9 tests: 6
  `LongBTreeTest`, 1 differential full-width mutation test, and 2
  `VectorSearchTest`. The randomized differential test compares candidate,
  frozen control and `TreeMap` across updates, real removals, full scans and
  ranges.
- Runtime guard: fresh positive smoke passed 10 finite rows with matching
  source locations and hashes for both tree and Node in both arms. The
  deliberately incorrect candidate tree hash failed during fork setup with
  `-foe true`.
- Screen result: C5 rejected without confirmation. The 40-row exploratory
  screen showed 4,096-entry hit/miss/overwrite 11–20% slower and range64
  6.7–6.8% slower; at 262,144 entries point operations ranged from 2.4%
  faster to 1.6% slower, and ranges from 1.4% faster to 2.6% slower. Churn
  varied around ±3%. These one-fork, two-measurement estimates do not establish
  precision; the small-tree regressions and lack of a coherent broad win do
  not justify confirmation.
- Raw output: `/tmp/hillclimb-r2-c5-screen.{log,json,exit,start}`;
  committed raw screen JSON: `c5-screen.json` (SHA-256
  `0ed9b3aea59eb994ebba21ddf0fa39428768a860113f71cb0566387a546893cd`);
  positive smoke `/tmp/hillclimb-r2-c5-smoke-final.{log,json}`;
  wrong-hash guard `/tmp/hillclimb-r2-c5-wronghash.{log,exit}`; targeted build
  `/tmp/hillclimb-r2-c5-targeted-final.log`. Rejected source patch is preserved
  as `c5-rejected.patch`. No JOL measurement was needed because node layout
  did not change.
- Status: rejected and production source restored to the 94adfd0 linear
  degree-16 implementation. No performance claim is made.

## Candidate C6 — degree 8 with linear search

- Hypothesis: retain the accepted linear lower-bound algorithm while reducing
  node capacity from 31 to 15 keys. Fewer comparisons and smaller per-node
  arrays may reduce cache footprint; the taller tree and extra splits/merges
  may instead hurt point and mutation workloads.
- Change only `LongBTree.DEGREE` (therefore the derived node capacity), with
  StringBTree unchanged. Frozen control remains exact 94adfd0 source. Use
  `Round2DegreeEightBenchmark` and the same sizes, key distributions, five
  operations, runtime fingerprints, positive/negative guards and trial
  teardown checks as C5.
- Candidate production source SHA-256 before compilation:
  `957abbb2bdec040a8bb830647406feff960e5dae865958b55df970e39a1e1bfd`.
  Harness source SHA-256:
  `e3dda73c0d48344639d44d556372000e20fd4d879093a6a32a485f5624a4d580`.
- Targeted JDK 25 `-Psimd` run passed 9 tests (LongBTree 6, randomized
  differential 1, VectorSearch 2). A positive smoke passed 10 finite rows with
  tree and Node source/hash receipts in each arm; the deliberate wrong Node
  hash failed under `-foe true`. JMH discovery listed all five methods.
- Exploratory screen: 40 finite rows, exit 0. At 4,096 entries, hit/miss were
  7–14% slower, overwrite 13–20% slower, churn 2–15% slower, and range64
  34–36% slower. At 262,144 entries, hit/miss were 24–29% slower, overwrite
  26% slower, churn 17–25% slower, and range64 67–68% slower. It is a
  one-fork/two-measurement screen, but the regressions are broad and large;
  C6 is rejected without confirmation.
- Retained-memory was not remeasured during this screen. Earlier degree-8 JOL
  geometry evidence in `results/hillclimb/c3-degree8-footprint.md` found
  13–15% greater retained bytes, about twice the node count and one or two
  extra levels for the same fixture. That is prior-round structural evidence,
  not a new C6 measurement. C6 source diff is preserved in
  `c6-rejected.patch`; raw JSON is `c6-screen.json` (SHA-256
  `b7aa31627b8e0428abc28f7df3bbcbc00be55f58d60eec03295d8d9ff85107d3`).
- Logs and command receipts: `/tmp/hillclimb-r2-c6-targeted.log`,
  `/tmp/hillclimb-r2-c6-smoke.{log,json}`,
  `/tmp/hillclimb-r2-c6-wronghash.{log,exit}`, and
  `/tmp/hillclimb-r2-c6-screen.{log,json,exit,start}`. The production source
  was restored to degree 16 before preparing the final degree-32 geometry
  candidate.

## Candidate C7 — degree 32 with linear search

- Hypothesis: increase capacity to 63 keys while retaining linear lower-bound
  search. This may reduce height and retained footprint; the longer in-node
  scan may hurt point operations. C6's degree-8 results reject smaller nodes,
  while prior binary-search degree-32 evidence is only motivation and does not
  predict this combination.
- The candidate changes only `LongBTree.DEGREE` and its explanatory capacity
  comment. The frozen control remains exact 94adfd0 source. Use
  `Round2DegreeThirtyTwoBenchmark` with the same selected-map fixture and
  five operations as C5/C6.
- Candidate source SHA-256 before compilation:
  `22a83ef03318ee8dec8ac3761ce1169222162a2b2accd85725b5683231d7eb26`.
  Harness SHA-256:
  `d302f114e89c6cf8b81c93c0212e52225ea4f785015a25c0d7addd99889e40c5`.
- Runtime SHA-256 manifest: frozen outer/Node `8d066b5d882be85d0a3f5c22465846f0295aa35103908bda4e2408fd5a4ecfbd` / `44e61ca9a42a39f0993587e345eb9c002ad7ba856cd34c5b0ea76fc66acd200b`; candidate outer/Node `32abc1ac7257977c56075a10a290657ed52058e6d42869e4ace6078bb2111142` / `7b015946ce07cae040ec9bcf5135fc8694bf0a1063fa746319140bd9a77b3980`.
- Targeted JDK 25 `-Psimd` tests passed 9 cases (LongBTree 6, randomized
  differential 1, VectorSearch 2). Positive smoke passed 10 finite rows with
  expected outer/Node class fingerprints; a wrong candidate hash failed closed
  under `-foe true`.
- Exploratory screen: 40 finite rows, exit 0. At 262,144 entries, hit/miss
  improved 8–11%, overwrite 6–7% and range64 21–23%, but churn regressed
  89–93%. At 4,096 entries, hit/miss/overwrite regressed 26–30%, range64
  improved 14–17%, and churn ranged from 0.4% faster to 8% slower. These are
  one-fork/two-measurement screen estimates, not confirmation. Material point,
  write and churn regressions outweigh the scoped large-tree range/read gains;
  C7 is rejected without confirmation.
- Earlier degree-32 structural JOL evidence in
  `results/hillclimb/c4-degree32-footprint.md` found 6.6–13.9% fewer retained
  bytes at 4,096 and 7.0–7.3% fewer at 262,144. This is prior-round evidence,
  not a new C7 measurement. The rejected diff is `c7-rejected.patch`; raw
  screen JSON is `c7-screen.json` (SHA-256
  `7f70a41168ea567308a9169622337776bc66d563dede88f43af45efccbee880b`).
- Logs and receipts: `/tmp/hillclimb-r2-c7-targeted.log`,
  `/tmp/hillclimb-r2-c7-smoke.{log,json}`,
  `/tmp/hillclimb-r2-c7-wronghash.{log,exit}`, and
  `/tmp/hillclimb-r2-c7-screen.{log,json,exit,start}`. Production source is
  restored to degree 16.

The round-two campaign is complete: C5, C6 and C7 were all rejected. The
production implementation remains exactly the degree-16 linear baseline from
94adfd0. Further algorithm changes require a new profile-driven campaign.
