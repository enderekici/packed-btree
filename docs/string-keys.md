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

The current search path ends a node lookup when it finds an equal key and computes the exclusive upper-bound position once per visited node during range traversal. A guarded confirmation against the frozen implementation measured 16 String-to-String workloads; all point estimates were lower and 15 of 16 99.9% intervals were separated. The only overlapping result was the large common-prefix miss. A JDK 17 subset found a separated improvement for random-key hits, while its churn and range intervals overlapped. These are single-host, prebuilt-string results and do not establish performance for `StringBTree<Long>` or other workloads. See the [confirmation tables and controls](../results/hillclimb-round2/strings/candidate3-confirmation.md) and the [candidate ledger](../HILLCLIMB-STRINGS.md).

The current implementation also walks a fully covered subtree in order without
repeating bound searches in each descendant. In the guarded round-three range64
confirmation, means were 11.3–15.7% lower across three JDK 25 fixtures and
14.9–20.6% lower in the JDK 17 subset. The width-one prefix range was 0.8%
slower with overlapping intervals; small-width results remain inconclusive. See
the [covered-range report](../results/hillclimb-round3/strings/README.md).

## Measurement scope

A single-threaded JDK 25 baseline JMH run covered 113 configurations (90 point, 18 ordered-range, 5 large-hit), with 226 forks and 678 measured iterations. A later guarded hill-climb confirmed the StringBTree search/range changes on a narrower String-to-String fixture set. At 262,144 entries, its random hit was 288.3 ± 3.1 ns/op versus 323.3 ± 5.6 for the frozen baseline and 305.0 ± 6.4 for TreeMap; its stationary range64 repeat was 796.4 ± 16.8 versus 950.6 ± 17.1 baseline and 989.4 ± 35.6 TreeMap. These results apply to those fixtures only; they do not establish String-to-Long or concurrent performance. The broader baseline matrix, setup details, and raw JSON links are in [the benchmark report](../results/string-benchmarks/REPORT.md), and C3 evidence is in the [confirmation report](../results/hillclimb-round2/strings/candidate3-confirmation.md).
