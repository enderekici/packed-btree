# String-key B-tree benchmark results

The timing runs completed successfully on JDK 25.0.3, Apple M4 Pro, macOS 27.0, with 64 GiB RAM. Across all passes there were 113 configurations (90 point + 18 range + 5 large-hit), 226 forks, and 678 measured iterations. Each JMH configuration used two forks, two 500 ms warmup iterations, and three 500 ms measurement iterations. This is a short comparison run; treat the confidence intervals and close results cautiously. Values below are average nanoseconds per operation with JMH's 99.9% confidence interval.

The point fixtures contain 16,384 entries for the main matrix. The B-tree and JDK maps received the same prebuilt keys and values in fixed-seed shuffled insertion order. Point queries use a separate fixed-seed randomized rank traversal; query strings have equal contents in separate char-array-backed objects. Miss keys are each stored key with a NUL appended. No Strings are built in timed operations. Range fixtures traverse exactly 64 adjacent entries at fixed-seed randomized ranks, and both the B-tree callback and JDK entry iterator consume each stored value into the same reusable checksum/count accumulator. Range bounds are prebuilt key references. Setup checks each range count/checksum against a TreeMap oracle; teardown rechecks every range.

The B-tree and TreeMap hit results are close in many 16K cases; common-prefix inputs cost more than short/random keys for both. HashMap and ConcurrentHashMap have lower point-operation latency in these single-threaded runs. This does not characterize concurrent throughput. Exact ordered 64-entry ranges favored the B-tree over TreeMap in this sample, while ConcurrentSkipListMap range results were noisier. The larger random hit-only probe retained the same map and query fixtures at 262,144 entries; it shows the observed latency ordering at that size but does not support extrapolating String-value results to Long values.

## 16,384-entry point matrix

#### hit

| Value type | Shape | B-tree | TreeMap | HashMap | ConcurrentHashMap | ConcurrentSkipListMap |
|---|---|---:|---:|---:|---:|---:|
| string | short | 118.2 ± 13.6 | 114.1 ± 10.4 | 11.0 ± 1.2 | 11.4 ± 0.6 | 187.7 ± 6.8 |
| string | random | 151.7 ± 9.9 | 112.2 ± 10.8 | 9.2 ± 0.2 | 9.8 ± 0.4 | 195.7 ± 12.0 |
| string | prefix | 177.4 ± 5.1 | 182.8 ± 2.8 | 13.4 ± 0.6 | 13.8 ± 0.6 | 259.7 ± 7.8 |
| long | short | 116.1 ± 11.1 | 111.1 ± 10.0 | 11.0 ± 0.8 | 11.1 ± 0.6 | 185.4 ± 11.9 |
| long | random | 129.5 ± 9.2 | 114.5 ± 11.4 | 9.3 ± 0.5 | 9.4 ± 0.3 | 189.9 ± 8.3 |
| long | prefix | 193.3 ± 70.5 | 180.5 ± 9.1 | 13.0 ± 0.5 | 13.6 ± 0.4 | 281.6 ± 33.3 |

#### miss

| Value type | Shape | B-tree | TreeMap | HashMap | ConcurrentHashMap | ConcurrentSkipListMap |
|---|---|---:|---:|---:|---:|---:|
| string | short | 118.8 ± 9.5 | 130.6 ± 2.7 | 4.3 ± 0.2 | 5.7 ± 0.1 | 197.7 ± 9.2 |
| string | random | 127.8 ± 11.7 | 139.7 ± 9.9 | 5.0 ± 0.9 | 7.8 ± 0.9 | 210.9 ± 10.8 |
| string | prefix | 177.2 ± 8.4 | 195.5 ± 11.3 | 4.8 ± 0.5 | 7.8 ± 0.1 | 266.8 ± 20.4 |
| long | short | 116.9 ± 7.6 | 127.3 ± 12.8 | 4.0 ± 0.5 | 5.4 ± 0.1 | 191.0 ± 15.9 |
| long | random | 124.7 ± 8.9 | 138.7 ± 17.8 | 5.2 ± 0.4 | 7.6 ± 0.8 | 202.5 ± 9.0 |
| long | prefix | 177.4 ± 5.8 | 186.3 ± 5.7 | 4.5 ± 0.4 | 7.6 ± 1.1 | 259.5 ± 11.0 |

#### overwrite

| Value type | Shape | B-tree | TreeMap | HashMap | ConcurrentHashMap | ConcurrentSkipListMap |
|---|---|---:|---:|---:|---:|---:|
| string | short | 130.1 ± 7.0 | 165.3 ± 38.4 | 13.6 ± 0.6 | 27.2 ± 5.6 | 228.3 ± 27.2 |
| string | random | 146.7 ± 23.7 | 168.0 ± 32.5 | 11.2 ± 0.3 | 23.2 ± 4.0 | 248.6 ± 28.3 |
| string | prefix | 323.1 ± 194.9 | 210.5 ± 9.8 | 17.8 ± 1.8 | 28.0 ± 5.9 | 286.8 ± 22.9 |
| long | short | 132.9 ± 13.8 | 147.7 ± 15.0 | 13.0 ± 1.5 | 27.0 ± 3.3 | 219.7 ± 14.5 |
| long | random | 158.3 ± 19.7 | 155.5 ± 16.5 | 11.1 ± 0.2 | 22.2 ± 0.8 | 233.1 ± 11.9 |
| long | prefix | 318.7 ± 127.7 | 206.3 ± 34.9 | 16.3 ± 3.0 | 27.3 ± 5.2 | 320.8 ± 133.4 |

## 16,384-entry ordered range64

| Value type | Shape | B-tree | TreeMap | ConcurrentSkipListMap |
|---|---|---:|---:|---:|
| string | short | 444.2 ± 14.7 | 524.1 ± 44.0 | 737.2 ± 51.0 |
| string | random | 468.2 ± 19.7 | 535.8 ± 43.8 | 725.7 ± 34.6 |
| string | prefix | 552.9 ± 32.3 | 580.1 ± 18.0 | 497.8 ± 74.5 |
| long | short | 445.2 ± 33.0 | 511.0 ± 22.1 | 734.7 ± 9.5 |
| long | random | 459.6 ± 20.6 | 510.4 ± 17.8 | 578.8 ± 495.1 |
| long | prefix | 542.5 ± 25.1 | 570.9 ± 5.2 | 911.8 ± 80.2 |

## 262,144-entry random String-to-String hit probe

| Map | ns/op |
|---|---:|
| B-tree | 344.2 ± 9.1 |
| TreeMap | 404.9 ± 27.2 |
| HashMap | 45.4 ± 2.6 |
| ConcurrentHashMap | 46.5 ± 3.7 |
| ConcurrentSkipListMap | 804.9 ± 83.4 |

## Commands

```sh
env JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin:$PATH java -jar benchmarks/target/benchmarks.jar 'bench.StringMapBenchmark.(hit|miss|overwrite)' -p size=16384 -p implementation=btree,tree,hash,concurrentHash,skiplist -p shape=short,random,prefix -p valueType=string,long -wi 2 -w 500ms -i 3 -r 500ms -f 2 -rf json -rff results/string-benchmarks/point-16k.json

env JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin:$PATH java -jar benchmarks/target/benchmarks.jar 'bench.OrderedStringRangeBenchmark.range64' -p size=16384 -p implementation=btree,tree,skiplist -p shape=short,random,prefix -p valueType=string,long -wi 2 -w 500ms -i 3 -r 500ms -f 2 -rf json -rff results/string-benchmarks/range-16k.json

env JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin:$PATH java -jar benchmarks/target/benchmarks.jar 'bench.StringMapBenchmark.hit' -p size=262144 -p implementation=btree,tree,hash,concurrentHash,skiplist -p shape=random -p valueType=string -wi 2 -w 500ms -i 3 -r 500ms -f 2 -rf json -rff results/string-benchmarks/point-262k-string-random-hit.json
```

## Reproducibility

- Commit: `b08ea4bd0a32f8ca3ba59601880ad40df0c3489b`
- JMH: 1.37
- JDK: Zulu OpenJDK 25.0.3+9-LTS, 64-bit Server VM, G1 GC, 1 GiB heap (`-Xms1g -Xmx1g`)
- CPU / OS: Apple M4 Pro, Darwin 27.0.0, arm64; host has 64 GiB memory
- Full `clean verify`: JDK 17 and JDK 25 passed, 10 library tests plus 1 benchmark-module test each.
- Source SHA-256:
  - `library/src/main/java/io/github/enderekici/packedbtree/StringBTree.java`: `1b680f7e44c2e1dadcfc39de6d75cf64303f08d0a7eca2d641506afd299ccb6b`
  - `library/src/test/java/io/github/enderekici/packedbtree/StringBTreeTest.java`: `c1f427309070de270e4624f3965d947ece6cc1ac1ff49f7b6864dde1c43b5ae9`
  - `benchmarks/src/main/java/bench/StringMapBenchmark.java`: `fa2a6a99f9b2c2484760743594a4ba25eadddd33f5ad52bc1476740f1f7c45f0`
  - `benchmarks/src/main/java/bench/OrderedStringRangeBenchmark.java`: `b38e233a4331e7b3cd2f6661bf72ab6c584f037c426170be6ddff6204bad11f6`
- Raw JMH JSON: `point-16k.json` (90 cases), `range-16k.json` (18 cases), `point-262k-string-random-hit.json` (5 cases).
- Raw JSON SHA-256:
  - `point-16k.json`: `fc02daad9c43c77eada82c24fec8e46cc2654e78e0cad5ac11a98562f8f97c82`
  - `range-16k.json`: `aafbe95b822cb55f17d8d2e230c1049dae5df6ee400fd725afdf692d2290842a`
  - `point-262k-string-random-hit.json`: `e147909b7cbe27f9d816914fca011dc125a0767a825d52c311af9dd1a26cb654`
- Full process logs: `/tmp/string-point-16k.log`, `/tmp/string-range-16k.log`, `/tmp/string-point-262k.log`.
- Setup and teardown positive controls passed; all three JMH commands exited 0.
