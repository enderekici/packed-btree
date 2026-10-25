# Final integrated verification

Both full builds ran after all production changes were present. This is a standalone Maven library; the public-library release-17 / optional-release-25 profile setup is not expressible through the EVAM application build wrapper, so these used the documented direct Maven exception.

| Runtime | Command | Result |
| --- | --- | --- |
| Azul JDK 17.0.19 | `JAVA_HOME='/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home' PATH='/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home/bin':$PATH ./mvnw --settings /tmp/packed-btree-maven-settings.xml clean verify` | Exit 0. Library: 15 tests; benchmarks: 4 tests. |
| Azul JDK 25 | `JAVA_HOME='/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home' PATH='/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin':$PATH ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd clean verify` | Exit 0. Library: 15 tests; benchmarks: 4 tests; SIMD module: 3 tests. |

Complete stdout logs are `/tmp/packed-btree-final-jdk17.log` and `/tmp/packed-btree-final-jdk25-simd.log`; corresponding exit files contain `0`.

Packaged consumers compiled and ran against the jars produced by those builds. On JDK 17, `DefaultConsumer` passed with no incubator module and `StringConsumer` passed null-value/fallback, value identity, UTF-16 supplementary/BMP ordering, range, deletion, cardinality and validation checks. `jdeps --print-module-deps` on the core jar returned `java.base`. On JDK 25, `VectorCompressedConsumer` passed vector lookup through both ordinary and compressed trees, including fallback routing. Consumer sources and commands are in `results/hillclimb/packaged-consumer/README.md`; raw probe results were returned directly by `javac`/`java` (all probes exited 0).

The packaged core jar SHA-256 is `07fa61d13555bffbe90f4837f82a1ff71713904fb75c68e43eee6b3216949eba`; optional SIMD jar SHA-256 is `70625a1903e4f36b0f2c4a75d2f0ae88d5b60cf9ed2fc3070e4bb65ed8494c54`. The packaged `LongBTree.class` SHA-256 `deee482d00087b4f62b71934407cb27369ba69b6e8191bf6693742d56c51a289` and `StringBTree.class` SHA-256 `a2bbd446d6ea76d2ef3d3c88a7ea7407a2d6f5ca7d51d8258eabf2d4768dbfe8` each matched the corresponding class in `library/target/classes`.
