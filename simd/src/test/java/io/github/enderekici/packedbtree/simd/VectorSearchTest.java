package io.github.enderekici.packedbtree.simd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.enderekici.packedbtree.LongBTree;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class VectorSearchTest {
  @Test
  void differentialFullWidthRandomMutationsAndBoundaries() {
    LongBTree scalar = new LongBTree();
    LongBTree vector = new LongBTree(new VectorSearch());
    TreeMap<Long, Long> expected = new TreeMap<>();
    long[] edges = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1, 0, 1,
        Long.MAX_VALUE - 1, Long.MAX_VALUE};
    for (long key : edges) {
      scalar.put(key, key ^ 0x55aa55aa55aa55aaL);
      vector.put(key, key ^ 0x55aa55aa55aa55aaL);
      expected.put(key, key ^ 0x55aa55aa55aa55aaL);
    }
    Random random = new Random(719331L);
    for (int i = 0; i < 20_000; i++) {
      long key = random.nextLong();
      if ((i & 3) == 0) {
        assertEquals(expected.containsKey(key), scalar.remove(key));
        assertEquals(expected.containsKey(key), vector.remove(key));
        expected.remove(key);
      } else {
        long value = random.nextLong();
        scalar.put(key, value);
        vector.put(key, value);
        expected.put(key, value);
      }
      assertEquals(expected.size(), scalar.size());
      assertEquals(expected.size(), vector.size());
      assertEquals(scalar.getOrDefault(key, 17), vector.getOrDefault(key, 17));
      if (i % 127 == 0) {
        scalar.validate();
        vector.validate();
        long probe = random.nextLong();
        assertEquals(expected.getOrDefault(probe, 99L), vector.getOrDefault(probe, 99L));
      }
    }
    assertEquals(expected.get(Long.MIN_VALUE), vector.getOrDefault(Long.MIN_VALUE, 0));
    assertEquals(expected.get(Long.MAX_VALUE), vector.getOrDefault(Long.MAX_VALUE, 0));
    assertFalse(vector.remove(42));
  }
}
