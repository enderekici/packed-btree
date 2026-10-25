# Matched one-million-entry comparison

This is a fresh, matched comparison of the original prototype, the optimized
packed B-tree at the feature-branch source, and `java.util.TreeMap`. The run
started at 2026-10-25 16:24:42 UTC on an Apple M4 Pro, macOS 27.0.1, Azul
OpenJDK 25.0.3. It used one JMH thread, two forks, three one-second warmups and
five one-second measurements, with `-foe true`; exit code 0. All 15
implementation/workload rows are finite, with ten measurement samples (five
per fork) in each row. The initial full-matrix raw JSON is
[`initial-full-jdk25-m4pro.json`](initial-full-jdk25-m4pro.json); its command and
fork properties are in [`command.txt`](command.txt). The complete console log is
`/tmp/threeway-1m.log`. Initial JSON SHA-256:
`492580fc1b68fafc0f031f95ed636e0744ac92847db621eac5478df89cace631`.

Each trial builds one selected map with 1,048,576 entries. Keys are shuffled,
dense even signed longs from 1,024 through 2,098,174, so all keys and values are
outside the Long cache. All implementations receive the same insertion order,
queries, return-value consumption, range bounds and changing-overwrite/churn
semantics. Point hit/miss queries cycle through all 1,048,576 keys in the
shuffled fixture. Churn removes and reinserts a corresponding odd key at
constant cardinality. Range queries cycle through 4,096 fixed-seed windows of
exactly 64 entries; setup verifies the first, middle and highest-rank windows. Trial teardown checks the full map contents,
cardinality and tree invariants. No timed string construction or extra reader or
writer threads are involved.

The original implementation is an identifier-renamed copy of
`src/main/java/bench/LongBTree.java` at original commit
`dcb51764891551745b1efd9825a9b55e031a5248`. Reverse-normalizing the class name
produces byte-for-byte identical source (SHA-256
`f8dcc302c0b4d1eb04721ec3dbb34e0bbc7e0e815b32deaf7b475f30a78f3c41`); the
renamed test copy hash is `52279f178d23189270fe56ea05366e6149392658903fb7fa616472dca2ca536e`. The
optimized implementation is the default `LongBTree` at feature commit
`451f3b32901c6ac4ad6ebfaa96f12de55f5e5a61` (source file SHA-256
`9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503`). The
three-way harness source SHA-256 is
`ca0be37b665f758e9d668611d27d3e5311bf43c0cb672647b6309ffdbd82919e`. Every fork
hashes the loaded tree and node classes and checks their code-source directories; the original loads
from `simd/target/test-classes`, and the optimized class loads from
`library/target/classes`, placed first in the classpath. `TreeMap` and its entry
class are checked against the JDK 25 bootstrap `java.base` class bytes.

| Operation | Original ns/op | Optimized ns/op | TreeMap ns/op | Optimized / original | TreeMap / original |
| --- | ---: | ---: | ---: | ---: | ---: |
| Hit | 133.205 ± 4.203 | 102.202 ± 2.079 | 314.419 ± 9.413 | 0.767× | 2.360× |
| Miss | 124.875 ± 2.959 | 92.795 ± 1.753 | 473.725 ± 20.364 | 0.743× | 3.794× |
| Changing overwrite | 134.513 ± 2.451 | 100.570 ± 2.545 | 318.300 ± 12.025 | 0.748× | 2.366× |
| Churn | 269.535 ± 5.374 | 202.745 ± 3.607 | 981.186 ± 82.723 | 0.752× | 3.640× |
| Range of 64 | 176.491 ± 5.858 | 118.179 ± 6.130 | 718.979 ± 19.621 | 0.669× | 4.074× |

Error values are JMH 99.9% confidence-interval half-widths; ratios divide the displayed
means. The range row uses a separate, focused repeat with eight one-second
warmups (see [`range-command.txt`](range-command.txt) and
[`range-confirm-jdk25-m4pro.json`](range-confirm-jdk25-m4pro.json)). In the first
full-matrix run, the TreeMap range operation changed sharply within both forks
(about 1.9 microseconds for its first two measured iterations, then about 0.7);
the longer warmup settled to roughly 0.7 microseconds in both forks. Optimized
is faster than the original on all five tested operations in this fixture.
TreeMap is slower on all five. This single JDK/hardware/key shape is not a
general-purpose ranking, and these results do not predict full-width
random keys, other sizes or distributions, String keys, memory use, concurrent
access, or application behavior. No retained-memory comparison was run for this
campaign. Full verification receipts are in [`verification.md`](verification.md).
