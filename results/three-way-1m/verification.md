# Verification receipt

- Targeted harness compilation and tests (JDK 25, `-Psimd`, `-pl simd -am`,
  `LongBTreeTest,VectorSearchTest`): exit 0; 8 tests, no failures/errors/skips.
- Five-operation, three-arm smoke: exit 0; 15 finite JSON rows under
  `-foe true`; all per-fork class-location/hash guards matched, including
  original and optimized tree/node classes and bootstrap `TreeMap`/entry
  classes. Each trial completed full content/cardinality/invariant teardown.
- JDK 17 `clean verify`: exit 0; 15 library tests and 4 benchmark tests.
  Log: `/tmp/threeway-verify17.log`.
- JDK 25 `-Psimd clean verify`: exit 0; 15 library tests, 4 benchmark tests
  and 2 SIMD tests. Log: `/tmp/threeway-verify25-simd.log`.
- Full timed JMH comparison: exit 0; 15 rows, 10 measurement samples per row
  (five in each of two forks), no setup, teardown, class-fingerprint or
  finite-score failures. Launch time and exact command are recorded in
  `README.md` and `command.txt`; complete log: `/tmp/threeway-1m.log`.
- Focused range64 repeat: exit 0; 3 finite rows, eight one-second warmups, five
  one-second measurements, two forks. Complete log: `/tmp/threeway-range-confirm.log`;
  launch time, fingerprints and raw JSON are recorded in the report and
  `range-confirm-jdk25-m4pro.json`.

No production source or runtime dependency changed for this measurement. No
retained-memory comparison was run.
