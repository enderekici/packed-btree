# Compressed routing measurements

`memory.csv` preserves historical track measurements and adds an integrated
comparison at 1,048,576 mappings. The integrated rows compare the exact pre-hook
scalar control, current default scalar tree, and compressed tree, each in a fresh
JDK 25 JVM using the same `RoutingKeys` fixture and `GraphLayout` probe. The
current default retained the same measured bytes as the pre-hook control in all
three tested distributions; compressed routing retained more because it adds
per-node suffix arrays and metadata. See `docs/compressed-routing.md` for the
values and environment.

Timed lookup results are pending the exclusive JMH slot. Use
`bench.RoutingBenchmark` with both search modes and all three distributions.
Measure `btree` and `btreeCompressed` separately with `bench.MemoryFootprint`
for each distribution, and record exact commands and host details.
