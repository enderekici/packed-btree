# Compressed routing measurements

This directory holds raw JMH JSON, the console log, the environment record, and
retained-memory CSV for the scalar versus compressed routing comparison. The
JMH run uses 1,048,576 mappings and all three routing distributions; the JOL
comparison includes the pre-hook control, integrated scalar, and integrated
compressed trees at the same size. The scalar source is from commit `48c4f75`,
before the later packed mutable `LongBTree` layout; these memory rows do not
measure the current mutable default against compressed routing. See [the report](../../docs/compressed-routing.md)
for results and limitations.

The timing run found no compelling general lookup improvement: compressed
routing was 3–4% slower on sequential and full-width random keys, while the
common-prefix result was inconclusive. It retained about 17% more memory. Keep
this API optional and experimental.
