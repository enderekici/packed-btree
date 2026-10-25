# Default search and node-size hill-climb ledger

## Scope and invariants

- Source baseline: `57ea447d94078b2a0872569b09bb88986b71f886` on `feature/parallel-integration`.
- Library/API invariants: Java 17 default consumer; signed full-width `long` ordering; String ordering/null-value and half-open range contracts; `SearchStrategy` / `CompressedLongBTree` composition; optional `VectorSearch`; no batched API or concurrency redesign.
- One candidate at a time. Preserve each source hash and result. Do not tune from a single point-hit case. Final verification is JDK 17 clean verify and JDK 25 `-Psimd` clean verify after the last source edit.
- Correctness gate for each candidate: existing differential/property tests plus targeted changed-path checks for default strategy, custom strategy, compressed/custom composition, VectorSearch, range, insert/overwrite/delete and full-width keys before timing.
- Timing gate: same selected-map harness, alternating baseline/candidate when practical; small and large sizes; dense and full-width keys; hits, misses, ranges, writes; two forks, at least three warmups and five one-second measurements for confirmation. Capture JFR and inspect execution call stacks before naming a hotspot. Record retained-memory and write/range guardrails. JMH intervals and workload limits remain explicit.

## Baseline

- Initial integrated source: `57ea447d94078b2a0872569b09bb88986b71f886`. Every hill-climb candidate is compared against this source, not the historical pre-hook implementation.
- The immutable A/B control will be a test-only renamed copy of `LongBTree.java` extracted from `57ea447` (`bench.CurrentBaselineLongBTree`); code differences must be limited to package/class/constructor identifiers. Candidate is the production `LongBTree`; each trial instantiates only one selected tree. `bench.PreHookLongBTree` remains a historical control only.
- Existing selected-map comparison source: `dcb5130afe5d8a162acd3de465026f93bfbd6933` (same current library source; JMH harness only). The pre-hook comparison at `results/default-search/selected-default-control.json` is not the hill-climb A/B baseline. Its 4,096 point estimate was 26.7% higher and its 262,144 point estimate 1.7% higher; intervals overlap at both sizes.
- Baseline JFR run at source `57ea447`: current default hit, size 262,144, two forks, 3x1s warmup/5x1s measure, exit 0. Recording: `/tmp/hillclimb-jfr-baseline,stackDepth=64/io.github.enderekici.packedbtree.simd.CurrentDefaultSearchRegressionBenchmark.currentDefault-AverageTime-size-262144/profile.jfr`; log `/tmp/hillclimb-jfr-baseline.log`. `jfr view hot-methods` found 374 execution samples: `LongBTree$Node.index(long)` was the top frame for 91.18%, `getOrDefault` 8.02%. Representative stacks include `Node.index(SearchStrategy,long)`, but sampling does not quantify dispatch overhead. Binary-search comparisons dominate samples; use this only to motivate a bounded dispatch experiment.
- Initial baseline/candidate screen: `results/hillclimb/baseline-screen.md`, summary CSV and 40-row JMH JSON. It covers five operations, sizes 4,096/262,144, dense/fullWidth keys, one fork and 2x250ms measurement. The runtime classpath was not fingerprint-verified; treat this only as baseline diagnostic context, not proof of a candidate comparison. It showed same-source small dense point estimates differed 14–18%, consistent with substantial screen noise. Corrected screens now require per-fork tree and Node bytecode fingerprints; details in `results/hillclimb/harness-classpath-correction.md` and `runtime-fingerprint-smoke.md`.
- Baseline JFR call-stack evidence: see the previous bullet and `/tmp/hillclimb-jfr-baseline-stacks.txt`; 374 samples, `Node.index(long)` top frame 91.18%, `getOrDefault` 8.02%. The nullable strategy overload appears in representative stacks but overhead is not separately measured. No performance conclusion from this profile.
- Shared host: root observed unrelated kubectl/Codex CPU use. Runs use paired maps/settings and remain subject to environmental noise.

## Candidates and decisions

| ID | Candidate / hypothesis | Source hash | Correctness gate | Benchmark / evidence | Decision and reason |
| --- | --- | --- | --- | --- | --- |
| C1 | Default `getOrDefault` scalar/custom traversal split. | `936ca4653e43dd08d012a1a4be7618360a0c8bcd535138273b443d62df30d3ba` (temporary candidate; reverted) | JDK 25 `-Psimd`, LongBTreeTest 6 + VectorSearchTest 2 passed. | Corrected guarded 40-row screen: `results/hillclimb/c1-corrected-summary.md` and two raw JSONs. | Rejected without confirmation: large cases neutral/mixed; dense 4,096 miss +24.5% and overwrite +32.4%. No coherent gain. |
| C2 | Search algorithm only, fixed degree 16: linear scan vs binary lower-bound. | `9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503` (accepted) | JDK 25 `-Psimd`, LongBTreeTest 6 + VectorSearchTest 2 passed. JDK 17 decisive JMH subset and final full verifies also pass. Runtime tree/Node fingerprints verified in every JMH fork. | Guarded confirmation and memory: `results/hillclimb/c2-confirm/README.md`, eight JDK25 JSONs and four JDK17 JSONs; final build/consumer receipt `results/hillclimb/final-verification.md`. | Accepted: all 20 JDK25 cells and all 6 JDK17 decisive cells favored linear scan; retained footprint unchanged. |
| C3 | Node degree 8 (15 keys), binary search unchanged. | `36bb5e688f92a93754bd8bd01af03e2838888138de08d2f65f89b4491874f960` (candidate; reverted) | JDK 25 `-Psimd`, LongBTreeTest 6 + VectorSearchTest 2 passed; runtime fingerprints verified. | Guarded screen + JOL: `results/hillclimb/c3-degree8-summary.md`, `c3-degree8-footprint.md`, raw JSONs/CSV. | Rejected: slower nearly everywhere; range +62–72%, retained bytes +13–15%, height +1–2 levels. |
| C4 | Node degree 32 (63 keys), binary search unchanged. | `4155dceeaba5cf77589b2bdd4d0cf98b0d1bc92327530d6bbdea5fc5d0b9ef60` (candidate; reverted) | JDK 25 `-Psimd`, LongBTreeTest 6 + VectorSearchTest 2 passed; runtime fingerprints verified. | Guarded screen + JOL: `results/hillclimb/c4-degree32-summary.md`, `c4-degree32-footprint.md`, raw JSONs/CSV. | Rejected for default: range/overwrite and footprint improved, but churn +59–67% and small fullWidth point reads regressed. Mixed workload tradeoff. |

Limit: four meaningful candidates total, then final paired confirmation or reject all. Stop a line after two rejected changes. Keep source simple; no public tuning knobs without demonstrated need.

### C3 — degree 8

- Isolated variable: `LongBTree.DEGREE=8`, binary search unchanged. Compare against frozen degree-16 binary source. Record JOL retained bytes and node/occupancy counts with identical keys in addition to the timing screen.
- Gate: targeted JDK 25 `-Psimd` LongBTree/Vector tests, guarded screen in both sizes and key shapes, with benchmark order opposite the previous C2 run (candidate first, then baseline). No significance claim from screening.
- Source SHA256: `36bb5e688f92a93754bd8bd01af03e2838888138de08d2f65f89b4491874f960`; compiled candidate tree/Node fingerprints are recorded in `harness-classpath-correction.md`. Correctness passed (6 LongBTree tests, 2 Vector tests, 0 failures).
- Guarded screen: candidate ran first (completed 15:43:49 UTC), then frozen baseline (15:44:18 UTC), exit 0. Raw JSONs `c3-degree8-candidate.json` and `c3-degree8-baseline.json`, logs `/tmp/hillclimb-c3-degree8-candidate.log` and `/tmp/hillclimb-c3-degree8-baseline.log`, paired summary `c3-degree8-summary.md`. Both arms had 20/20 finite rows and 40 expected per-trial tree/Node fingerprint lines.
- Deltas candidate vs baseline (dense/fullWidth): at 262,144 hits +11.4/+15.0%, misses +13.1/+12.5%, range64 +71.9/+62.3%, overwrite +13.5/+12.1%, churn +6.8/+12.2%. At 4,096 hits +16.4/+15.5%, misses +10.8/-5.1%, range +27.7/+37.6%, overwrite +16.1/-1.0%, churn +5.4/+8.1%. Reject degree 8; these are screening estimates only.
- JOL retained footprint and node geometry: `c3-degree8-footprint.md` / `.csv`, same fixture keys, 4,096 and 262,144, dense/fullWidth. Degree 8 used 13.1–13.5% more bytes at 4K and 14.9–15.2% more at 262K. Nodes roughly doubled, active keys/node halved, height grew 3→4 and 4→6. JOL used HotspotUnsafe estimates because attach instrumentation was unavailable. Rejected patch is preserved as `c3-degree8-rejected.patch`.

### C4 — degree 32

- Isolated variable: `LongBTree.DEGREE=32`, binary search unchanged. Compare against the same frozen degree-16 binary source; measure retained bytes and node/occupancy counts. Screen after C3 is recorded, with baseline-first/candidate-second order.
- Source SHA256: `4155dceeaba5cf77589b2bdd4d0cf98b0d1bc92327530d6bbdea5fc5d0b9ef60`; targeted JDK 25 `-Psimd` correctness passed (6 LongBTree tests, 2 Vector tests, 0 failures).
- Guarded screen: baseline first (completed 15:46:48 UTC), candidate second (15:47:16 UTC), JDK 25 one-fork 1x250ms warmup/2x250ms measure, `-foe true`; 20/20 finite rows and 40 matching tree/Node fingerprint lines per arm. Raw `c4-degree32-baseline.json`/`candidate.json`, paired summary `c4-degree32-summary.md`, logs `/tmp/hillclimb-c4-degree32-baseline.log` and `/tmp/hillclimb-c4-degree32-candidate.log`.
- Deltas (dense/fullWidth): at 262,144 hit +0.9/-1.8%, miss -1.8/-0.9%, range -12.0/-8.0%, overwrite -1.7/-1.9%, churn +67.3/+59.1%. At 4,096 hit +3.1/+13.3%, miss +5.4/+18.8%, range -5.5/-10.4%, overwrite -11.2/-20.1%, churn +7.6/+10.1%. Mixed workload tradeoff; reject as default. No confirmation run.
- JOL retained footprint: `c4-degree32-footprint.md`/`.csv`; same key fixtures. Retained bytes fell 6.6–13.9% at 4K and 7.0–7.3% at 262K; nodes halved and height unchanged. JOL used HotspotUnsafe estimates without Instrumentation. Rejected source is preserved in `c4-degree32-rejected.patch`.

### C2 — linear lower-bound at fixed degree 16

- Hypothesis: nodes contain at most 31 keys; a linear lower-bound scan may reduce branch/search overhead for small packed nodes. JFR shows `Node.index(long)` dominates sampled execution but does not establish that linear search is faster.
- Isolated variable: search algorithm only. Degree remains 16; no hybrid threshold or node-size changes in this candidate.
- Correctness gate: `LongBTreeTest` and `VectorSearchTest` under JDK 25 `-Psimd`, then full paired screen with all five workloads, both sizes and key distributions. Reject if no broad signal; candidate may proceed to confirmation only if screen gives a coherent improvement without read/write/range regressions.
- Candidate source hash after final explanatory comment: `9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503`. Targeted correctness command passed on 2026-10-25, exit 0, 8 tests.
- Initial screen/confirmation are invalid and retained only for audit (see `results/hillclimb/harness-classpath-correction.md`). Corrected screen ran baseline first, then candidate, using `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar`, `-foe true`, per-fork tree/Node fingerprints and finite JSON row validation. Baseline log `/tmp/hillclimb-c2-corrected-baseline.log` completed 15:40:52 UTC; candidate log `/tmp/hillclimb-c2-corrected-candidate.log` completed 15:41:19 UTC. Each arm produced 20/20 finite rows and 40 fingerprint lines (two class fingerprints for each of 20 trials). Candidate JDK25 hashes: tree `735203836860d90afcfebeb8dbec729a8fbfe316b83cbe2a42444ca08aeede76`, Node `6f183f62dab68d6454553cd591bcc6384db0c9569f8805b9b16135f88ab10b5b`; baseline hashes remain listed in the correction report. Raw JSONs `c2-corrected-baseline.json`, `c2-corrected-candidate.json`; paired summary `c2-corrected-summary.md`.
- Corrected JDK 25 confirmation and JDK 17 decisive subset are in `results/hillclimb/c2-confirm/README.md`; raw JSONs are adjacent. JDK 25 alternated order by size×distribution block (C/B, B/C, C/B, B/C), with 2 forks, 3×1s warmup and 5×1s measure. Every arm produced five finite rows and 20 expected runtime class receipts. All 20 JMH 99.9% confidence intervals favored linear scan and did not overlap. At 262,144, gains ranged from 23.1–31.9% across all five workloads; at 4,096, gains ranged from 22.6–57.6%. JDK 17 hit/range/churn at 262,144 favored the candidate by 26.7–35.6% on both key distributions. JOL retained bytes, object counts and node counts matched exactly for both distributions. JOL sizes are HotspotUnsafe estimates because Instrumentation attach was unavailable. Accept C2; no change to degree or public API.
- Campaign conclusion: keep C2 linear search only. C1 dispatch split, C3 degree 8, and C4 degree 32 were rejected. Do not describe the hill-climb results as improvements to other workloads or concurrent maps.

## Round three — packed long key/value node storage

One candidate is authorized: retain degree 16 and linear search, but place keys in
`long[0..MAX)` and values in `long[MAX..2*MAX)` of one `long[2*MAX]` allocation per node.
The hypothesis is fewer per-node arrays/headers and potentially improved access locality;
co-location does not establish a cache-miss reduction, and the value slot remains 248 bytes
after its key slot. No other node geometry, search algorithm, API, StringBTree, or
ConcurrentLongBTree change is in scope.

The frozen control is the `LongBTree` source from merged `origin/main` commit
`603b6f5b0ebcdddf5a5dab15b9a3f0f71c704340`, SHA-256
`9f7dac57003bd771dbbca65fa2c22c0584dee9dbc52836cec1470a8dd6fb5503`. The existing
`Round2BaselineLongBTree` is the renamed independent test class; its reverse-normalized
source must match that checksum before timing. Strategy callers are limited to
`keys[0..count)`; trailing array positions are unspecified storage. `VectorSearch` uses
`count`, not array length. Compressed nodes inherit the base storage and need the same
value-offset treatment while preserving their suffix/prefix metadata and fallback behavior.

Astra's reviewed guidance: distinguish the allocation-count hypothesis from any unmeasured
latency mechanism; include compressed and custom/vector strategies in correctness checks;
verify keys-first active-prefix ordering, full-width updates/removals/ranges, and both
borrow directions, splits, merges and root collapse. A candidate proceeds only after
targeted tests, positive/negative per-fork runtime hash guards, a finite 40-row screen
(2 sizes × 2 key shapes × 5 operations × 2 implementations), and paired JOL data showing
lower retained bytes for identical fixtures. The screen was near-neutral, but the confirmed
4.2% footprint reduction justified a paired check: alternate arms, compare the same 20
size/key-shape/operation cells, and reject for observed material latency regressions. Run a
decisive JDK 17 subset if retention remains plausible. Do not treat overlapping intervals as
proof of equivalence.

**Accepted for the 4.2% measured retained-footprint reduction.** The 20-cell JDK 25
confirmation and three-cell JDK 17 subset found no >5% latency regression, while every
candidate/baseline interval overlapped. This does not demonstrate latency equivalence or a
speedup. Full correctness/build status remains pending after final integration with String
round two. Evidence and raw JSON links are in
[`results/hillclimb-round3/long/README.md`](results/hillclimb-round3/long/README.md).

## Experiment records

Append one section per baseline or candidate: source commit/hash; exact correctness command/result; exact JMH/JFR command, JDK/host/start time and raw/log paths; per-workload measurements and confidence intervals; memory/write/range effects; accept/reject/revert decision and rationale. Logs/JFR stay in `/tmp`; summarized JSON/CSV and this ledger stay tracked under `results/hillclimb/`.

### C1 — one-time scalar/custom search split in `getOrDefault`

- Candidate source SHA256: `936ca4653e43dd08d012a1a4be7618360a0c8bcd535138273b443d62df30d3ba`; temporary edit to `LongBTree.getOrDefault`, subsequently reverted. It branches once before traversal and calls `node.index(key)` for the default path, retaining the strategy overload for custom searches.
- Correctness: `JAVA_HOME=.../azul-25/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd -pl simd -am -Dtest=LongBTreeTest,VectorSearchTest -Dsurefire.failIfNoSpecifiedTests=false test`; exit 0, 8 tests, no failures. This was targeted test coverage, not full verification.
- The first JMH run is invalid and retained only for audit (see `results/hillclimb/harness-classpath-correction.md`). Corrected screen used `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar`, `-foe true`, per-fork class+Node path/hash guard and finite-row validation. Candidate arm ran first (log `/tmp/hillclimb-c1-corrected-candidate.log`, completed 15:38:31 UTC); frozen baseline ran second (log `/tmp/hillclimb-c1-corrected-baseline.log`, completed 15:39:03 UTC). Each emitted 20/20 finite rows; all per-trial tree/Node fingerprints matched. Raw JSON: `c1-corrected-candidate.json` and `c1-corrected-baseline.json`; paired summary: `c1-corrected-summary.md`.
- Corrected screen deltas (candidate vs baseline): at 262,144, hit -1.0% dense/-0.2% fullWidth; miss -0.8%/+2.6%; range64 -0.4%/-10.5%; overwrite +0.4%/+1.3%; churn -0.4%/-2.2%. At 4,096, hit -3.4%/+5.1%, miss +24.5%/-1.8%, range -0.1%/+0.7%, overwrite +32.4%/+3.7%, churn -0.1%/+1.7%. One fork/two short measurements are coarse, but no broad win appears and dense small miss/write are notably slower. Reject C1 without long confirmation; revert the production edit. No memory impact was measured; C1 changes traversal code only.

## Round 2 — linear-search LongBTree

The preceding C2 campaign has already changed the default to degree-16 linear
lower-bound search. Round 2 therefore uses feature commit `94adfd0e37a1168e80635a9124fac6f82bd8d1e5` as its current baseline, not the historical degree-16 binary-search source from `57ea447`. The frozen baseline clone, guarded selected-map screen, value-aware mutation checks, and candidate ledger are in [`results/hillclimb-round2/long/README.md`](results/hillclimb-round2/long/README.md).

At most three result-driven candidates are allowed: first a modest unrolled scan at fixed degree 16; then degree 8 or 32 with linear search only if evidence warrants the geometry interaction. No public tuning knobs or concurrency redesign are in scope. Candidate outcome, exact provenance, correctness and build receipts will be recorded in the round-two ledger.

C5, four-at-a-time linear scan at degree 16, was rejected after a guarded JDK 25
screen: 4,096-entry hit/miss/overwrite regressed 11–20% and range64 6.7–6.8%;
262,144-entry point/range results were mixed and near neutral. The 40-row
one-fork/two-measurement screen is exploratory, not confirmation. Targeted
correctness passed 9 tests; positive runtime fingerprints passed and a wrong
hash failed closed. The production source is restored and the rejected diff,
raw artifacts, hashes and precise limitations are recorded in the linked
round-two ledger.

C6, degree 8 with the same linear search, was also rejected. Its guarded JDK 25
screen regressed across every workload, including 24–29% slower point reads and
67–68% slower range64 at 262,144 entries. The exploratory screen is not
confirmation; its complete evidence and limitations are in the round-two
ledger. Production source has been restored to degree 16.

C7, degree 32 with linear search, improved 262,144-entry reads/ranges by
6–23% but regressed churn 89–93%; 4,096-entry point operations regressed
26–30% while range64 improved 14–17%. It is rejected as a mixed-workload
tradeoff. The 40-row exploratory screen and prior-round footprint limits are
documented in the round-two ledger. No round-two production optimization was
accepted; the default remains the 94adfd0 degree-16 linear implementation.
