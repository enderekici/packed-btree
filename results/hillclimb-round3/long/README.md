# Round-three long key/value node layout

## Candidate and gate

Worktree base is merged `origin/main` commit
`603b6f5b0ebcdddf5a5dab15b9a3f0f71c704340`. The candidate keeps degree 16, scalar linear
search and all APIs, but stores keys in the first 31 slots and values in the second 31 slots
of a single `long[62]` per node. It changes only `LongBTree`; `ConcurrentLongBTree` and
`StringBTree` remain unchanged. Candidate production source SHA-256:
`f2edc91f72eac1f266fff4072dd6c3572c29ba857e87e70bfe0d03ca5a58be5c`.

The frozen test control is `Round2BaselineLongBTree`, reverse-normalized from
`simd/src/test/java/bench/Round2BaselineLongBTree.java`; its bytes exactly match the
`LongBTree.java` source at the base commit (SHA-256
`9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503`). The candidate
runtime tree/Node hashes were `46b2cbe8c7f9d9a561d91057493dba72af0a2080e16aba02895db950cb0b55b5` /
`d631e8eff38b377cf6f9ffbc1035bb51df9cd5b61b27285cff78ab44d46140b2`; baseline hashes were
`8d066b5d882be85d0a3f5c22465846f0295aa35103908bda4e2408fd5a4ecfbd` /
`44e61ca9a42a39f0993587e345eb9c002ad7ba856cd34c5b0ea76fc66acd200b`. The benchmark class
loader checked code source and bytes for both classes in every fork. A 10-row positive
smoke exited 0 with 20 expected class receipts; a deliberately wrong candidate SHA failed
before measurement with exit 1 under `-foe true`.

Correctness gate: JDK 25 `-Psimd -pl simd -am -Dtest=LongBTreeTest,VectorSearchTest
-Dsurefire.failIfNoSpecifiedTests=false test` exited 0; LongBTreeTest ran 7/7 (including a
new custom-strategy full-width differential test with randomized values, updates, real
deletions, ranges, root collapse and refill), and VectorSearchTest ran 2/2 (including
compressed compact routing and custom/vector fallback). `SearchStrategy` specifies only the
active `keys[0..count)` prefix; trailing slots are documented as unspecified. `VectorSearch`
uses `count`. The combined buffer leaves the key prefix and compressed route metadata in
place; value accesses use offset `MAX` through split, merge, borrow, erase, lookup, range and
validation paths.

## Paired retained-footprint probe

JOL `GraphLayout` used the same shuffled keys and `key + 1` values for each arm. JOL could
not attach Instrumentation and reported HotspotUnsafe estimates. Candidate graph bytes and
objects fell consistently:

| Size | Distribution | Baseline bytes / objects | Packed bytes / objects | Byte change |
| ---: | --- | ---: | ---: | ---: |
| 4,096 | dense | 112,200 / 604 | 107,448 / 406 | -4.2% |
| 4,096 | fullWidth | 110,520 / 595 | 105,840 / 400 | -4.2% |
| 262,144 | dense | 6,960,808 / 37,418 | 6,665,920 / 25,131 | -4.2% |
| 262,144 | fullWidth | 6,954,728 / 37,382 | 6,660,008 / 25,102 | -4.2% |

The one-array-per-node reduction is directly verified by source and object counts. It does
not establish a cache-miss or latency mechanism. Probe log: `/tmp/packed-long-round3-jol.log`;
exit file: `/tmp/packed-long-round3-jol.exit`.

## Exploratory screen

The 40-row JDK 25 screen used the existing selected-map `Round2LinearUnrolledBenchmark`
fixture: 4,096/262,144 entries × dense/fullWidth keys × hit/miss/range64/changing overwrite/
churn × baseline/candidate. Keys, absent keys, ranges and values were identical between
arms; ranges were prechecked at low, middle and high ranks and returned exactly 64 entries.
Each one-fork trial used 1×250ms warmup, 2×250ms measurement and `-foe true`. JMH parameter
enumeration ran candidate then baseline within each distribution, with sizes 4,096 then
262,144; it did not alternate order by individual fixture. JMH's two samples do not provide
a usable interval; this is a screening estimate only. All 40 scores are finite and all 80
tree/Node fingerprint receipts matched. Raw JSON is `packed-layout-screen.json` (SHA-256
`39f1dfef0bb03b18e44af22da2e5461df331eb9271642ffb23fc986b5a114ddf`); full log:
`/tmp/packed-long-round3-screen.log`; exit file: `/tmp/packed-long-round3-screen.exit`.

The screen command, with per-fork class guards, was:

```bash
BENCH_EXPECTED_CANDIDATE_TREE_LOCATION="$PWD/library/target/classes" \
BENCH_EXPECTED_CANDIDATE_TREE_SHA256=46b2cbe8c7f9d9a561d91057493dba72af0a2080e16aba02895db950cb0b55b5 \
BENCH_EXPECTED_CANDIDATE_NODE_LOCATION="$PWD/library/target/classes" \
BENCH_EXPECTED_CANDIDATE_NODE_SHA256=d631e8eff38b377cf6f9ffbc1035bb51df9cd5b61b27285cff78ab44d46140b2 \
BENCH_EXPECTED_BASELINE_TREE_LOCATION="$PWD/simd/target/test-classes" \
BENCH_EXPECTED_BASELINE_TREE_SHA256=8d066b5d882be85d0a3f5c22465846f0295aa35103908bda4e2408fd5a4ecfbd \
BENCH_EXPECTED_BASELINE_NODE_LOCATION="$PWD/simd/target/test-classes" \
BENCH_EXPECTED_BASELINE_NODE_SHA256=44e61ca9a42a39f0993587e345eb9c002ad7ba856cd34c5b0ea76fc66acd200b \
/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin/java \
  --add-modules=jdk.incubator.vector \
  -cp "$PWD/library/target/classes:$PWD/simd/target/test-classes:$PWD/benchmarks/target/benchmarks.jar" \
  org.openjdk.jmh.Main 'bench.Round2LinearUnrolledBenchmark.*' \
  -p size=4096,262144 -p distribution=dense,fullWidth -p implementation=candidate,baseline \
  -wi 1 -w 250ms -i 2 -r 250ms -f 1 -foe true -rf json \
  -rff /tmp/packed-long-round3-screen.json
```

The targeted test command was JDK 25 `-Psimd -pl simd -am
-Dtest=LongBTreeTest,VectorSearchTest -Dsurefire.failIfNoSpecifiedTests=false test`. The
JMH runner was built with JDK 25 `-pl benchmarks -am package`. The JOL probe compiled
`/tmp/LongPackedFootprint.java` against `library/target/classes`, `simd/target/test-classes`
and `jol-core-0.17.jar`, then ran `bench.LongPackedFootprint`; its output is
`/tmp/packed-long-round3-jol.log`.

| Size | Keys | Operation | Baseline ns/op | Packed ns/op | Change |
| ---: | --- | --- | ---: | ---: | ---: |
| 4,096 | dense | hit | 8.877 | 8.834 | -0.5% |
| 4,096 | dense | miss | 8.847 | 8.829 | -0.2% |
| 4,096 | dense | range64 | 49.592 | 48.271 | -2.7% |
| 4,096 | dense | overwrite | 10.533 | 10.323 | -2.0% |
| 4,096 | dense | churn | 87.521 | 88.621 | +1.3% |
| 4,096 | fullWidth | hit | 8.950 | 8.974 | +0.3% |
| 4,096 | fullWidth | miss | 8.895 | 8.949 | +0.6% |
| 4,096 | fullWidth | range64 | 49.164 | 50.441 | +2.6% |
| 4,096 | fullWidth | overwrite | 10.591 | 10.516 | -0.7% |
| 4,096 | fullWidth | churn | 87.286 | 86.395 | -1.0% |
| 262,144 | dense | hit | 69.380 | 69.486 | +0.2% |
| 262,144 | dense | miss | 69.107 | 70.076 | +1.4% |
| 262,144 | dense | range64 | 95.078 | 94.421 | -0.7% |
| 262,144 | dense | overwrite | 71.252 | 70.126 | -1.6% |
| 262,144 | dense | churn | 148.616 | 146.246 | -1.6% |
| 262,144 | fullWidth | hit | 69.988 | 69.356 | -0.9% |
| 262,144 | fullWidth | miss | 68.487 | 68.425 | -0.1% |
| 262,144 | fullWidth | range64 | 104.336 | 94.377 | -9.5% |
| 262,144 | fullWidth | overwrite | 70.949 | 69.694 | -1.8% |
| 262,144 | fullWidth | churn | 146.090 | 146.474 | +0.3% |

The screen was near-neutral overall; the lower fullWidth range point remained exploratory.

## Paired confirmation and JDK 17 subset

The paired JDK 25 confirmation ran 2026-10-25 19:15:31–19:26:30 UTC on Azul OpenJDK 25.0.3, 1 GiB heap, G1, and one benchmark thread. Each arm was a separate selected-map JMH invocation with `-wi 3 -w 1s -i 5 -r 1s -f 2 -foe true`; order alternated by fixture block: candidate/baseline for 4,096 dense, baseline/candidate for 4,096 fullWidth, candidate/baseline for 262,144 dense, baseline/candidate for 262,144 fullWidth. The classpath put `library/target/classes` before `simd/target/test-classes` and the shaded benchmark jar. Each invocation validated exactly five finite JSON rows; per-arm logs record ten tree and ten Node fingerprints (two forks × five benchmarks), all matching the expected code source and SHA-256.

The per-arm invocation used `/tmp/run-packed-confirm.sh SIZE DISTRIBUTION ARM`; exact arguments were the guarded screen command above with the requested size, distribution and single implementation, and `-wi 3 -w 1s -i 5 -r 1s -f 2 -foe true`. Candidate outer/Node class SHA-256: `46b2cbe8c7f9d9a561d91057493dba72af0a2080e16aba02895db950cb0b55b5` / `d631e8eff38b377cf6f9ffbc1035bb51df9cd5b61b27285cff78ab44d46140b2`; baseline outer/Node SHA-256: `8d066b5d882be85d0a3f5c22465846f0295aa35103908bda4e2408fd5a4ecfbd` / `44e61ca9a42a39f0993587e345eb9c002ad7ba856cd34c5b0ea76fc66acd200b`.

Each table value is mean ns/op ± JMH's 99.9% confidence-interval half-width. Delta is candidate vs baseline point estimate. All 20 point deltas were between -6.49% and +3.02%; no workload showed a >5% point regression. The intervals overlap in every cell, so these results do not establish latency equivalence or a latency improvement. The 262,144 dense miss and overwrite point increases were +3.02% and +2.40%, respectively, with overlapping intervals.

| Size | Keys | Operation | Baseline ns/op | Packed ns/op | Delta |
| ---: | --- | --- | ---: | ---: | ---: |
| 4,096 | dense | hit | 9.482 ± 0.048 | 9.174 ± 0.059 | -3.25% |
| 4,096 | dense | miss | 9.489 ± 0.127 | 9.231 ± 0.165 | -2.72% |
| 4,096 | dense | range64 | 50.310 ± 1.003 | 49.877 ± 1.760 | -0.86% |
| 4,096 | dense | overwrite | 11.019 ± 0.180 | 10.793 ± 0.221 | -2.05% |
| 4,096 | dense | churn | 97.512 ± 2.147 | 91.184 ± 3.271 | -6.49% |
| 4,096 | fullWidth | hit | 9.513 ± 0.110 | 9.503 ± 0.570 | -0.11% |
| 4,096 | fullWidth | miss | 9.499 ± 0.147 | 9.383 ± 0.142 | -1.22% |
| 4,096 | fullWidth | range64 | 50.065 ± 1.995 | 48.450 ± 0.808 | -3.22% |
| 4,096 | fullWidth | overwrite | 11.191 ± 0.182 | 10.885 ± 0.173 | -2.73% |
| 4,096 | fullWidth | churn | 95.094 ± 1.155 | 92.670 ± 0.439 | -2.55% |
| 262,144 | dense | hit | 73.946 ± 0.577 | 74.641 ± 1.383 | +0.94% |
| 262,144 | dense | miss | 73.848 ± 0.772 | 76.081 ± 2.240 | +3.02% |
| 262,144 | dense | range64 | 104.825 ± 3.430 | 101.050 ± 7.037 | -3.60% |
| 262,144 | dense | overwrite | 76.539 ± 1.060 | 78.379 ± 3.621 | +2.40% |
| 262,144 | dense | churn | 159.376 ± 3.111 | 158.348 ± 3.279 | -0.64% |
| 262,144 | fullWidth | hit | 74.327 ± 1.113 | 73.336 ± 1.710 | -1.33% |
| 262,144 | fullWidth | miss | 73.455 ± 1.090 | 74.844 ± 0.765 | +1.89% |
| 262,144 | fullWidth | range64 | 104.841 ± 3.751 | 104.156 ± 5.586 | -0.65% |
| 262,144 | fullWidth | overwrite | 76.428 ± 0.798 | 75.380 ± 0.725 | -1.37% |
| 262,144 | fullWidth | churn | 156.849 ± 1.302 | 155.699 ± 1.717 | -0.73% |

The JDK 17 decisive subset ran with Azul OpenJDK 17.0.19 on 262,144 fullWidth keys for hit, churn and range64, with 2 forks, 3×1s warmup and 3×1s measurement. Since the JDK 25 shaded jar contains JDK 25-generated harness bytecode, the three plain fixture sources (`SearchScreenFixture`, `Round2BaselineLongBTree`, `Round2LinearUnrolledBenchmark`) were freshly compiled with JDK 17 `javac --release 17` and JMH 1.37 annotation processing into `/tmp/packed-long-round3-jdk17-classes-20261025`; that output directory was first on the runtime classpath. Per-arm invocation used `/tmp/run-packed-long-jdk17-fixed.sh METHOD ARM`, `-foe true`, two forks, and one finite JSON row. Each arm log has two runtime tree and two Node fingerprint receipts, all matching expected sources and hashes. JDK 17 candidate outer/Node hashes match the JDK 25 candidate classes; frozen baseline outer/Node hashes from the JDK 17 compilation are `be03777166e55ca28db64ce4f03f8034959688e2ff6226776b54f79239dbc32b` / `6e3896ad187aa10cdd5a319276abcf09f7726f0b75c766307bef79f6fd44c319`.

| Operation | Baseline ns/op | Packed ns/op | Delta |
| --- | ---: | ---: | ---: |
| hit | 71.208 ± 2.667 | 71.190 ± 1.359 | -0.03% |
| churn | 158.778 ± 1.907 | 157.356 ± 1.691 | -0.90% |
| range64 | 100.007 ± 9.589 | 100.646 ± 8.434 | +0.64% |

All three JDK 17 point estimates were within -0.90% to +0.64% and their intervals overlap. A targeted JDK 17 Maven test, `JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml -pl library -Dtest=LongBTreeTest test`, exited 0 (7 tests, no failures/errors/skips).

**Decision: retain the packed layout for its measured 4.2% lower retained footprint.** This confirmation found no >5% latency regression in the finite tested grid, but overlapping intervals do not prove equivalence; no speedup or cache-locality claim is made. The JDK 17 Maven test and JMH measurements are targeted, not a full verification gate.

Raw confirmation JSONs are this directory's `confirmation-jdk25-*.json` and `confirmation-{hit,churn,range64}-{candidate,baseline}.json`. Full logs remain in `/tmp/packed-long-round3-confirm-*.log` and `/tmp/packed-long-round3-jdk17-*.log`; all successful invocation exit files are adjacent in `/tmp`. The first JDK 17 attempt against the existing shaded JDK 25 jar failed before measurements with `UnsupportedClassVersionError` (class version 69 vs JDK 17 maximum 61); it is invalid and excluded. The attempted `-pl simd -am` JDK 17 Maven setup also failed because `simd` is outside the default Maven reactor; no result from either failed setup is included.

The source/test candidate patch is preserved as `packed-layout-candidate.patch` (SHA-256 `3c9f2ec00a6aff855da8f014d92cde470e29b2549b19efbf987436fb00a03662`).
