package bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.enderekici.packedbtree.LongBTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class Round2LongBTreeDifferentialTest {
  @Test
  void matchesFrozenBaselineAndTreeMapAcrossSignedLongMutationsAndRanges() {
    LongBTree candidate = new LongBTree();
    Round2BaselineLongBTree baseline = new Round2BaselineLongBTree();
    TreeMap<Long, Long> expected = new TreeMap<>();
    RangeCollector collector = new RangeCollector();
    Random random = new Random(0x4c6f6e6742547265L);
    long[] keys = keyPool(random);
    int half = keys.length / 2;

    for (int step = 0; step < 4_096; step++) {
      int operation = step % 4;
      int group = step / 4;
      long key = keys[group % keys.length];
      switch (operation) {
        case 0 -> {
          long value = random.nextLong();
          candidate.put(key, value);
          baseline.put(key, value);
          expected.put(key, value);
        }
        case 1 -> {
          long fallback = random.nextLong();
          long value = expected.getOrDefault(key, fallback);
          assertEquals(value, candidate.getOrDefault(key, fallback));
          assertEquals(value, baseline.getOrDefault(key, fallback));
        }
        case 2 -> {
          long otherKey = keys[(group + half) % keys.length];
          long value = random.nextLong();
          candidate.put(otherKey, value);
          baseline.put(otherKey, value);
          expected.put(otherKey, value);
        }
        case 3 -> {
          assertTrue(expected.containsKey(key));
          assertTrue(candidate.remove(key));
          assertTrue(baseline.remove(key));
          expected.remove(key);
        }
        default -> throw new AssertionError("Unreachable operation");
      }

      long query = random.nextLong();
      long fallback = random.nextLong();
      long expectedValue = expected.getOrDefault(query, fallback);
      assertEquals(expectedValue, candidate.getOrDefault(query, fallback));
      assertEquals(expectedValue, baseline.getOrDefault(query, fallback));

      if (step % 128 == 0) {
        assertTreesMatch(candidate, baseline, expected);
        long first = random.nextLong();
        long second = random.nextLong();
        long from = Math.min(first, second);
        long to = Math.max(first, second);
        assertRangeMatches(candidate, baseline, expected, collector, from, to);
      }
    }
    assertTreesMatch(candidate, baseline, expected);
    assertRangeMatches(candidate, baseline, expected, collector, Long.MIN_VALUE, Long.MAX_VALUE);
  }

  private static long[] keyPool(Random random) {
    long[] keys = new long[2_048];
    long[] edges = {
        Long.MIN_VALUE, Long.MIN_VALUE + 1, -1, 0, 1, Long.MAX_VALUE - 1, Long.MAX_VALUE};
    System.arraycopy(edges, 0, keys, 0, edges.length);
    java.util.HashSet<Long> used = new java.util.HashSet<>();
    for (long edge : edges) used.add(edge);
    for (int i = edges.length; i < keys.length; i++) {
      long key;
      do {
        key = random.nextLong();
      } while (!used.add(key));
      keys[i] = key;
    }
    return keys;
  }

  private static void assertTreesMatch(
      LongBTree candidate, Round2BaselineLongBTree baseline, TreeMap<Long, Long> expected) {
    assertEquals(expected.size(), candidate.size());
    assertEquals(expected.size(), baseline.size());
    candidate.validate();
    baseline.validate();
    for (Map.Entry<Long, Long> entry : expected.entrySet()) {
      long key = entry.getKey();
      long value = entry.getValue();
      assertEquals(value, candidate.getOrDefault(key, ~value));
      assertEquals(value, baseline.getOrDefault(key, ~value));
    }
  }

  private static void assertRangeMatches(
      LongBTree candidate,
      Round2BaselineLongBTree baseline,
      TreeMap<Long, Long> expected,
      RangeCollector collector,
      long from,
      long to) {
    collector.entries.clear();
    candidate.forEach(from, to, collector);
    List<Map.Entry<Long, Long>> candidateEntries = List.copyOf(collector.entries);
    collector.entries.clear();
    baseline.forEach(from, to, collector);
    List<Map.Entry<Long, Long>> baselineEntries = List.copyOf(collector.entries);
    List<Map.Entry<Long, Long>> expectedEntries = new ArrayList<>();
    if (from < to) {
      for (Map.Entry<Long, Long> entry : expected.subMap(from, true, to, false).entrySet()) {
        expectedEntries.add(Map.entry(entry.getKey(), entry.getValue()));
      }
    }
    assertEquals(expectedEntries, candidateEntries);
    assertEquals(expectedEntries, baselineEntries);
  }

  private static final class RangeCollector implements LongBTree.EntryConsumer,
      Round2BaselineLongBTree.EntryConsumer {
    private final List<Map.Entry<Long, Long>> entries = new ArrayList<>();

    @Override
    public void accept(long key, long value) {
      entries.add(Map.entry(key, value));
    }
  }
}
