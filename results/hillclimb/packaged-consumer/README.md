# Packaged consumer probes

These minimal standalone consumers compile against the artifacts produced by the final clean verifies, not workspace `target/classes`.

JDK 17 core-only compile and run (no incubator module flag):

```bash
JDK17=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home
$JDK17/bin/javac -cp library/target/packed-btree-0.1.0-SNAPSHOT.jar \
  -d /tmp/packed-btree-consumer results/hillclimb/packaged-consumer/DefaultConsumer.java \
  results/hillclimb/packaged-consumer/LongPackedConsumer.java \
  results/hillclimb/packaged-consumer/StringConsumer.java
$JDK17/bin/java -cp "/tmp/packed-btree-consumer:library/target/packed-btree-0.1.0-SNAPSHOT.jar" DefaultConsumer
$JDK17/bin/java -cp "/tmp/packed-btree-consumer:library/target/packed-btree-0.1.0-SNAPSHOT.jar" LongPackedConsumer
$JDK17/bin/java -cp "/tmp/packed-btree-consumer:library/target/packed-btree-0.1.0-SNAPSHOT.jar" StringConsumer
```

`DefaultConsumer` exercises point lookups, a half-open range, deletion, cardinality and structural validation. `LongPackedConsumer` inserts signed full-width keys, including both extremes, compares lookups and ranges with `TreeMap`, removes half the mappings and validates survivors. These print `default packaged consumer passed` and `packed LongBTree packaged consumer passed`. `StringConsumer` checks nullable values, reference identity, UTF-16 order, a 512-entry covered-subtree range, a one-entry boundary range, deletion and validation; it prints `StringBTree packaged consumer passed`. `jdeps --print-module-deps library/target/packed-btree-0.1.0-SNAPSHOT.jar` reports `java.base`.

The external probes ran against the jars produced by the final clean verifies, not workspace `target/classes`. Their stdout and exit receipts are in `/tmp/packed-btree-round3-consumer-*.log` and `/tmp/packed-btree-round3-consumer-*.exit`.

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

Final packaged JAR SHA-256: library `29d6dc2b9b589459ff72a55af9cc125f47e0e629fee881dea56ed0e0c520cdc6`; optional SIMD `70625a1903e4f36b0f2c4a75d2f0ae88d5b60cf9ed2fc3070e4bb65ed8494c54`. Packaged class bytes match the corresponding final `target/classes` entries: `LongBTree` `46b2cbe8c7f9d9a561d91057493dba72af0a2080e16aba02895db950cb0b55b5`, `LongBTree$Node` `d631e8eff38b377cf6f9ffbc1035bb51df9cd5b61b27285cff78ab44d46140b2`, `StringBTree` `bdf7cd06a1a9bae4ef475b2ff38371e1a19e131cc58e24b1331fceb6a0610320`, and `StringBTree$Node` `da2cb1f658cf4e590dc70be859aa76c4ad37c1b6934a86a6585ff73bd79cd14b`. The String outer-class bytes differ from the JDK 25 benchmark class hash `b3d28205cd35c91519d1f55757e47a2754209363f52ac3ea29aa02dba65ea320`; production source SHA is unchanged, `javap -c -p` output is byte-for-byte identical, and the final packaged consumer executed against the final class. This is a compiler/classfile fingerprint difference, not a changed measured source.
