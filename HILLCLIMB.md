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
| C2 | Search algorithm only, fixed degree 16: linear scan vs binary lower-bound. | `86a7e108eeccbe515a8e79249c2e82607720ea7c2d40d83a3b07b381d8dbf10e` (temporary candidate; reverted) | Targeted tests passed, but initial JMH did not load candidate bytecode. | Previous screen and confirm retained but invalid; see `results/hillclimb/harness-classpath-correction.md`. Correct run pending. | No performance decision. |
| C3 | One evidence-led follow-up within scope (for example String compare duplication) only if remaining profile justifies it. | pending | pending | pending | pending |

Limit: four meaningful candidates total, then final paired confirmation or reject all. Stop a line after two rejected changes. Keep source simple; no public tuning knobs without demonstrated need.

### C2 — linear lower-bound at fixed degree 16 (initial JMH invalidated)

- Hypothesis: nodes contain at most 31 keys; a linear lower-bound scan may reduce branch/search overhead for small packed nodes. JFR shows `Node.index(long)` dominates sampled execution but does not establish that linear search is faster.
- Isolated variable: search algorithm only. Degree remains 16; no hybrid threshold or node-size changes in this candidate.
- Correctness gate: `LongBTreeTest` and `VectorSearchTest` under JDK 25 `-Psimd`, then full paired screen with all five workloads, both sizes and key distributions. Reject if no broad signal; candidate may proceed to confirmation only if screen gives a coherent improvement without read/write/range regressions.
- Candidate source hash: `86a7e108eeccbe515a8e79249c2e82607720ea7c2d40d83a3b07b381d8dbf10e`. Targeted correctness command passed on 2026-10-25 at 15:20 UTC, exit 0, 8 tests.
- Initial screen and confirmation values previously reported here are invalid because the JMH runtime classpath omitted `library/target/classes`; the stale shaded benchmark jar supplied baseline production bytecode to both arms. Preserve all JSON/logs for audit only. See `results/hillclimb/harness-classpath-correction.md`; rerun C2 with runtime class fingerprints after C1.

## Experiment records

Append one section per baseline or candidate: source commit/hash; exact correctness command/result; exact JMH/JFR command, JDK/host/start time and raw/log paths; per-workload measurements and confidence intervals; memory/write/range effects; accept/reject/revert decision and rationale. Logs/JFR stay in `/tmp`; summarized JSON/CSV and this ledger stay tracked under `results/hillclimb/`.

### C1 — one-time scalar/custom search split in `getOrDefault`

- Candidate source SHA256: `936ca4653e43dd08d012a1a4be7618360a0c8bcd535138273b443d62df30d3ba`; temporary edit to `LongBTree.getOrDefault`, subsequently reverted. It branches once before traversal and calls `node.index(key)` for the default path, retaining the strategy overload for custom searches.
- Correctness: `JAVA_HOME=.../azul-25/Contents/Home ./mvnw --settings /tmp/packed-btree-maven-settings.xml -Psimd -pl simd -am -Dtest=LongBTreeTest,VectorSearchTest -Dsurefire.failIfNoSpecifiedTests=false test`; exit 0, 8 tests, no failures. This was targeted test coverage, not full verification.
- The first JMH run is invalid and retained only for audit (see `results/hillclimb/harness-classpath-correction.md`). Corrected screen used `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar`, `-foe true`, per-fork class+Node path/hash guard and finite-row validation. Candidate arm ran first (log `/tmp/hillclimb-c1-corrected-candidate.log`, completed 15:38:31 UTC); frozen baseline ran second (log `/tmp/hillclimb-c1-corrected-baseline.log`, completed 15:39:03 UTC). Each emitted 20/20 finite rows; all per-trial tree/Node fingerprints matched. Raw JSON: `c1-corrected-candidate.json` and `c1-corrected-baseline.json`; paired summary: `c1-corrected-summary.md`.
- Corrected screen deltas (candidate vs baseline): at 262,144, hit -1.0% dense/-0.2% fullWidth; miss -0.8%/+2.6%; range64 -0.4%/-10.5%; overwrite +0.4%/+1.3%; churn -0.4%/-2.2%. At 4,096, hit -3.4%/+5.1%, miss +24.5%/-1.8%, range -0.1%/+0.7%, overwrite +32.4%/+3.7%, churn -0.1%/+1.7%. One fork/two short measurements are coarse, but no broad win appears and dense small miss/write are notably slower. Reject C1 without long confirmation; revert the production edit. No memory impact was measured; C1 changes traversal code only.
