package io.github.enderekici.packedbtree.simd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.enderekici.packedbtree.LongBTree;
import io.github.enderekici.packedbtree.CompressedLongBTree;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void compressedRoutingUsesVectorFallbackForWideNodes() {
    VectorSearch vector = new VectorSearch();
    AtomicInteger fallbackCalls = new AtomicInteger();
    CompressedLongBTree tree = new CompressedLongBTree((keys, count, key) -> {
      fallbackCalls.incrementAndGet();
      return vector.lowerBound(keys, count, key);
    });
    TreeMap<Long, Long> expected = new TreeMap<>();
    for (int i = 0; i < 2_000; i++) {
      long key = 0x1234000000000000L + i;
      tree.put(key, ~key);
      expected.put(key, ~key);
    }
    tree.validate();
    for (long key : expected.keySet()) assertEquals(expected.get(key), tree.getOrDefault(key, 0));
    assertEquals(0, fallbackCalls.get(), "same-prefix nodes should use compact suffix routing");

    tree.put(Long.MIN_VALUE, 17);
    tree.put(Long.MAX_VALUE, 19);
    expected.put(Long.MIN_VALUE, 17L);
    expected.put(Long.MAX_VALUE, 19L);
    for (long key : expected.keySet()) assertEquals(expected.get(key), tree.getOrDefault(key, 0));
    assertTrue(fallbackCalls.get() > 0, "wide routing nodes should use the supplied vector search");
    tree.validate();
    for (int i = 0; i < 2_000; i += 3) {
      long key = 0x1234000000000000L + i;
      assertEquals(expected.containsKey(key), tree.remove(key));
      expected.remove(key);
    }
    tree.validate();
    assertEquals(expected.size(), tree.size());
  }
}
