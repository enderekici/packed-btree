# Candidate 2 short screen

Date: 2026-10-25. Worktree: `feature/string-opt-round2`. Frozen baseline commit: `94adfd0e37a1168e80635a9124fac6f82bd8d1e5`. C2 source SHA-256: `33202d7a75f2ecaca65e36e84915a5cb020d8906fa2701d7df8748004b44a3ed`. C2 started from baseline `StringBTree.java` SHA-256 `1b680f7e44c2e1dadcfc39de6d75cf64303f08d0a7eca2d641506afd299ccb6b`; only `visit` changed.

## Verification and run

JDK 25 `-Psimd -pl simd -am clean test-compile` passed; JDK 17 `-pl library test -Dtest=StringBTreeTest` passed 5/5. Guarded smoke passed all five operations for candidate and baseline at 16K/short/String with setup/teardown invariants active. Smoke evidence is `/tmp/string-candidate2-smoke.log` and `/tmp/string-candidate2-smoke.json`.

Short screen used JDK 25.0.3, one fork, one 250ms warmup and two 250ms measurements, `-foe true`, production classes first on the classpath. It selected five operations, candidate+baseline, sizes 16384/262144, shapes short/random/prefix, and String values (60 configurations):

```sh
JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin:$PATH java -cp 'library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar' org.openjdk.jmh.Main 'bench.StringTreeHillclimbBenchmark.*' -p implementation=baseline,candidate -p size=16384,262144 -p shape=short,random,prefix -p valueType=string -wi 1 -w 250ms -i 2 -r 250ms -f 1 -foe true -rf json -rff /tmp/string-candidate2-screen.json -jvmArgsAppend '-Dstring.expected.candidate.tree.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/library/target/classes -Dstring.expected.candidate.tree.sha256=076ab1a4c86140c93c9991b529722dfb26e1a5f77b902b31e7bfdcf276aa14a4 -Dstring.expected.candidate.node.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/library/target/classes -Dstring.expected.candidate.node.sha256=a9d92533e26926feca50a1fa5eb3aa3f22edfd36e7e31de88e9c4ccfd616dd51 -Dstring.expected.baseline.tree.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/simd/target/test-classes -Dstring.expected.baseline.tree.sha256=dc021d2cc18f322bd792e3140b2711b9af8744b696979158df3f2ed1929f60b2 -Dstring.expected.baseline.node.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/simd/target/test-classes -Dstring.expected.baseline.node.sha256=33867d8c3f6097e93ef9b5c65415f9292ebc5d41ae01a14badf12223f9331ae8' > /tmp/string-candidate2-screen.log 2>&1
```

The harness verified these candidate class fingerprints in every fork: `StringBTree` `076ab1a4c86140c93c9991b529722dfb26e1a5f77b902b31e7bfdcf276aa14a4` and `StringBTree$Node` `a9d92533e26926feca50a1fa5eb3aa3f22edfd36e7e31de88e9c4ccfd616dd51`. Baseline outer/node hashes remained `dc021d2cc18f322bd792e3140b2711b9af8744b696979158df3f2ed1929f60b2` / `33867d8c3f6097e93ef9b5c65415f9292ebc5d41ae01a14badf12223f9331ae8`.

## Provisional observation

Range64 point estimates were lower for C2 in all six cells: short 11.3% (16K) and 18.9% (262K); random 7.5% and 16.6%; common-prefix 18.3% and 34.9%. Non-range operations were mostly similar, with noisy small differences. The exact two-iteration raw data are in [candidate2-screen.json](candidate2-screen.json); full output is `/tmp/string-candidate2-screen.log`.

This is a short screen, not a significance test: one fork and two 250ms measurements do not establish repeatability. It covers String values only. No broad performance claim is made.
