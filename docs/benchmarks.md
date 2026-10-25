# Benchmark guide

Use the [results index](../results/README.md) to navigate measured outcomes and
raw evidence. The repository contains independent campaigns, not one combined
ranking. Match implementation revision, key/value shape, operation, fixture
size, JDK, and thread count before comparing numbers.

## Reproduction

Build and test the benchmark modules on JDK 17:

```bash
./mvnw clean verify
```

The optional SIMD module requires JDK 25:

```bash
./mvnw -Psimd clean verify
```

List JMH benchmarks and run one chosen benchmark from the shaded JAR:

```bash
java -jar benchmarks/target/benchmarks.jar -l
java -jar benchmarks/target/benchmarks.jar 'bench.MapBenchmark.hit' \
  -p implementation=btree -p size=1048576 -t 1 \
  -wi 2 -w 1s -i 3 -r 1s -f 2 -foe true \
  -rf json -rff results/my-hit-run.json
```

Use fresh output paths for each run. For comparisons, preserve equivalent
fixtures and query streams, select only one map implementation in each trial,
validate setup and teardown, retain raw JMH JSON, and record the exact source
revision and JDK. The guarded comparison harnesses under `simd/src/test` show
how this repository checks loaded class locations and fingerprints; older
historical campaigns may have narrower controls, which their reports disclose.

Do not combine measurements across campaigns to infer an implementation ratio.
The mutable-versus-concurrent report compares whole current implementations at
one thread; concurrent-only scaling runs answer a separate question. The
original-prototype three-way comparison uses another source snapshot and a
one-million-entry fixture. Hash maps have no ordered range operation. Each
report states its own limits and points to raw files.
