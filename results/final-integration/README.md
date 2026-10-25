# Final PR2 + PR3 verification

This receipt is for the combined current tree: packed mutable `LongBTree`,
covered-subtree `StringBTree` ranges, and the concurrent Long/String APIs. The
core targets Java 17; the optional SIMD profile targets Java 25. Commands use
the project's Maven wrapper and explicit settings file.

## Full verification

| Runtime | Command | Result |
| --- | --- | --- |
| Azul JDK 17.0.19 | `JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home/bin:$PATH ./mvnw --settings /tmp/packed-btree-maven-settings.xml clean verify` | Exit 0; library 25 tests, benchmarks 4 tests; no failures, errors, or skips. |
| Azul JDK 25.0.3 | `JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin:$PATH ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd clean verify` | Exit 0; library 25 tests, benchmarks 4 tests, SIMD 3 tests; no failures, errors, or skips. |

Complete stdout logs: [`verify-jdk17.log`](verify-jdk17.log) and
[`verify-jdk25-simd.log`](verify-jdk25-simd.log). The installed project target
is Java 17 for the core and Java 25 for SIMD.

## Packaged consumers

`DocumentationConsumer.java` compiles with `javac --release 17` against the
built core JAR and runs on both Azul JDK 17.0.19 and JDK 25.0.3. It exercises the
README's mutable Long/String examples and both concurrent variants, including
callbacks that mutate the live concurrent map while the traversal retains its
starting root. Both executions print `documentation consumer passed`.

The JDK 25 packaged `VectorCompressedConsumer` also passed with
`--add-modules jdk.incubator.vector`, checking the optional VectorSearch
fallback for regular and compressed trees. `jdeps --print-module-deps` reports
`java.base` for the core JAR.

Commands, stdout, and outputs:

- [`DocumentationConsumer.java`](DocumentationConsumer.java)
- JDK 17 compile/run: `consumer-compile-jdk17.log`, `consumer-run-jdk17.log`
- JDK 25 compile/run: `consumer-compile-jdk25.log`, `consumer-run-jdk25.log`
- Optional vector/compressed probe: `vector-compile-jdk25.log`, `vector-run-jdk25.log`
- Core dependency check: `jdeps.log`

The final JDK 25-built artifact hashes are core
`7e0434a59230c94a4e662018fb35c6b14d0fc1f9cdd062ccc0a701d073c70254` and SIMD
`70625a1903e4f36b0f2c4a75d2f0ae88d5b60cf9ed2fc3070e4bb65ed8494c54`.

## Benchmark-command smoke check

The JMH list command exited 0. A one-fork 100ms `MapBenchmark.hit` smoke on
JDK 17 used `implementation=btree`, size 16,384, `-wi 0 -i 1 -r 100ms`, and
`-foe true`; setup and teardown assertions passed. This validates the documented
runner command and fixture, and is not performance evidence. See
[`jmh-list.log`](jmh-list.log) and [`jmh-fixture-smoke.log`](jmh-fixture-smoke.log).
