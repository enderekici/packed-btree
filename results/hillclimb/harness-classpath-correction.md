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
