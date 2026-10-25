# Candidate 1 short screen

Date: 2026-10-25. Worktree: `feature/string-opt-round2`. Baseline commit: `94adfd0e37a1168e80635a9124fac6f82bd8d1e5`. Candidate source SHA-256: `e013b049eda0b6b22ad10c0673afc21b4a66d7a89290c775844268aee839e956`.

## Run

JDK 25.0.3, `-foe true`, one fork, one 250 ms warmup, two 250 ms measurements. The guarded harness selected one map per trial. Classpath order was `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar`.

The exact run selected five operations (`hit`, `miss`, `overwrite`, `churn`, `range64`), implementation `baseline,candidate`, sizes `16384,262144`, shapes `short,random,prefix`, and `valueType=string` (60 configurations):

```sh
JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin:$PATH java -cp 'library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar' org.openjdk.jmh.Main 'bench.StringTreeHillclimbBenchmark.*' -p implementation=baseline,candidate -p size=16384,262144 -p shape=short,random,prefix -p valueType=string -wi 1 -w 250ms -i 2 -r 250ms -f 1 -foe true -rf json -rff /tmp/string-candidate1-screen.json -jvmArgsAppend '-Dstring.expected.candidate.tree.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/library/target/classes -Dstring.expected.candidate.tree.sha256=0117345295f958bd5799340af651c5cfce15af423a7e66b3ac2f07fffcd0aad4 -Dstring.expected.candidate.node.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/library/target/classes -Dstring.expected.candidate.node.sha256=5ef0e63bccf03cc29e1b896a6788ebfb46e4119503022a676ad033931c159755 -Dstring.expected.baseline.tree.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/simd/target/test-classes -Dstring.expected.baseline.tree.sha256=dc021d2cc18f322bd792e3140b2711b9af8744b696979158df3f2ed1929f60b2 -Dstring.expected.baseline.node.location=/Users/ender/code/evamcep/.packed-btree-worktrees/strings/simd/target/test-classes -Dstring.expected.baseline.node.sha256=33867d8c3f6097e93ef9b5c65415f9292ebc5d41ae01a14badf12223f9331ae8' > /tmp/string-candidate1-screen.log 2>&1
```

Before this screen, JMH was run once with the candidate-tree expected SHA set to `0000`; it exited 1 with `Unexpected loaded class` under `-foe true`. Positive smoke then passed all five methods for both baseline and candidate at 16K/short/String, with setup and teardown checks active. Smoke logs/JSON are `/tmp/string-guard-wrong.log`, `/tmp/string-smoke.log`, and `/tmp/string-smoke.json`.

## Provisional observation

Candidate point-operation estimates are lower in all 18 cells: hit deltas range from 0.3% to 17.6% (262K prefix hit is effectively flat), misses from 1.8% to 8.6%, and overwrites from 7.6% to 13.1%. Churn estimates are lower in all six cells (2.2% to 12.4%). Range64 is mixed: estimates range from 1.7% faster to 4.2% slower. Exact per-iteration data and class-fingerprint output are in the committed [raw JMH JSON](candidate1-screen.json); complete JMH output is `/tmp/string-candidate1-screen.log`.

This is a screening result only: one fork and two short measurement iterations do not establish significance or generality. It covers String values only, not String-to-Long. No acceptance or broad performance claim is made from it.
