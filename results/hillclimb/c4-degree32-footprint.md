# C4 degree-32 retained footprint

JOL `GraphLayout` measured frozen degree-16 baseline and degree-32 candidate with identical `SearchScreenFixture` keys; a temporary `/tmp/DegreeFootprint.java` also counted nodes, active keys/node and height. Data is in `c4-degree32-footprint.csv`.

Degree 32 used 6.6–13.9% fewer retained bytes at 4,096 and 7.0–7.3% fewer at 262,144. Node counts halved, mean active keys/node doubled, and height was unchanged (3 at 4K, 4 at 262K). JOL could not attach Instrumentation and used HotspotUnsafe estimates, so exact object-size bytes carry that limitation.

The screen improved range and overwrite but worsened small fullWidth hit/miss and made churn 59–67% slower at both sizes. This is a mixed workload tradeoff; do not use it as the default without a product decision. No confirmation was run.
