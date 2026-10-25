# Compressed routing measurements

This directory holds the JMH JSON and retained-memory CSV for the scalar versus
compressed routing comparison. `memory.csv` records the three-way JOL results.
Run `bench.RoutingBenchmark` on the reserved
measurement host/slot with both `search` values and all `distribution` values.
Measure `btree` and `btreeCompressed` separately with `bench.MemoryFootprint`
for each distribution. Use fresh JVMs for memory runs and record the JDK, host,
heap, and exact commands in `docs/compressed-routing.md`.

Timed lookup results are pending the exclusive JMH slot.
