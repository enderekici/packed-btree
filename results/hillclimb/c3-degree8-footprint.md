# C3 degree-8 retained footprint

JOL `GraphLayout` measured the frozen degree-16 baseline and degree-8 candidate in the same JVM using identical `SearchScreenFixture` keys. A temporary `/tmp/DegreeFootprint.java` also traversed nodes to count active keys and height. Rows are in `c3-degree8-footprint.csv`.

Degree 8 used 13.1–13.5% more retained bytes at 4,096 entries and 14.9–15.2% more at 262,144. Node count roughly doubled, mean active keys per node halved, and tree height grew from 3 to 4 levels (4K) and 4 to 6 (262K). JOL could not attach Instrumentation and used HotspotUnsafe estimates, so exact object-size bytes carry that limitation.

The degree-8 screen was slower across nearly all operations and showed large range penalties. Reject this degree; no confirmation was run.
