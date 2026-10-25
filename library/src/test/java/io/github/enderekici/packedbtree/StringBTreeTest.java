package io.github.enderekici.packedbtree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

final class StringBTreeTest {
  @Test
  void orderingNullsOverwriteAndIdentityIndependentLookup() {
    List<String> keys = new ArrayList<>(List.of("", "a", "aa", "a\0", "\0", "z", "é",
        "\uD800", "\uD83D\uDE00", "\uDBFF\uDFFF", "\uFFFF", "A"));
    StringBTree<String> tree = new StringBTree<>();
    TreeMap<String, String> expected = new TreeMap<>();
    for (String key : keys) { tree.put(key, key); expected.put(key, key); }
    tree.put(new String("aa"), "overwritten");
    expected.put("aa", "overwritten");
    tree.put("null-value", null);
    expected.put("null-value", null);
    assertNull(tree.getOrDefault(new String("null-value"), "fallback"));
    assertEquals("fallback", tree.getOrDefault("missing", "fallback"));
    assertThrows(NullPointerException.class, () -> tree.put(null, "x"));
    assertThrows(NullPointerException.class, () -> tree.getOrDefault(null, "x"));
    assertThrows(NullPointerException.class, () -> tree.remove(null));
    compare(tree, expected);
    for (String key : keys) assertEquals(expected.get(key), tree.getOrDefault(new String(key), "absent"));
  }

  @Test
  void randomizedStringValuesAndRanges() {
    for (int seed = 0; seed < 8; seed++) differential(seed, false);
  }

  @Test
  void randomizedBoxedLongValuesAndFullDrainRefill() {
    for (int seed = 0; seed < 8; seed++) differential(seed, true);
    StringBTree<Long> tree = new StringBTree<>();
    TreeMap<String, Long> expected = new TreeMap<>();
    for (int i = 0; i < 5000; i++) { String key = String.format("key-%05d", i); tree.put(key, (long) i); expected.put(key, (long) i); }
    List<String> order = new ArrayList<>(expected.keySet());
    Collections.shuffle(order, new Random(32));
    for (String key : order) { assertTrue(tree.remove(key)); expected.remove(key); }
    compare(tree, expected);
    for (int i = 0; i < 5000; i++) { String key = String.format("key-%05d", i); tree.put(key, (long) -i); expected.put(key, (long) -i); }
    compare(tree, expected);
  }

  private static <V> void differential(int seed, boolean longs) {
    Random random = new Random(seed);
    StringBTree<V> tree = new StringBTree<>();
    TreeMap<String, V> expected = new TreeMap<>();
    for (int i = 0; i < 25000; i++) {
      String key = key(random.nextInt(4000));
      switch (random.nextInt(5)) {
        case 0, 1 -> {
          @SuppressWarnings("unchecked") V value = (V) (longs ? Long.valueOf(random.nextLong()) : "v" + random.nextInt(100));
          tree.put(new String(key), value); expected.put(key, value);
        }
        case 2 -> { boolean existed = expected.containsKey(key); expected.remove(key); assertEquals(existed, tree.remove(new String(key))); }
        case 3 -> assertEquals(expected.getOrDefault(key, null), tree.getOrDefault(new String(key), null));
        default -> {
          String end = key(random.nextInt(4000));
          List<String> found = new ArrayList<>();
          tree.forEach(key, end, (k, v) -> found.add(k + "=" + v));
          List<String> reference = new ArrayList<>();
          if (key.compareTo(end) < 0) expected.subMap(key, true, end, false).forEach((k, v) -> reference.add(k + "=" + v));
          assertEquals(reference, found, "range at operation " + i);
        }
      }
      if (i % 250 == 0) compare(tree, expected);
    }
    compare(tree, expected);
  }

  private static String key(int n) { return "prefix-" + (n % 7) + "-" + String.format("%05d", n); }

  private static <V> void compare(StringBTree<V> actual, TreeMap<String, V> expected) {
    actual.validate();
    assertEquals(expected.size(), actual.size());
    List<String> found = new ArrayList<>();
    actual.forEach("", "\uFFFF\uFFFF", (k, v) -> found.add(k + "=" + v));
    List<String> reference = new ArrayList<>();
    expected.forEach((k, v) -> reference.add(k + "=" + v));
    assertEquals(reference, found);
    expected.forEach((k, v) -> assertEquals(v, actual.getOrDefault(new String(k), null)));
  }
}
