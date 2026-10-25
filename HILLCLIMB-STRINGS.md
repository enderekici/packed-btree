# StringBTree hill-climb round two

## Scope and rules

Optimize only `StringBTree<V>` in this worktree. Preserve `String.compareTo` UTF-16 order,
null-key rejection, nullable values, exact range bounds, deletion invariants, and inactive-slot
reference clearing. `String -> String` and `String -> Long` are separate workload classes. Do not
time code that creates strings. The comparison harness must load and SHA-256-check both the
candidate and exact frozen baseline outer and nested-node classes in every fork, with production
classes first on the classpath. Use `-foe true` and finite explicit benchmark selections.

Baseline source is commit `94adfd0e37a1168e80635a9124fac6f82bd8d1e5`, branch
`feature/string-opt-round2`. Production `StringBTree.java` SHA-256 is
`1b680f7e44c2e1dadcfc39de6d75cf64303f08d0a7eca2d641506afd299ccb6b`.
`CurrentBaselineStringBTree.java` is a frozen copy whose only changes are the class and
constructor names; reverse-normalized source matched the production file byte-for-byte.

## Harness and verification

`simd/src/test/java/bench/StringTreeHillclimbBenchmark.java` selects one map per trial and
supports candidate, frozen baseline, and `TreeMap`. It prebuilds sorted keys, shuffled insertion
and point-query ranks, and equal-but-distinct hit strings backed by copied char arrays. Misses
are related to real keys by appending NUL. Overwrite alternates prebuilt values; churn toggles
between present and absent key forms. `range64` uses a sequential window selected from seeded
rank fixtures and the same stored key refs in each map; it consumes equal entry/value payloads.
The highest exclusive endpoint is precomputed from `lastKey + NUL`, outside measured code.
Fixture checks validate hit/miss/delete/refill and three range windows; teardown checks exact
prebuilt value identity, cardinality, absent alternate keys, and candidate/baseline invariants.

Commands/results so far:

| Check | Result |
| --- | --- |
| JDK 25 `-Psimd -pl simd -am clean test-compile` | BUILD SUCCESS; all 13 SIMD test sources and benchmark compiled |
| JDK 17 `-pl library test -Dtest=StringBTreeTest` | BUILD SUCCESS; 5 tests, 0 failures/errors/skips |
| JDK 17 `-pl library package` | BUILD SUCCESS; 15 tests, 0 failures/errors/skips; library and source/Javadoc jars built |
| JDK 25 baseline random 262144 String/String `hit`, JFR, 1 fork; warmup 2x500ms, measure 3x1s | 322.592 +/- 32.047 ns/op; 3 measurement iterations |

The baseline JFR profile's 227 execution samples were 50.22% in
`CurrentBaselineStringBTree$Node.index(String)` (114) and 48.46% in
`String.compareTo(String)` (110); the remaining 1.32% was other methods. The harness printed and
accepted all four expected loaded class locations and hashes. Full command output, JSON and JFR
are in `/tmp/string-baseline-profile.log`, `/tmp/string-baseline-profile.json`, and
`/tmp/string-baseline-jfr/bench.StringTreeHillclimbBenchmark.hit-AverageTime-implementation-baseline-shape-random-size-262144-valueType-string/profile.jfr`.
An earlier attempt correctly failed the runtime hash guard because the supplied candidate-tree
hash was mistyped; the corrected run passed. The first attempt to select `simd` without `-Psimd`
failed reactor selection. A subsequent combined selected-test run passed the library's 5 tests
but failed because the SIMD module had no selected tests; compile and test were then split as
above.

## Candidate ledger

### Candidate 1: terminate node search on equality

`Node.index` returns `~position` immediately when a binary-search comparison equals the query;
otherwise it returns the usual nonnegative lower bound. Lookup, overwrite and deletion decode
negative hits, and inclusive range traversal decodes a matching lower-bound key. The child-split
path retains its explicit promoted-key comparison. Commit `9ad847d` contains this isolated source
candidate and its provisional screen evidence. JDK 25 compiled the SIMD test/benchmark sources;
JDK 17 StringBTreeTest passed 5/5. A wrong-hash launch failed as required, and all five operations
passed guarded smoke on both baseline and candidate. The short screen has one fork and two 250ms
measurements; it is provisional, String values only, and does not establish significance.

### Candidate 2: bound each visited node once

For this isolated candidate, production `StringBTree.java` has been restored byte-for-byte to
baseline source `94adfd0` before editing. `visit` computes the local exclusive upper-bound index
once per node and loops while the lower-bound cursor is below it, instead of calling
`compareTo(to)` on each emitted separator. Child traversal order is unchanged. C2 compiled and
passed StringBTreeTest 5/5; all five operations passed guarded candidate/baseline smoke. Its
60-cell short screen estimated range64 faster in all six cells (7.5–34.9%). This is provisional
one-fork/two-iteration evidence; raw results and command are in
`results/hillclimb-round2/strings/candidate2-screen.md` and the adjacent JSON. This is an
intermediate historical candidate; final C3 combines C1 and C2, as recorded below.

### Candidate 3: combine point search and range bound changes

C3 combines C1's early equality return with C2's per-node upper bound. Both `from` and `to`
indices decode negative exact-match positions; decoding the exclusive upper bound keeps a matching
separator excluded. Existing root checks preserve empty/reversed ranges. Targeted StringBTreeTest
passed 5/5, and guarded smoke passed all five operations for candidate and baseline. The full
confirmation, matched TreeMap control, JDK17 subset, and JOL estimate are recorded in
`results/hillclimb-round2/strings/candidate3-confirmation.md`. C3 reduced all 16 JDK25 confirmation
point estimates versus the frozen baseline; 15 comparisons had non-overlapping 99.9% intervals,
with only the 262K prefix miss overlapping. The JDK17 hit intervals were disjoint; range and churn
intervals overlapped. Retain the scope limits in the evidence report.

# StringBTree hill-climb round three: covered subtrees

This round starts from merged `origin/main` commit `603b6f5b0ebcdddf5a5dab15b9a3f0f71c704340`.
The frozen baseline is the current C3 `StringBTree` copied to
`simd/src/test/java/io/github/enderekici/packedbtree/CoveredBaselineStringBTree.java`; only outer
class/constructor names change. The exact baseline production source SHA-256 is
`94853ac9b62ff038696271a4b5de80c6110887e4ecf639f400f38767a60ab94e`; frozen-copy source SHA is
`44566719000b19bb3d21fa4b991d338ae831c7268e4f9a3474a7b4d2cb01f005`.

Candidate 1 passes inherited lower/upper coverage flags during range traversal. When parent
separators establish that a child's entire key interval lies within `[fromInclusive,toExclusive)`,
`visitAll` walks that subtree in order without repeating either bound search. Boundary subtrees
still use the normal lower-bound searches. No node metadata or public API changes. Correctness
coverage has been added for sizes 0, 1, 31, 32, 33, 127, and 512, including NUL, supplementary
and unpaired surrogate code units, `\uFFFF`, common prefixes, exact/equal/reversed/absent bounds,
nullable values with reference-identity checks, and randomized mutations checked against
`TreeMap`.

The guarded JDK25 confirmation completed 38/38 rows (76 forks) with two forks, three 1s warmups,
and five 1s measurements. Range64 improved 15.1%, 11.3%, and 15.7% on 16K-short, 262K-random,
and 262K-prefix respectively. Point/write/churn guardrails were within about 2%. Prefix width 1
was 0.8% slower (390.68 to 393.80 ns/op); width 0 was flat, width 8 was modestly faster, and width
1024 improved 32.5%. The one-fork short screen's apparent width-1 regression did not reproduce.
JDK17 confirmed the three range64 fixtures in six rows (two forks, three 1s warmups and three 1s
measurements): candidate was 20.6%, 14.9%, and 17.5% faster. The JDK17 focused StringBTreeTest
passed 9/9 after adding stored-key reference checks and tightening the callback-exception fixture.
See `results/hillclimb-round3/strings/README.md` for source/runtime hashes, exact commands, raw
JSON and the limits of these measurements. Full clean verify remains for the integration run.
