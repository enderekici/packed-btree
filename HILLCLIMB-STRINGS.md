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

`Node.index` now returns `~position` immediately when a binary-search comparison equals the query;
otherwise it returns the usual nonnegative lower bound. Lookup, overwrite and deletion decode
negative hits, and inclusive range traversal decodes a matching lower-bound key. The child-split
path retains its explicit promoted-key comparison. This is a point-search hypothesis motivated
by the hot-comparison profile; it has not yet been compiled, tested, or timed. Do not infer a
performance gain until runtime-guarded results exist. Before candidate timing, run the targeted
String tree tests, then guarded fixture smoke for all five operations against both candidate and
frozen baseline.
