package io.github.enderekici.packedbtree;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
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
  void utf16BoundariesCrossInternalNodes() {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < 512; i++) {
      keys.add("\uFFFF" + (char) i);
      keys.add("\uD800" + (char) i);
      keys.add("prefix-" + (char) i + "-" + String.format("%04d", i));
      keys.add("ascii-" + String.format("%04d", i));
    }
    Collections.shuffle(keys, new Random(2026));
    StringBTree<Integer> tree = new StringBTree<>();
    TreeMap<String, Integer> expected = new TreeMap<>();
    for (int i = 0; i < keys.size(); i++) { tree.put(new String(keys.get(i)), i); expected.put(keys.get(i), i); }
    compare(tree, expected);
    for (int i = 0; i < keys.size(); i += 2) { String key = keys.get(i); assertTrue(tree.remove(key)); expected.remove(key); }
    compare(tree, expected);
  }

  @Test
  void rangesMatchTreeMapAtSmallSizesAndUnicodeBoundaries() {
    List<String> corpus = new ArrayList<>();
    Collections.addAll(corpus, "", "\0", "\0\0", "a", "a\0", "a\0\0", "aa",
        "prefix", "prefix\0", "\uD800", "\uD800\uDC00", "\uD800x", "\uDC00",
        "\uFFFF", "\uFFFF\uFFFF", "\uFFFF\uFFFFx");
    for (int i = 0; i < 512; i++) {
      corpus.add("\u0001" + (char) i + "-" + i);
      corpus.add("\uD800" + (char) i + "-" + i);
      corpus.add("common-prefix-" + String.format("%04x", i));
    }
    corpus.sort(String::compareTo);
    List<String> bounds = new ArrayList<>(corpus);
    bounds.add("\u0000\u0000\u0000");
    bounds.add("common-prefix-\u7fff");
    bounds.add("\uD800\uFFFF");
    bounds.add("\uFFFF\uFFFF\uFFFF");
    bounds.sort(String::compareTo);

    for (int size : new int[] {0, 1, 31, 32, 33, 127, 512}) {
      StringBTree<String> actual = new StringBTree<>();
      TreeMap<String, String> expected = new TreeMap<>();
      for (int i = 0; i < size; i++) {
        String key = new String(corpus.get(i).toCharArray());
        String value = i % 9 == 0 ? null : new String(("value-" + i).toCharArray());
        actual.put(key, value);
        expected.put(key, value);
      }
      for (int i = 0; i < bounds.size(); i += 7) {
        String from = bounds.get(i);
        for (int j = 0; j < bounds.size(); j += 13) {
          String to = bounds.get(j);
          assertRangeMatches(actual, expected, from, to, true);
        }
      }
      actual.validate();
    }
  }

  @Test
  void randomizedMutationsKeepRangesEquivalentToTreeMap() {
    Random random = new Random(0x5eedb7L);
    StringBTree<String> actual = new StringBTree<>();
    TreeMap<String, String> expected = new TreeMap<>();
    for (int i = 0; i < 12000; i++) {
      String key = boundaryKey(random.nextInt(768));
      switch (random.nextInt(4)) {
        case 0, 1 -> {
          String value = random.nextInt(7) == 0 ? null : "v" + random.nextInt(64);
          actual.put(new String(key.toCharArray()), value);
          expected.put(key, value);
        }
        case 2 -> {
          boolean existed = expected.containsKey(key);
          expected.remove(key);
          assertEquals(existed, actual.remove(key));
        }
        default -> {
          String other = boundaryKey(random.nextInt(768));
          String from = key.compareTo(other) <= 0 ? key : other;
          String to = key.compareTo(other) <= 0 ? other : key;
          assertRangeMatches(actual, expected, from, to);
        }
      }
      if (i % 127 == 0) actual.validate();
    }
    actual.validate();
    for (int i = 0; i < 200; i++) {
      String a = boundaryKey(random.nextInt(768));
      String b = boundaryKey(random.nextInt(768));
      assertRangeMatches(actual, expected, a, b);
    }
  }

  private static String boundaryKey(int value) {
    return switch (value % 8) {
      case 0 -> "\u0000" + (char) (value / 8);
      case 1 -> "\uD800" + (char) (value / 8);
      case 2 -> "\uDC00" + (char) (value / 8);
      case 3 -> "\uFFFF" + (char) (value / 8);
      case 4 -> "shared-" + String.format("%04d", value / 8);
      case 5 -> "a" + (char) (value / 8) + "\0";
      case 6 -> String.valueOf((char) (value / 8));
      default -> "z" + value;
    };
  }

  private static void assertRangeMatches(StringBTree<String> actual,
      TreeMap<String, String> expected, String from, String to) {
    assertRangeMatches(actual, expected, from, to, false);
  }

  private static void assertRangeMatches(StringBTree<String> actual,
      TreeMap<String, String> expected, String from, String to, boolean checkKeyIdentity) {
    List<String> actualKeys = new ArrayList<>();
    List<String> actualValues = new ArrayList<>();
    actual.forEach(from, to, (key, value) -> { actualKeys.add(key); actualValues.add(value); });
    List<String> expectedKeys = new ArrayList<>();
    List<String> expectedValues = new ArrayList<>();
    if (from.compareTo(to) < 0) {
      expected.subMap(from, true, to, false)
          .forEach((key, value) -> { expectedKeys.add(key); expectedValues.add(value); });
    }
    assertEquals(expectedKeys, actualKeys, "keys in [" + from + ", " + to + ")");
    assertEquals(expectedValues.size(), actualValues.size());
    for (int i = 0; i < expectedValues.size(); i++) {
      if (checkKeyIdentity) assertSame(expectedKeys.get(i), actualKeys.get(i), "key identity at " + i);
      assertSame(expectedValues.get(i), actualValues.get(i), "value identity at " + i);
    }
  }

  @Test
  void equalButDistinctBoundsPreserveStoredKeyIdentityAcrossOverwrite() {
    StringBTree<String> tree = new StringBTree<>();
    String storedKey = new String("same".toCharArray());
    String equalKey = new String("same".toCharArray());
    String firstValue = new String("first".toCharArray());
    String secondValue = new String("second".toCharArray());
    tree.put(storedKey, firstValue);
    tree.put(equalKey, secondValue);
    String[] seenKey = new String[1];
    String[] seenValue = new String[1];
    tree.forEach(new String("same".toCharArray()), new String("same\0".toCharArray()),
        (key, value) -> { seenKey[0] = key; seenValue[0] = value; });
    assertSame(storedKey, seenKey[0]);
    assertSame(secondValue, seenValue[0]);
  }

  @Test
  void rangeVisitorExceptionsPropagateUnchanged() throws ReflectiveOperationException {
    StringBTree<String> tree = new StringBTree<>();
    for (int i = 0; i < 512; i++) tree.put("common-prefix-" + String.format("%04d", i), "v" + i);
    Field rootField = StringBTree.class.getDeclaredField("root");
    rootField.setAccessible(true);
    Object root = rootField.get(tree);
    Field keysField = root.getClass().getDeclaredField("keys");
    Field countField = root.getClass().getDeclaredField("count");
    keysField.setAccessible(true);
    countField.setAccessible(true);
    int rootCount = countField.getInt(root);
    assertTrue(rootCount >= 3);
    String[] separators = (String[]) keysField.get(root);
    IllegalStateException failure = new IllegalStateException("visitor failure");
    int[] visited = {0};
    IllegalStateException observed = assertThrows(IllegalStateException.class,
        () -> tree.forEach(separators[0], separators[2], (key, value) -> {
          if (++visited[0] == 2) throw failure;
        }));
    assertEquals(2, visited[0]);
    assertSame(failure, observed);
  }

  @Test
  void validatorDetectsRetainedInactiveKey() throws ReflectiveOperationException {
    StringBTree<String> tree = new StringBTree<>();
    tree.validate();
    Field rootField = StringBTree.class.getDeclaredField("root");
    rootField.setAccessible(true);
    Object root = rootField.get(tree);
    Field keysField = root.getClass().getDeclaredField("keys");
    keysField.setAccessible(true);
    ((String[]) keysField.get(root))[0] = "retained";
    assertThrows(AssertionError.class, tree::validate);
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
          @SuppressWarnings("unchecked") V value = (V) (random.nextInt(8) == 0 ? null
              : longs ? Long.valueOf(random.nextLong()) : "v" + random.nextInt(100));
          tree.put(new String(key), value); expected.put(key, value);
        }
        case 2 -> { boolean existed = expected.containsKey(key); expected.remove(key); assertEquals(existed, tree.remove(new String(key))); }
        case 3 -> {
          @SuppressWarnings("unchecked") V fallback = (V) (longs ? Long.valueOf(-1) : "fallback");
          V expectedValue = expected.containsKey(key) ? expected.get(key) : fallback;
          assertEquals(expectedValue, tree.getOrDefault(new String(key), fallback));
        }
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

  private static String key(int n) {
    if (n == 0) return "";
    if (n == 1) return "\uFFFF\uFFFFx";
    if (n == 2) return "\uD800";
    if (n == 3) return "\uDC00";
    if (n < 1028) return String.valueOf((char) ((n - 4) * 61));
    return "prefix-" + (n % 7) + "-" + String.format("%05d", n);
  }

  private static <V> void compare(StringBTree<V> actual, TreeMap<String, V> expected) {
    actual.validate();
    assertEquals(expected.size(), actual.size());
    List<String> found = new ArrayList<>();
    String upper = expected.isEmpty() ? "\0" : expected.lastKey() + "\0";
    actual.forEach("", upper, (k, v) -> found.add(k + "=" + v));
    List<String> reference = new ArrayList<>();
    expected.forEach((k, v) -> reference.add(k + "=" + v));
    assertEquals(reference, found);
    expected.forEach((k, v) -> assertEquals(v, actual.getOrDefault(new String(k), null)));
  }
}
