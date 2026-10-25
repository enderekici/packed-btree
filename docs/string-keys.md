# String-key B-tree

`StringBTree<V>` is a mutable, non-thread-safe B-tree for String keys and generic values. Keys use Java's natural `String.compareTo` ordering (lexicographic UTF-16 code units). Empty strings are valid. Null keys are rejected; null values are allowed. Values are stored by reference.

```java
StringBTree<String> names = new StringBTree<>();
names.put("alice", "Alice A.");
String name = names.getOrDefault("alice", "unknown");
names.forEach("a", "m", (key, value) -> System.out.println(key + ": " + value));
names.remove("alice");
```

`forEach(fromInclusive, toExclusive, visitor)` visits the half-open range in ascending order. Equal or reversed bounds produce no visits. Do not mutate the tree from the visitor. `validate()` performs a full diagnostic traversal and is intended for tests and diagnostics rather than hot paths.

## Measurement scope

A short JDK 25 single-threaded JMH run covered 113 configurations (90 point, 18 ordered-range, 5 large-hit), with 226 forks and 678 measured iterations. At 262,144 entries, the random String-to-String hit probe measured 344.2 ± 9.1 ns/op for `StringBTree`, 404.9 ± 27.2 for `TreeMap`, and 45.4 ± 2.6 for `HashMap`. This result applies only to that hit fixture; it does not establish broad string-map or concurrent performance. The full matrix, confidence intervals, setup details, and raw JSON links are in [the benchmark report](../results/string-benchmarks/REPORT.md).
