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
- Fresh paired baseline needed before candidate work: broad point/range/write screen and JFR profile under the campaign harness. The Mac mini is a shared developer host (root observed unrelated kubectl/Codex CPU usage); alternate paired runs and record this noise limitation.

## Candidates and decisions

| ID | Candidate / hypothesis | Source hash | Correctness gate | Benchmark / evidence | Decision and reason |
| --- | --- | --- | --- | --- | --- |
| C1 | Default scalar dispatch: remove avoidable nullable strategy dispatch from the default hot path if JFR shows cost; preserve custom/compressed dispatch. | pending | pending | pending | pending |
| C2 | Node degree/search thresholds: evaluate degree 8/16/32 (15/31/63 keys) and binary/linear/hybrid search only if baseline profile supports it. | pending | pending | pending | pending |
| C3 | One evidence-led follow-up within scope (for example String compare duplication) only if remaining profile justifies it. | pending | pending | pending | pending |

Limit: four meaningful candidates total, then final paired confirmation or reject all. Stop a line after two rejected changes. Keep source simple; no public tuning knobs without demonstrated need.

## Experiment records

Append one section per baseline or candidate: source commit/hash; exact correctness command/result; exact JMH/JFR command, JDK/host/start time and raw/log paths; per-workload measurements and confidence intervals; memory/write/range effects; accept/reject/revert decision and rationale. Logs/JFR stay in `/tmp`; summarized JSON/CSV and this ledger stay tracked under `results/hillclimb/`.
