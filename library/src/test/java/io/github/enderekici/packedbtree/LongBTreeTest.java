package io.github.enderekici.packedbtree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Contract and differential tests against the JDK ordered map. */
public final class LongBTreeTest {
  private static long operations;

  @Test
  void boundaryValues() {
    boundaries();
  }

  @Test
  void orderedInsertionDrainAndRefill() {
    for (int order = 0; order < 3; order++) ordered(order);
  }

  @Test
  void randomizedDenseKeys() {
    for (int seed = 0; seed < 12; seed++) differential(seed);
  }

  @Test
  void randomizedFullWidthKeys() {
    wideKeys();
  }

  @Test
  void oracleDetectsKnownMismatch() {
    positiveControl();
  }

  private static void boundaries() {
    LongBTree actual = new LongBTree();
    TreeMap<Long, Long> expected = new TreeMap<>();
    long[] keys = {Long.MIN_VALUE, Long.MAX_VALUE, 0, -1, 1};
    for (long key : keys) {
      actual.put(key, key);
      expected.put(key, key);
    }
    compare(actual, expected);
    for (long key : keys) {
      if (actual.getOrDefault(key, 123) != key) throw new AssertionError("Boundary value");
      actual.put(key, ~key);
      expected.put(key, ~key);
    }
    compare(actual, expected);
    for (long from : keys) for (long to : keys) range(actual, expected, from, to);
    for (long key : keys) {
      if (!actual.remove(key) || actual.remove(key)) throw new AssertionError("Remove contract");
      expected.remove(key);
      compare(actual, expected);
    }
    actual.put(3, Long.MIN_VALUE);
    if (actual.getOrDefault(3, 42) != Long.MIN_VALUE
        || actual.getOrDefault(4, 42) != 42) throw new AssertionError("Fallback");
  }

  private static void ordered(int order) {
    List<Long> keys = new ArrayList<>();
    for (long i = -10000; i < 10000; i++) keys.add(i);
    if (order == 1) Collections.reverse(keys);
    if (order == 2) Collections.shuffle(keys, new Random(19));
    LongBTree actual = new LongBTree();
    TreeMap<Long, Long> expected = new TreeMap<>();
    for (long key : keys) {
      actual.put(key, key * 7);
      expected.put(key, key * 7);
      operations++;
    }
    compare(actual, expected);
    Collections.shuffle(keys, new Random(order));
    for (long key : keys) {
      if (!actual.remove(key)) throw new AssertionError("Existing deletion");
      expected.remove(key);
      operations++;
      if (expected.size() % 97 == 0) compare(actual, expected);
    }
    compare(actual, expected);
    actual.put(17, 3);
    expected.put(17L, 3L);
    compare(actual, expected);
  }

  private static void differential(int seed) {
    Random random = new Random(seed);
    LongBTree actual = new LongBTree();
    TreeMap<Long, Long> expected = new TreeMap<>();
    for (int i = 0; i < 200000; i++) {
      long key = random.nextInt(16000) - 8000;
      switch (random.nextInt(5)) {
        case 0, 1 -> {
          long value = random.nextLong();
          actual.put(key, value);
          expected.put(key, value);
        }
        case 2 -> {
          if (actual.remove(key) != (expected.remove(key) != null)) {
            throw new AssertionError("Deletion seed=" + seed + " operation=" + i);
          }
        }
        case 3 -> {
          long fallback = random.nextLong();
          if (actual.getOrDefault(key, fallback) != expected.getOrDefault(key, fallback)) {
            throw new AssertionError("Lookup");
          }
        }
        default -> range(actual, expected, key, key + random.nextInt(128));
      }
      operations++;
      if (i % 1000 == 0) compare(actual, expected);
    }
    compare(actual, expected);
  }

  private static void compare(LongBTree actual, TreeMap<Long, Long> expected) {
    actual.validate();
    if (actual.size() != expected.size()) throw new AssertionError("Size");
    List<String> entries = new ArrayList<>();
    actual.forEach(Long.MIN_VALUE, Long.MAX_VALUE, (k, v) -> entries.add(k + ":" + v));
    List<String> reference = new ArrayList<>();
    expected.headMap(Long.MAX_VALUE, false).forEach((k, v) -> reference.add(k + ":" + v));
    if (!entries.equals(reference)) throw new AssertionError("Full ordered contents");
    for (var entry : expected.entrySet()) {
      if (actual.getOrDefault(entry.getKey(), ~entry.getValue()) != entry.getValue()) {
        throw new AssertionError("Full lookup");
      }
    }
  }

  private static void range(LongBTree actual, TreeMap<Long, Long> expected, long from, long to) {
    List<String> found = new ArrayList<>();
    actual.forEach(from, to, (k, v) -> found.add(k + ":" + v));
    List<String> reference = new ArrayList<>();
    if (from < to) expected.subMap(from, true, to, false)
        .forEach((k, v) -> reference.add(k + ":" + v));
    if (!found.equals(reference)) throw new AssertionError("Range " + from + ".." + to);
  }

  private static void positiveControl() {
    LongBTree actual = new LongBTree();
    TreeMap<Long, Long> wrong = new TreeMap<>();
    actual.put(1, 2);
    wrong.put(1L, 3L);
    boolean detected = false;
    try {
      compare(actual, wrong);
    } catch (AssertionError expected) {
      detected = true;
    }
    if (!detected) throw new AssertionError("Oracle did not detect a known mismatch");
  }

  private static void wideKeys() {
    Random random = new Random(9573);
    long[] keys = new long[24000];
    for (int i = 0; i < keys.length; i++) keys[i] = random.nextLong();
    LongBTree actual = new LongBTree();
    TreeMap<Long, Long> expected = new TreeMap<>();
    for (int i = 0; i < 300000; i++) {
      long key = keys[random.nextInt(keys.length)];
      if (random.nextBoolean()) {
        long value = random.nextLong();
        actual.put(key, value);
        expected.put(key, value);
      } else if (actual.remove(key) != (expected.remove(key) != null)) {
        throw new AssertionError("Wide-key deletion");
      }
      if (i % 1000 == 0) {
        compare(actual, expected);
        long other = keys[random.nextInt(keys.length)];
        range(actual, expected, Math.min(key, other), Math.max(key, other));
      }
      operations++;
    }
  }

}
