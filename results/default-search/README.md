# Default-search regression comparison

This isolated JMH run compares the current default `LongBTree` against the exact
pre-hook implementation. Each benchmark trial contains only its selected tree,
so JIT profiling does not mix custom-strategy and scalar tree types. Both use the
same ascending, evenly spaced signed-key fixture and fixed-seed random hit/miss
query array;
trial teardown checks every expected result and validates the tree.

| Size | Current default | Pre-hook control | Assessment |
| ---: | ---: | ---: | --- |
| 4,096 | 25.603 ± 4.651 ns/op | 20.201 ± 4.678 ns/op | Current mean +26.7%; intervals overlap |
| 262,144 | 74.946 ± 11.731 ns/op | 73.697 ± 5.792 ns/op | Intervals overlap; no resolved change |

Errors are JMH's 99.9% confidence intervals. The smaller-fixture point estimate is 26.7% higher on the current default path.
Intervals overlap at both sizes, so this run cannot establish a regression or rule
one out; it also does not establish equivalence. Treat it as a
single-machine, fixed-fixture check, not a broad performance claim.

The run used JDK 25.0.3 on an Apple M4 Pro, one thread, G1, 1 GiB heap, two forks,
three 1-second warmups, and five 1-second measurements per fork. Source HEAD was
`dcb5130afe5d8a162acd3de465026f93bfbd6933`; run started at 2026-10-25 14:53:14
UTC and completed successfully in about 65 seconds. Raw JSON is
`selected-default-control.json`. The console log was captured at
`/tmp/selected-default-control-jmh.log`.
