# String covered-subtree experiment

Base: `origin/main` commit `603b6f5b0ebcdddf5a5dab15b9a3f0f71c704340`.
Candidate source: `library/src/main/java/io/github/enderekici/packedbtree/StringBTree.java`,
SHA-256 `0b9782f57b7d67e07de3bc8ee1293927cb9c42a2f98463ce52d23c93a438190e`.
Frozen current-C3 baseline source:
`simd/src/test/java/io/github/enderekici/packedbtree/CoveredBaselineStringBTree.java`, SHA-256
`44566719000b19bb3d21fa4b991d338ae831c7268e4f9a3474a7b4d2cb01f005` (reverse-normalize only
class and constructor names before comparing with the merged C3 source).

Candidate hypothesis: after a parent's separators show that both range bounds enclose a child
subtree, a complete inorder walk avoids repeated `String.compareTo` lower/upper searches in every
descendant. Boundary children retain C3 bounded traversal. This predicts gains for ranges spanning
several children, with no point-operation effect; small ranges may regress due to coverage checks.

Correctness tests and `StringCoveredRangeBenchmark` were prepared before the CPU lease opened.
Build/test and guarded screening results are below. Keep all runs guarded by runtime code-source
and SHA checks for candidate and frozen baseline outer and nested classes, with `-foe true`; report
the intentional wrong-hash rejection before timing. Do not include invalid or unguarded rows as
evidence.

Final measured-source hashes: candidate production source
`0b9782f57b7d67e07de3bc8ee1293927cb9c42a2f98463ce52d23c93a438190e`, frozen baseline source
`44566719000b19bb3d21fa4b991d338ae831c7268e4f9a3474a7b4d2cb01f005`, guarded benchmark source
`e98348c461b53410edfab301c354187df1bea954b546e9fa0227d06274749b32`, and final focused test source
`0055287cf54582e736b6ffc8c6ad9936e6e255c62be14d3ea67757195278d2cd`. The JMH range cursor wraps
before integer overflow, including width 0. After benchmark source freeze, tests gained shared-key
reference checks, equal-but-distinct query-bound checks, overwrite key-identity coverage, and a
stronger callback exception fixture; measured production and benchmark sources were unchanged.

## Guarded screening run (exploratory)

All commands used the newly created worktree at `/Users/ender/code/evamcep/.packed-btree-worktrees/string-covered`, branch `feature/string-covered-ranges`, base `603b6f5b0ebcdddf5a5dab15b9a3f0f71c704340`.

Build and correctness commands:

```sh
JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home/bin:$PATH ./mvnw --settings /tmp/packed-btree-maven-settings.xml -pl library test -Dtest=StringBTreeTest
JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-25/Contents/Home/bin:$PATH ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd -pl simd -am test-compile
JAVA_HOME=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home PATH=/Users/ender/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home/bin:$PATH ./mvnw --settings /tmp/packed-btree-maven-settings.xml -pl benchmarks -am package
```

Results: JDK17 focused `StringBTreeTest` passed 8/8. JDK25 compiled library and all SIMD test/benchmark sources. The JDK17 benchmark launcher package passed 4/4 benchmark tests. Full clean verify has not been run in this experiment. Logs: `/tmp/string-covered-jdk17-test.log`, `/tmp/string-covered-jdk25-compile.log`, `/tmp/string-covered-jdk17-benchlauncher.log`.

JMH 1.37 ran on Zulu OpenJDK 25.0.3 / Apple M4 Pro. Runtime class manifest (all forks validated both arms):

| Arm/type | Code source | SHA-256 |
| --- | --- | --- |
| candidate `StringBTree` | `library/target/classes` | `b3d28205cd35c91519d1f55757e47a2754209363f52ac3ea29aa02dba65ea320` |
| candidate `StringBTree$Node` | `library/target/classes` | `da2cb1f658cf4e590dc70be859aa76c4ad37c1b6934a86a6585ff73bd79cd14b` |
| frozen C3 `CoveredBaselineStringBTree` | `simd/target/test-classes` | `c2983747a4670371bdc94ca0cda888879dfd1027cc95442e5e21eb5d384a434e` |
| frozen C3 `CoveredBaselineStringBTree$Node` | `simd/target/test-classes` | `9e94dd030a7ba75328e9340be2daf6a7fedb8579af9eac0177cf99ec56daa938` |

A negative control intentionally set the expected candidate outer-class SHA to 64 zeroes. It exited 1 with `Unexpected loaded class` and no accepted measurement row; receipt is `candidate1-wrong-hash-control.log`. The valid smoke then exercised hit, miss, overwrite, churn, and range on both arms, 10 rows total, exit 0: `candidate1-guarded-smoke.json`.

The primary screen selected `hit|miss|overwrite|churn|range`, `implementation=candidate,baseline`, sizes 16384/262144, shapes short/random/prefix, String values, width 64: 60 rows. The width screen selected `range`, the same sizes/shapes/arms, and widths 0/1/8/64/1024: 60 rows. Both used `-wi 1 -w 250ms -i 2 -r 250ms -f 1 -foe true -rf json`; these are short one-fork screens, not significance tests. Raw data: `candidate1-primary-screen.json` and `candidate1-range-width-screen.json`. Launch logs: `/tmp/string-covered-primary.log` and `/tmp/string-covered-widths.log`.

For width 64, screen point estimates favored C1 over baseline in all six range cells: 10.9–20.3% lower in the primary screen (7.9–17.5% in the independent width screen). Width 1024 estimates were 14–40% lower in all six cells. Widths 0/1/8 were mixed; width 1 was 9% slower on 262K prefix in the short screen. Point/write/churn estimates stayed within roughly ±4% but were mixed. No causal or significance claims follow from these short screens. The prefix/262K width-1 cell was included as a confirmation guardrail.

## Confirmation results

JDK25 (Zulu 25.0.3, JMH 1.37, Apple M4 Pro): 38 rows across 76 forks, with 2 forks, three 1s
warmups and five 1s measurements per row. Each fork verified candidate and baseline outer and
nested-node code source and SHA-256. The three all-operation groups alternated arm order.

| Fixture, width 64 | Baseline ns/op | Candidate ns/op | Change |
| --- | ---: | ---: | ---: |
| 16K short | 384.152 ± 6.386 | 325.963 ± 4.963 | −15.1% |
| 262K random | 798.548 ± 22.451 | 708.152 ± 41.300 | −11.3% |
| 262K common-prefix | 1005.534 ± 50.816 | 847.488 ± 20.613 | −15.7% |

Point, miss, overwrite and churn means stayed within approximately 2% for these fixtures. The
262K-prefix width guard was 0/1/8/1024 entries: width 0 was flat (6.820 vs 6.806 ns/op), width 1
was 0.8% slower (390.679 vs 393.796), width 8 was modestly faster (454.419 vs 438.779), and width
1024 was 32.5% faster (8403.565 vs 5670.477). The short-screen width-1 regression did not reproduce: its confirmation point was 0.8% slower, with overlapping intervals. Widths 0/1/8 remain inconclusive; do not interpret the overlapping intervals as proof of equivalence.

JDK17 ran a decisive range64 subset using `javac --release 17`, separate candidate and baseline
output directories, and the same JMH 1.37 launcher. It covered 16K short, 262K random, and 262K
prefix with two forks, three 1s warmups and three 1s measurements. Means were 378.347→300.399 ns/op
(−20.6%), 670.265→570.358 (−14.9%), and 822.793→678.848 (−17.5%). This six-row cross-JDK check is
not a full operation matrix. Its intentional wrong-hash control exited 1 with `Unexpected loaded
class`; all valid forks verified both class fingerprints.

Raw JSON is `confirmation-jdk25-*.json` and `confirmation-jdk17-*.json` in this directory.
Committed logs are `string-covered-confirm-*.log` and `string-covered-jdk17-*.log`; the JDK25
width confirmation log is `string-covered-confirm-widths-prefix262.log`. The JDK17 negative guard receipt is
`jdk17-wrong-hash-control.log`. JDK17 runtime class hashes (Zulu 17.0.19) were candidate outer/Node
`19f004f10cf337285720c45ff43d2ac21970a04e037fca801e87e335ee66aa4b` /
`19b4fd06c5b39149233a7eaeb762c4df2489ca50af768ceb2b32771d7f53a96b`, baseline outer/Node
`92de6aea753dc619c8480617528d3d37709043b4dd9a946ae7d5d52ba0cf3fd5` /
`c455ebdb7dad53bf0c1ffede42c0d57488e5f8b9dab9079596db39f13009fcf1`. Candidate classes loaded
from `/tmp/string-covered-jdk17-candidate`, baseline classes from `/tmp/string-covered-jdk17-harness`.

The final JDK17 focused test passed 9/9 with:
`./mvnw --settings /tmp/packed-btree-maven-settings.xml -pl library test -Dtest=StringBTreeTest`.
This isolated String branch did not run full clean verify or a packaged consumer probe. The
combined Long/String integration run did both JDK 17 and JDK 25 full verifies and fresh packaged
consumer probes; see [`results/final-integration-verification.md`](../../final-integration-verification.md).
