# JMH classpath correction

On 2026-10-25, the C1 and C2 candidate tests compiled production code into `library/target/classes`, but all candidate JMH commands used `simd/target/test-classes:benchmarks/target/benchmarks.jar`. The shaded jar is timestamped 2026-10-25 14:39 UTC; the library candidate class was timestamped 15:19 UTC. `javap -classpath benchmarks/target/benchmarks.jar -c -p io.github.enderekici.packedbtree.LongBTree` showed the default `getOrDefault` still invokes `Node.index(SearchStrategy,long)`. Therefore the JMH JVM loaded baseline jar code for `io.github...LongBTree` in both benchmark arms. The two-arm timing differences were noise and do not measure C1 or C2.

Preserved but invalid artifacts:

- C1 screen: `c1-screen.json`, `c1-screen.md`; log `/tmp/hillclimb-c1-screen.log`.
- C2 screen: `c2-linear-screen.json`, `c2-linear-screen.md`; log `/tmp/hillclimb-c2-linear-screen.log`.
- C2 2-fork confirm: `c2-linear-confirm.json`, `c2-linear-confirm.md`; log `/tmp/hillclimb-c2-linear-confirm.log`.

Corrected runtime classpath must place `library/target/classes` before the shaded jar, and the baseline test-only clone must resolve from `simd/target/test-classes`. Both arms will verify and print their loaded class code-source plus SHA-256 of class bytes, compared with expected hashes passed as properties. Candidate and baseline bytecode fingerprints must be checked with `javap` before any corrected screen. The corrected runs will alternate selected-map order explicitly where practical.

## Runtime guard evidence

The test-only search harness now checks, in each JMH fork's trial setup, the `CodeSource` directory and SHA-256 of the loaded tree and nested Node class resources. It fails on missing or wrong expected values. The absolute resource path is built from the class binary name, including `$Node`.

- Correct smoke classpath: `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar`.
- Candidate C1 JDK 25 class hashes: `LongBTree` `382ff9d6b3be74d68a8de7befedeaf103dad23063ce047ed9766997e78b0c318`; `LongBTree$Node` `cef81047868cf07dfca733d4112a1d272997d4846f231925cf406453f24febb0`.
- Frozen baseline JDK 25 class hashes: `CurrentBaselineLongBTree` `857602ebb4df3eb7247c8d93a4d4772f614f771ac1f44e5b49e5287ccaf4ffd5`; nested Node `4d06e5a71c69a5a099361c2828891d0ce119213c957cfaebb21a72c7831d935f`.
- Positive guarded smoke: `/tmp/hillclimb-fingerprint-smoke-foe.log` and `.json`; both expected code-source paths and all four hashes matched, 2 rows/finite scores, exit 0. It used `-foe true`.
- Negative guarded smoke: `/tmp/hillclimb-guard-negative-foe.log`; candidate tree hash intentionally set to `0000`. Setup logged `Unexpected loaded class` before measurement and JMH `-foe true` exited 1. This confirms the guard detects a mismatch.
- `javap -c -p` on the C1 candidate class shows `getOrDefault` branches on `searchStrategy`, invokes `Node.index(long)` in the default loop, and uses `Node.index(SearchStrategy,long)` only in the custom branch. The old shaded JAR bytecode invokes the strategy overload unconditionally.

Every corrected timing run must use `-foe true`, provide expected fingerprint values to forked JVMs, and validate the expected number of finite JSON result rows. The C1 screen will run candidate then baseline separately; C2 will reverse that order. Raw invalid JSON is retained and not rewritten.

## Corrected C2 screen receipt

- Order: baseline first, candidate second; each JDK 25 arm used `library/target/classes:simd/target/test-classes:benchmarks/target/benchmarks.jar`, one fork, 1x250ms warmup/2x250ms measurement, and `-foe true`.
- Baseline finished 15:40:52 UTC; candidate finished 15:41:19 UTC. Logs: `/tmp/hillclimb-c2-corrected-baseline.log`, `/tmp/hillclimb-c2-corrected-candidate.log`.
- Each raw JSON has 20 finite rows. Each arm logged 40 expected fingerprint lines (tree and Node for each trial); no `<failure>` marker.
- Linear candidate tree SHA-256: `735203836860d90afcfebeb8dbec729a8fbfe316b83cbe2a42444ca08aeede76`; Node: `6f183f62dab68d6454553cd591bcc6384db0c9569f8805b9b16135f88ab10b5b`. Baseline tree/Node hashes are above.
- `javap -c -p 'io.github.enderekici.packedbtree.LongBTree$Node'` showed the candidate's `index(long)` uses `iinc` and loops across keys; baseline clone bytecode shows the binary lower-bound `middle=(low+high)>>>1` and low/high updates.
- Candidate results are captured in `c2-corrected-summary.md`. This remains a short screen only; the large gains require replicated confirmation before any claim.

## Corrected C3 degree-8 screen receipt

- Candidate ran first, baseline second. Both arms used the corrected classpath, one fork, 1x250ms warmup/2x250ms measurement and `-foe true`.
- Each arm produced 20 finite rows and 40 matching fingerprint lines. Candidate log `/tmp/hillclimb-c3-degree8-candidate.log` completed 15:43:49 UTC; baseline log `/tmp/hillclimb-c3-degree8-baseline.log` completed 15:44:18 UTC.
- Candidate source SHA-256: `36bb5e688f92a93754bd8bd01af03e2838888138de08d2f65f89b4491874f960`; tree fingerprint `f2b91268716491a03d67b747a2f4da42d5e82e11a25c5fbea92747874ddf5724`; Node `824ddb5017dbe75850016a2740323e50f50c98fcb6700d704f785782f19b7459`.
- Raw JSON: `c3-degree8-candidate.json`, `c3-degree8-baseline.json`; summary `c3-degree8-summary.md`. JOL node/occupancy comparison pending.
- JOL comparison used identical key fixtures and produced `c3-degree8-footprint.csv` / `.md`. Instrumentation attach was unavailable; JOL used HotspotUnsafe estimates.
