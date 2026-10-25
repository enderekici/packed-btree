# Default search and node-size hill-climb ledger

## Scope and invariants

- Source baseline: `57ea447d94078b2a0872569b09bb88986b71f886` on `feature/parallel-integration`.
- Library/API invariants: Java 17 default consumer; signed full-width `long` ordering; String ordering/null-value and half-open range contracts; `SearchStrategy` / `CompressedLongBTree` composition; optional `VectorSearch`; no batched API or concurrency redesign.
- One candidate at a time. Preserve each source hash and result. Do not tune from a single point-hit case. Final verification is JDK 17 clean verify and JDK 25 `-Psimd` clean verify after the last source edit.
- Correctness gate for each candidate: existing differential/property tests plus targeted changed-path checks for default strategy, custom strategy, compressed/custom composition, VectorSearch, range, insert/overwrite/delete and full-width keys before timing.
- Timing gate: same selected-map harness, alternating baseline/candidate when practical; small and large sizes; dense and full-width keys; hits, misses, ranges, writes; two forks, at least three warmups and five one-second measurements for confirmation. Capture JFR and inspect execution call stacks before naming a hotspot. Record retained-memory and write/range guardrails. JMH intervals and workload limits remain explicit.

## Baseline

- Initial integrated source: `57ea447d94078b2a0872569b09bb88986b71f886`.
- Existing selected-map comparison source: `dcb5130afe5d8a162acd3de465026f93bfbd6933` (same library source; JMH harness only).
- Exact pre-hook control: `bench.PreHookLongBTree`; current map: `LongBTree`; one selected map per JMH trial/fork.
- Existing captured point-hit comparison: `results/default-search/selected-default-control.json` with interpretation in `results/default-search/README.md`. 4,096 mappings: current 25.603 ± 4.651 vs pre-hook 20.201 ± 4.678 ns/op (+26.7% point estimate); 262,144: 74.946 ± 11.731 vs 73.697 ± 5.792 (+1.7%). Intervals overlap at both sizes; it neither establishes a regression nor rules one out. This narrow result motivates a call-stack profile, not a performance claim.
- Fresh paired baseline needed before candidate work: broad point/range/write screen and JFR profile under the campaign harness.

## Candidates and decisions

| ID | Candidate / hypothesis | Source hash | Correctness gate | Benchmark / evidence | Decision and reason |
| --- | --- | --- | --- | --- | --- |
| C1 | Default scalar dispatch: remove avoidable nullable strategy dispatch from the default hot path if JFR shows cost; preserve custom/compressed dispatch. | pending | pending | pending | pending |
| C2 | Node degree/search thresholds: evaluate degree 8/16/32 (15/31/63 keys) and binary/linear/hybrid search only if baseline profile supports it. | pending | pending | pending | pending |
| C3 | One evidence-led follow-up within scope (for example String compare duplication) only if remaining profile justifies it. | pending | pending | pending | pending |

Limit: four meaningful candidates total, then final paired confirmation or reject all. Stop a line after two rejected changes. Keep source simple; no public tuning knobs without demonstrated need.

## Experiment records

Append one section per baseline or candidate: source commit/hash; exact correctness command/result; exact JMH/JFR command, JDK/host/start time and raw/log paths; per-workload measurements and confidence intervals; memory/write/range effects; accept/reject/revert decision and rationale. Logs/JFR stay in `/tmp`; summarized JSON/CSV and this ledger stay tracked under `results/hillclimb/`.
