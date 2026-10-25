# C2 linear search confirmation

The measured candidate source SHA-256 was `86a7e108eeccbe515a8e79249c2e82607720ea7c2d40d83a3b07b381d8dbf10e`; the final source adds only a comment documenting the 31-key node bound (final source SHA-256 `9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503`). The measured candidate runtime class hashes were LongBTree `735203836860d90afcfebeb8dbec729a8fbfe316b83cbe2a42444ca08aeede76`, Node `6f183f62dab68d6454553cd591bcc6384db0c9569f8805b9b16135f88ab10b5b`; frozen baseline clone tree `857602ebb4df3eb7247c8d93a4d4772f614f771ac1f44e5b49e5287ccaf4ffd5`, Node `4d06e5a71c69a5a099361c2828891d0ce119213c957cfaebb21a72c7831d935f`. Per-fork guards checked code source and bytes; `-foe true` was enabled. JDK 25.0.3, 2 forks, 3×1s warmup, 5×1s measurement. Four size×distribution blocks alternated order: candidate/baseline at 4096 dense, baseline/candidate at 4096 fullWidth, candidate/baseline at 262144 dense, baseline/candidate at 262144 fullWidth. Each of eight JSON files has five finite rows; each arm log has 20 expected tree/Node receipts. Raw result/log filenames are `candidate|baseline-<size>-<distribution>.json` and `/tmp/hillclimb-c2-confirm-<arm>-<size>-<distribution>.log`.

JDK 25 was Azul OpenJDK 25.0.3 on an Apple M4 Pro running macOS 27.0.1, one JMH thread, 1 GiB heap and G1. The baseline was the integrated default at commit `57ea447d94078b2a0872569b09bb88986b71f886`, not the historical pre-hook control. The candidate's search implementation was linear lower-bound search; the vector module was present in the runtime but this default scalar path did not call `VectorSearch`.

Final full verification rebuilt the comment-updated source and passed on JDK 17 and JDK 25. Final JDK 25 runtime class hashes are LongBTree `deee482d00087b4f62b71934407cb27369ba69b6e8191bf6693742d56c51a289`, Node `b98364082623221d2f89ce381b176ab473663d4dbf860b64310dc8161cfee26f`; the difference from measured bytes is line-table metadata from the added comment. `JAVA_HOME=.../azul-17.0.19/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml clean verify` passed with 15 library and 4 benchmark tests. `JAVA_HOME=.../azul-25/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd clean verify` passed those 19 tests plus 2 VectorSearch tests.

| Operation | Size | Keys | Baseline ns/op | Candidate ns/op | Change |
|---|---:|---|---:|---:|---:|
| hit | 4096 | dense | 20.589 ± 5.856 | 8.727 ± 0.092 | -57.6% |
| miss | 4096 | dense | 19.136 ± 3.017 | 8.988 ± 0.163 | -53.0% |
| range64 | 4096 | dense | 61.422 ± 2.781 | 47.537 ± 0.946 | -22.6% |
| overwrite | 4096 | dense | 19.506 ± 3.596 | 10.121 ± 0.099 | -48.1% |
| churn | 4096 | dense | 113.787 ± 2.715 | 83.013 ± 3.210 | -27.0% |
| hit | 4096 | fullWidth | 18.744 ± 4.942 | 9.053 ± 0.085 | -51.7% |
| miss | 4096 | fullWidth | 20.154 ± 3.789 | 9.122 ± 0.069 | -54.7% |
| range64 | 4096 | fullWidth | 63.381 ± 1.943 | 47.733 ± 1.284 | -24.7% |
| overwrite | 4096 | fullWidth | 21.022 ± 3.899 | 10.298 ± 0.110 | -51.0% |
| churn | 4096 | fullWidth | 116.226 ± 0.930 | 87.009 ± 0.698 | -25.1% |
| hit | 262144 | dense | 98.152 ± 0.858 | 66.832 ± 0.903 | -31.9% |
| miss | 262144 | dense | 98.261 ± 0.596 | 68.756 ± 0.533 | -30.0% |
| range64 | 262144 | dense | 125.570 ± 5.645 | 96.574 ± 7.565 | -23.1% |
| overwrite | 262144 | dense | 103.623 ± 1.032 | 71.228 ± 0.529 | -31.3% |
| churn | 262144 | dense | 200.922 ± 2.109 | 152.916 ± 9.208 | -23.9% |
| hit | 262144 | fullWidth | 99.505 ± 2.440 | 68.492 ± 0.542 | -31.2% |
| miss | 262144 | fullWidth | 99.217 ± 1.323 | 69.451 ± 0.819 | -30.0% |
| range64 | 262144 | fullWidth | 124.881 ± 6.287 | 94.260 ± 5.481 | -24.5% |
| overwrite | 262144 | fullWidth | 105.096 ± 1.739 | 72.542 ± 0.551 | -31.0% |
| churn | 262144 | fullWidth | 202.898 ± 3.249 | 148.238 ± 0.947 | -26.9% |

All 20 99.9% JMH confidence intervals were non-overlapping and favored the candidate (independent results are summarized by JMH; this is not a direct paired confidence interval). Results consistently improve point hits/misses, range64, overwrite and churn on both sizes and key distributions.

## JDK 17 confirmation

The selected decisive subset was rerun with Azul JDK 17.0.19: size 262144, hit/range64/churn, dense and fullWidth, 2 forks, 3×1s warmup and 3×1s measurement. Only the four plain fixture sources were recompiled with JDK 17 into `/tmp/hillclimb-jdk17-classes` using JMH 1.37 annotation processing; this avoids loading JDK 25 test bytecode. Production library classes are Java 17 bytecode. Each JSON has three finite rows and each arm log has 12 tree/Node receipts.

| Operation | Keys | Baseline ns/op | Candidate ns/op | Change |
|---|---|---:|---:|---:|
| hit | dense | 100.075 ± 3.277 | 68.565 ± 0.949 | -31.5% |
| range64 | dense | 142.124 ± 28.724 | 91.583 ± 11.407 | -35.6% |
| churn | dense | 208.342 ± 6.627 | 152.717 ± 1.963 | -26.7% |
| hit | fullWidth | 98.719 ± 1.783 | 68.355 ± 1.440 | -30.8% |
| range64 | fullWidth | 139.123 ± 11.456 | 91.121 ± 13.739 | -34.5% |
| churn | fullWidth | 212.474 ± 5.047 | 155.378 ± 4.307 | -26.9% |

JDK 17 runtime fingerprints: candidate tree/Node remain the measured hashes above; test-only baseline tree `b934b26238af26f7af10bf238051fa850f8a46375c8ac66a14432f7f8e0314dd`, Node `23bc805a7aa974a7b8389fe3f16ed6aac4824a6e10103e3ca88f7b56d0eacd03` (compiled with JDK 17). All six JDK 17 99.9% intervals were non-overlapping and favored the candidate.

JDK 17 harness compilation used these four plain test sources and JMH 1.37's annotation processor:

```bash
JDK17=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home
JMH=/Users/ender/.m2/repository/org/openjdk/jmh
$JDK17/bin/javac -cp "$PWD/library/target/classes:$JMH/jmh-core/1.37/jmh-core-1.37.jar" \
  -processorpath "$JMH/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar:$JMH/jmh-core/1.37/jmh-core-1.37.jar" \
  -d /tmp/hillclimb-jdk17-classes \
  simd/src/test/java/bench/SearchScreenFixture.java \
  simd/src/test/java/bench/CurrentBaselineLongBTree.java \
  simd/src/test/java/bench/BaselineSearchScreen.java \
  simd/src/test/java/bench/CandidateSearchScreen.java
```

Each JDK 17 JMH arm used the same command shape below, with `ARM` set to `Candidate` or `Baseline`, `DIST` to `dense` or `fullWidth`, and `ARMNAME` to `candidate` or `baseline`; output names correspond to the checked-in JSON files. These exact runtime hashes were supplied in every fork:

```bash
BENCH_EXPECTED_CANDIDATE_TREE_LOCATION="$PWD/library/target/classes" \
BENCH_EXPECTED_CANDIDATE_TREE_SHA256=735203836860d90afcfebeb8dbec729a8fbfe316b83cbe2a42444ca08aeede76 \
BENCH_EXPECTED_CANDIDATE_NODE_LOCATION="$PWD/library/target/classes" \
BENCH_EXPECTED_CANDIDATE_NODE_SHA256=6f183f62dab68d6454553cd591bcc6384db0c9569f8805b9b16135f88ab10b5b \
BENCH_EXPECTED_BASELINE_TREE_LOCATION=/tmp/hillclimb-jdk17-classes \
BENCH_EXPECTED_BASELINE_TREE_SHA256=b934b26238af26f7af10bf238051fa850f8a46375c8ac66a14432f7f8e0314dd \
BENCH_EXPECTED_BASELINE_NODE_LOCATION=/tmp/hillclimb-jdk17-classes \
BENCH_EXPECTED_BASELINE_NODE_SHA256=23bc805a7aa974a7b8389fe3f16ed6aac4824a6e10103e3ca88f7b56d0eacd03 \
$JDK17/bin/java --add-modules=jdk.incubator.vector \
  -cp "/tmp/hillclimb-jdk17-classes:$PWD/library/target/classes:$PWD/benchmarks/target/benchmarks.jar" \
  org.openjdk.jmh.Main "bench.${ARM}SearchScreen.(hit|range64|churn)" \
  -p size=262144 -p distribution="$DIST" -wi 3 -w 1s -i 3 -r 1s -f 2 -foe true \
  -rf json -rff "results/hillclimb/c2-confirm/jdk17-${ARMNAME}-262144-${DIST}.json"
```

The JDK 25 confirmation used the same expected hashes listed at the top of this report and baseline classes from `simd/target/test-classes`. With `ARM`, `ARMNAME`, `SIZE`, and `DIST` set per arm/block as above, the exact invocation was:

```bash
BENCH_EXPECTED_CANDIDATE_TREE_LOCATION="$PWD/library/target/classes" \
BENCH_EXPECTED_CANDIDATE_TREE_SHA256=735203836860d90afcfebeb8dbec729a8fbfe316b83cbe2a42444ca08aeede76 \
BENCH_EXPECTED_CANDIDATE_NODE_LOCATION="$PWD/library/target/classes" \
BENCH_EXPECTED_CANDIDATE_NODE_SHA256=6f183f62dab68d6454553cd591bcc6384db0c9569f8805b9b16135f88ab10b5b \
BENCH_EXPECTED_BASELINE_TREE_LOCATION="$PWD/simd/target/test-classes" \
BENCH_EXPECTED_BASELINE_TREE_SHA256=857602ebb4df3eb7247c8d93a4d4772f614f771ac1f44e5b49e5287ccaf4ffd5 \
BENCH_EXPECTED_BASELINE_NODE_LOCATION="$PWD/simd/target/test-classes" \
BENCH_EXPECTED_BASELINE_NODE_SHA256=4d06e5a71c69a5a099361c2828891d0ce119213c957cfaebb21a72c7831d935f \
/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin/java \
  --add-modules=jdk.incubator.vector \
  -cp "$PWD/library/target/classes:$PWD/simd/target/test-classes:$PWD/benchmarks/target/benchmarks.jar" \
  org.openjdk.jmh.Main "bench.${ARM}SearchScreen.(hit|miss|range64|overwrite|churn)" \
  -p size="$SIZE" -p distribution="$DIST" -wi 3 -w 1s -i 5 -r 1s -f 2 -foe true \
  -rf json -rff "results/hillclimb/c2-confirm/${ARMNAME}-${SIZE}-${DIST}.json"
```

## Final build and packaged consumers

`JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml clean verify` exited 0 on 2026-10-25; 15 library tests and 4 benchmark-adapter tests passed. `JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd clean verify` exited 0; those 19 tests plus both VectorSearch tests passed. Both used `clean verify` (the full lifecycle, not only unit tests).

An external Java 17 consumer compiled against `library/target/packed-btree-0.1.0-SNAPSHOT.jar` and ran without `--add-modules`; it exercised point lookups, half-open range, deletion and validation. `jdeps --print-module-deps` reports only `java.base`. A Java 25 consumer compiled and ran with both packaged jars and `--add-modules jdk.incubator.vector`; it exercised VectorSearch with `LongBTree` and `CompressedLongBTree`, including compact routing and noncompact fallback. Both printed a passed receipt. Final jar SHA-256: library `495d0f1ae7ea8c90235b7d2758db7a5b862de47d54d5dbec15ee2bda13d6aae9`, optional SIMD `70625a1903e4f36b0f2c4a75d2f0ae88d5b60cf9ed2fc3070e4bb65ed8494c54`.

## Retained memory

JOL `GraphLayout` with the same 262144 keys and fixture for both maps reports no footprint change. Dense: both 6,960,808 bytes, 37,418 objects, 12,287 nodes. FullWidth: both 6,954,728 bytes, 37,382 objects, 12,280 nodes. These are HotspotUnsafe estimates; JOL could not attach Instrumentation.

## Decision

Accept linear lower-bound scan at degree 16. The guarded confirmation and JDK 17 subset show broad, consistent wins without a retained-memory increase. C1 dispatch and C3/C4 node-degree alternatives remain rejected; see the campaign ledger and individual reports.
