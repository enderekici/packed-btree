# Packaged consumer probes

These minimal standalone consumers compile against the artifacts produced by the final clean verifies, not workspace `target/classes`.

JDK 17 core-only compile and run (no incubator module flag):

```bash
JDK17=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home
$JDK17/bin/javac -cp library/target/packed-btree-0.1.0-SNAPSHOT.jar \
  -d /tmp/packed-btree-consumer results/hillclimb/packaged-consumer/DefaultConsumer.java
$JDK17/bin/java -cp "/tmp/packed-btree-consumer:library/target/packed-btree-0.1.0-SNAPSHOT.jar" DefaultConsumer
```

The probe exercises point lookups, a half-open range, deletion, cardinality and structural validation. It prints `default packaged consumer passed`. `jdeps --print-module-deps library/target/packed-btree-0.1.0-SNAPSHOT.jar` reports `java.base`.

The JDK 17 `StringConsumer.java` probe checks nullable values, fallback behavior, reference identity, UTF-16 ordering for a supplementary character versus a BMP private-use character, half-open range traversal, deletion, size and validation. Compile and run it beside `DefaultConsumer.java` using the same commands above; it prints `StringBTree packaged consumer passed`.

JDK 25 optional SIMD/compressed compile and run:

```bash
JDK25=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home
CP=library/target/packed-btree-0.1.0-SNAPSHOT.jar:simd/target/packed-btree-simd-0.1.0-SNAPSHOT.jar
$JDK25/bin/javac --add-modules jdk.incubator.vector -cp "$CP" \
  -d /tmp/packed-btree-consumer results/hillclimb/packaged-consumer/VectorCompressedConsumer.java
$JDK25/bin/java --add-modules jdk.incubator.vector \
  -cp "/tmp/packed-btree-consumer:$CP" VectorCompressedConsumer
```

This exercises `VectorSearch` as the fallback for both `LongBTree` and `CompressedLongBTree`, including compact routing and a noncompact-key fallback. It prints `vector and compressed fallback consumer passed`.

Final packaged JAR SHA-256: library `07fa61d13555bffbe90f4837f82a1ff71713904fb75c68e43eee6b3216949eba`; optional SIMD `70625a1903e4f36b0f2c4a75d2f0ae88d5b60cf9ed2fc3070e4bb65ed8494c54`. The packaged `LongBTree.class` and `StringBTree.class` hashes match the classes from the final clean verify output.
