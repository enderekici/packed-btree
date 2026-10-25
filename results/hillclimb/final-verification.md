# Final verification receipt

This is a summary of the observed command outputs, not a captured Maven stdout log. It applies to source `library/src/main/java/io/github/enderekici/packedbtree/LongBTree.java` SHA-256 `9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503`.

| Runtime/build | Command | Observed result |
|---|---|---|
| Azul JDK 17.0.19 | `JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml clean verify` | Exit 0; 15 library tests and 4 benchmark tests passed, 0 failures/errors/skips. Finished 2026-10-25 16:08:37 UTC. |
| Azul JDK 25.0.3 | `JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd clean verify` | Exit 0; same 19 tests plus 2 VectorSearch tests passed, 0 failures/errors/skips. Finished 2026-10-25 16:08:54 UTC. |

Both commands ran the full `clean verify` lifecycle. Maven shade reported the ordinary overlapping `META-INF/MANIFEST.MF` warning; the builds succeeded.

Surefire reports from the final JDK 25 verify are `library/target/surefire-reports/TEST-io.github.enderekici.packedbtree.{StringBTreeTest,LongBTreeTest,ConcurrentLongBTreeTest}.xml`, `benchmarks/target/surefire-reports/TEST-bench.{MapsTest,HashMapAdaptersTest}.xml`, and `simd/target/surefire-reports/TEST-io.github.enderekici.packedbtree.simd.VectorSearchTest.xml`. They show 21 tests and zero failures, errors or skips. The final JDK 17 verify has the same reports except no SIMD report, totaling 19 tests.

The packaged-consumer probes and exact invocations are in `packaged-consumer/README.md`. The JDK 17 probe compiled and ran from the packaged core JAR without incubator modules; `jdeps --print-module-deps` returned only `java.base`. The JDK 25 probe ran `VectorSearch` with plain and compressed trees, including compressed fallback. Both printed their passed receipts.

Final packaged JAR hashes: core `495d0f1ae7ea8c90235b7d2758db7a5b862de47d54d5dbec15ee2bda13d6aae9`; optional SIMD `70625a1903e4f36b0f2c4a75d2f0ae88d5b60cf9ed2fc3070e4bb65ed8494c54`. Final JDK 25 `LongBTree`/`Node` class hashes are recorded in `c2-confirm/README.md`.
