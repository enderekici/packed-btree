package io.github.enderekici.packedbtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ConcurrentStringBTreeTest {
  private static final Object ABSENT = new Object();

  @Test
  void sequentialDifferentialAndUtf16Boundaries() {
    List<String> special = List.of("", "\0", "\0\0", "a", "a\0", "aa", "\uD800",
        "\uDC00", "\uD800\uDC00", "\uDBFF\uDFFF", "\uFFFF", "\uFFFF\0");
    ConcurrentStringBTree<Object> actual = new ConcurrentStringBTree<>();
    TreeMap<String, Object> expected = new TreeMap<>();
    Object firstValue = new Object();
    String retainedKey = new String("aa");
    for (String key : special) {
      String stored = key.equals("aa") ? retainedKey : new String(key);
      Object value = key.equals("a") ? null : new Object();
      actual.put(stored, value);
      expected.put(key, value);
    }
    String equivalentKey = new String("aa");
    actual.put(equivalentKey, firstValue);
    expected.put("aa", firstValue);
    assertSame(retainedKey, onlyKey(actual, "aa", "ab"));
    assertNotSame(retainedKey, equivalentKey);
    assertNull(actual.getOrDefault("a", ABSENT));
    assertSame(ABSENT, actual.getOrDefault("missing", ABSENT));
    compare(actual, expected);

    for (int seed = 0; seed < 4; seed++) {
      Random random = new Random(seed);
      actual = new ConcurrentStringBTree<>();
      expected = new TreeMap<>();
      for (int operation = 0; operation < 6_000; operation++) {
        String key = randomKey(random);
        int choice = random.nextInt(5);
        switch (choice) {
          case 0, 1 -> {
            Object value = random.nextInt(5) == 0 ? null : new Value(random.nextInt(50));
            String stored = new String(key);
            actual.put(stored, value);
            expected.put(key, value);
          }
          case 2 -> {
            assertEquals(expected.containsKey(key), actual.remove(new String(key)));
            expected.remove(key);
          }
          case 3 -> {
            Object fallback = new Object();
            Object observed = actual.getOrDefault(new String(key), fallback);
            Object reference = expected.containsKey(key) ? expected.get(key) : fallback;
            assertSame(reference, observed);
          }
          default -> {
            String to = randomKey(random);
            assertRange(actual, expected, key, to);
          }
        }
        assertEquals(expected.size(), actual.size(), "cardinality at seed " + seed
            + " operation " + operation + " choice=" + choice + " key=" + printable(key)
            + " missing=" + missingKeys(actual, expected));
        if (operation % 200 == 0) compare(actual, expected);
      }
      compare(actual, expected);
    }
  }

  @Test
  void nullValidationAndCallbackReentryUseCompleteSnapshots() {
    ConcurrentStringBTree<Object> tree = new ConcurrentStringBTree<>();
    tree.put("a", null);
    tree.put("b", new Object());
    assertThrows(NullPointerException.class, () -> tree.getOrDefault(null, ABSENT));
    assertThrows(NullPointerException.class, () -> tree.put(null, "x"));
    assertThrows(NullPointerException.class, () -> tree.remove(null));
    assertThrows(NullPointerException.class, () -> tree.forEach(null, "z", (k, v) -> {}));
    assertThrows(NullPointerException.class, () -> tree.forEach("a", null, (k, v) -> {}));
    assertThrows(NullPointerException.class, () -> tree.forEach("a", "z", null));
    assertEquals(2, tree.size());
    assertNull(tree.getOrDefault("a", ABSENT));
    assertTrue(tree.remove("a"));
    assertFalse(tree.remove("a"));
    tree.put("a", null);

    List<String> outer = new ArrayList<>();
    List<String> nested = new ArrayList<>();
    tree.forEach("a", "z", (key, value) -> {
      outer.add(key);
      if (key.equals("a")) {
        tree.put("c", "new");
        tree.remove("b");
        tree.forEach("a", "z", (nestedKey, nestedValue) -> nested.add(nestedKey));
      }
    });
    assertEquals(List.of("a", "b"), outer);
    assertEquals(List.of("a", "c"), nested);
    assertEquals(2, tree.size());

    RuntimeException marker = new RuntimeException("callback");
    assertSame(marker, assertThrows(RuntimeException.class,
        () -> tree.forEach("a", "z", (key, value) -> { throw marker; })));
    tree.put("after-exception", "still-live");
    tree.validate();
  }

  @Test
  void splitBorrowMergeAndRootCollapseMatchTreeMap() {
    for (int count : List.of(30, 31, 32, 62, 63, 64)) {
      for (boolean descending : new boolean[] {false, true}) {
        ConcurrentStringBTree<Object> actual = new ConcurrentStringBTree<>();
        TreeMap<String, Object> expected = new TreeMap<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) keys.add(String.format("k%03d", i));
        Collections.shuffle(keys, new Random(count * (descending ? 91 : 17)));
        for (String key : keys) {
          Object value = new Value(key.hashCode());
          actual.put(key, value);
          expected.put(key, value);
        }
        compare(actual, expected);
        keys.sort(descending ? Collections.reverseOrder() : String::compareTo);
        for (String key : keys) {
          assertEquals(expected.containsKey(key), actual.remove(key));
          expected.remove(key);
          actual.validate();
          assertEquals(expected.size(), actual.size());
        }
        assertEquals(0, actual.size());
        actual.validate();
      }
    }
  }

  @Test
  void pausedRangeKeepsKeyAndValueReferencesAcrossDeleteAndRebuild() throws Exception {
    ConcurrentStringBTree<Object> tree = new ConcurrentStringBTree<>();
    List<Entry> expected = new ArrayList<>();
    for (int i = 0; i < 2_000; i++) {
      String key = new String(String.format("k%04d", i));
      Object value = i % 11 == 0 ? null : new Value(i);
      tree.put(key, value);
      expected.add(new Entry(key, value));
    }

    CountDownLatch callbackEntered = new CountDownLatch(1);
    CountDownLatch resumeCallback = new CountDownLatch(1);
    List<Entry> observed = Collections.synchronizedList(new ArrayList<>());
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> traversal = pool.submit(() -> tree.forEach("", "l", (key, value) -> {
        if (key.equals("k0000")) {
          callbackEntered.countDown();
          try {
            if (!resumeCallback.await(10, TimeUnit.SECONDS)) {
              throw new AssertionError("Range callback was not resumed");
            }
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
          }
        }
        observed.add(new Entry(key, value));
      }));
      assertTrue(callbackEntered.await(10, TimeUnit.SECONDS));
      for (int i = 0; i < 2_000; i++) assertTrue(tree.remove(String.format("k%04d", i)));
      assertEquals(0, tree.size());
      for (int i = 0; i < 256; i++) tree.put("z" + String.format("%04d", i), new Value(-i));
      resumeCallback.countDown();
      traversal.get(10, TimeUnit.SECONDS);
      assertEntryReferences(expected, observed);
      assertEquals(256, tree.size());
      tree.validate();
    } finally {
      resumeCallback.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @Test
  void overlappingHistoriesPassOracleAndOracleRejectsImpossibleControls() throws Exception {
    for (int repetition = 0; repetition < 40; repetition++) {
      ConcurrentStringBTree<Object> tree = new ConcurrentStringBTree<>();
      TreeMap<String, Object> initialState = initial("a", null, "b", new Value(0));
      for (int i = 0; i < 30; i++) initialState.put(String.format("k%02d", i), new Value(i));
      initialState.forEach(tree::put);
      AtomicInteger clock = new AtomicInteger();
      List<Recorded> history = Collections.synchronizedList(new ArrayList<>());
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(3);
      List<Future<?>> futures = new ArrayList<>();
      List<List<Operation>> operations = List.of(
          List.of(Operation.put("a", new Value(1)), Operation.range("", "z"),
              Operation.size()),
          List.of(Operation.get("a", ABSENT), Operation.remove("b")),
          List.of(Operation.put("b", null), Operation.get("b", ABSENT)));
      try {
        for (List<Operation> workerOperations : operations) {
          futures.add(pool.submit(() -> {
            start.await();
            for (Operation operation : workerOperations) {
              invoke(tree, operation, history, clock);
            }
            return null;
          }));
        }
        start.countDown();
        for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        assertTrue(isLinearizable(history, initialState));
      } finally {
        start.countDown();
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
      }
    }

    Object legalValue = new Value(7);
    List<Recorded> legal = List.of(
        recorded(Operation.put("a", legalValue), 0, 1, null),
        recorded(Operation.get("a", ABSENT), 2, 3, legalValue));
    assertTrue(isLinearizable(legal, new TreeMap<>()));

    TreeMap<String, Object> oneKey = initial("a", null);
    List<Recorded> impossibleRemoves = List.of(
        recorded(Operation.remove("a"), 0, 1, true),
        recorded(Operation.remove("a"), 2, 3, true));
    assertFalse(isLinearizable(impossibleRemoves, oneKey));

    Object zero = new Value(0);
    Object one = new Value(1);
    TreeMap<String, Object> twoKeys = initial("a", zero, "b", zero);
    List<Recorded> impossibleRange = List.of(
        recorded(Operation.put("a", one), 0, 1, null),
        recorded(Operation.put("b", one), 2, 3, null),
        recorded(Operation.range("a", "c"), 4, 5,
            List.of(new Entry("a", zero), new Entry("b", one))));
    assertFalse(isLinearizable(impossibleRange, twoKeys));
  }

  private static void invoke(ConcurrentStringBTree<Object> tree, Operation operation,
      List<Recorded> history, AtomicInteger clock) {
    int start = clock.getAndIncrement();
    Object result = switch (operation.kind) {
      case PUT -> { tree.put(operation.key, operation.value); yield null; }
      case REMOVE -> tree.remove(operation.key);
      case GET -> tree.getOrDefault(operation.key, operation.fallback);
      case SIZE -> tree.size();
      case RANGE -> range(tree, operation.key, operation.to);
    };
    int end = clock.getAndIncrement();
    history.add(recorded(operation, start, end, result));
  }

  private static boolean isLinearizable(List<Recorded> history, TreeMap<String, Object> initial) {
    return search(history, new boolean[history.size()], new TreeMap<>(initial));
  }

  private static boolean search(List<Recorded> history, boolean[] used,
      TreeMap<String, Object> model) {
    boolean allUsed = true;
    for (boolean item : used) allUsed &= item;
    if (allUsed) return true;
    for (int i = 0; i < history.size(); i++) {
      if (used[i] || hasUnfinishedPredecessor(history, used, i)) continue;
      Recorded item = history.get(i);
      if (!matches(item, model)) continue;
      TreeMap<String, Object> next = new TreeMap<>(model);
      apply(item.operation, next);
      used[i] = true;
      if (search(history, used, next)) return true;
      used[i] = false;
    }
    return false;
  }

  private static boolean hasUnfinishedPredecessor(List<Recorded> history, boolean[] used,
      int index) {
    Recorded candidate = history.get(index);
    for (int i = 0; i < history.size(); i++) {
      if (!used[i] && i != index && history.get(i).end < candidate.start) return true;
    }
    return false;
  }

  private static boolean matches(Recorded item, TreeMap<String, Object> model) {
    Operation operation = item.operation;
    return switch (operation.kind) {
      case PUT -> true;
      case GET -> item.result == (model.containsKey(operation.key)
          ? model.get(operation.key) : operation.fallback);
      case REMOVE -> item.result.equals(model.containsKey(operation.key));
      case SIZE -> item.result.equals(model.size());
      case RANGE -> item.result.equals(referenceRange(model, operation.key, operation.to));
    };
  }

  private static void apply(Operation operation, TreeMap<String, Object> model) {
    if (operation.kind == Kind.PUT) model.put(operation.key, operation.value);
    if (operation.kind == Kind.REMOVE) model.remove(operation.key);
  }

  private static List<Entry> range(ConcurrentStringBTree<Object> tree, String from, String to) {
    List<Entry> entries = new ArrayList<>();
    tree.forEach(from, to, (key, value) -> entries.add(new Entry(key, value)));
    return entries;
  }

  private static List<Entry> referenceRange(TreeMap<String, Object> tree,
      String from, String to) {
    if (from.compareTo(to) >= 0) return List.of();
    List<Entry> entries = new ArrayList<>();
    tree.subMap(from, true, to, false).forEach((key, value) -> entries.add(new Entry(key, value)));
    return entries;
  }

  private static void assertRange(ConcurrentStringBTree<Object> actual,
      TreeMap<String, Object> expected, String from, String to) {
    List<Entry> reference = referenceRange(expected, from, to);
    List<Entry> observed = range(actual, from, to);
    if (reference.size() != observed.size()) actual.validate();
    assertEquals(reference.size(), observed.size(),
        () -> "range count mismatch from=" + printable(from) + " to=" + printable(to)
            + " missing=" + reference.stream().map(Entry::key).filter(key -> observed.stream()
                .noneMatch(entry -> entry.key.equals(key))).toList()
            + " extra=" + observed.stream().map(Entry::key).filter(key -> reference.stream()
                .noneMatch(entry -> entry.key.equals(key))).toList()
            + " lookup=" + actual.getOrDefault("prefix-53", ABSENT)
            + " size=" + actual.size() + "/" + expected.size());
    for (int i = 0; i < reference.size(); i++) {
      assertEquals(reference.get(i), observed.get(i),
          "range entry " + i + " from=" + printable(from) + " to=" + printable(to));
    }
  }

  private static String printable(String value) {
    return value.chars().mapToObj(codeUnit -> String.format("%04x", codeUnit))
        .collect(java.util.stream.Collectors.joining());
  }

  private static void compare(ConcurrentStringBTree<Object> actual,
      TreeMap<String, Object> expected) {
    actual.validate();
    assertEquals(expected.size(), actual.size());
    String upper = expected.isEmpty() ? "\0" : expected.lastKey() + "\0";
    assertEquals(referenceRange(expected, "", upper), range(actual, "", upper));
    for (Map.Entry<String, Object> entry : expected.entrySet()) {
      assertSame(entry.getValue(), actual.getOrDefault(new String(entry.getKey()), ABSENT));
    }
  }

  private static String onlyKey(ConcurrentStringBTree<Object> tree, String from, String to) {
    List<String> found = new ArrayList<>();
    tree.forEach(from, to, (key, value) -> found.add(key));
    assertEquals(1, found.size());
    return found.get(0);
  }

  private static List<String> missingKeys(ConcurrentStringBTree<Object> actual,
      TreeMap<String, Object> expected) {
    List<String> missing = new ArrayList<>();
    for (String key : expected.keySet()) {
      if (actual.getOrDefault(key, ABSENT) == ABSENT) missing.add(key);
    }
    return missing;
  }

  private static void assertEntryReferences(List<Entry> expected, List<Entry> observed) {
    assertEquals(expected.size(), observed.size());
    for (int i = 0; i < expected.size(); i++) {
      assertSame(expected.get(i).key, observed.get(i).key);
      assertSame(expected.get(i).value, observed.get(i).value);
    }
  }

  private static String randomKey(Random random) {
    return switch (random.nextInt(5)) {
      case 0 -> String.format("ascii-%04d", random.nextInt(256));
      case 1 -> "\uD800" + (char) random.nextInt(256);
      case 2 -> "\uFFFF" + (char) random.nextInt(256);
      case 3 -> "prefix-" + random.nextInt(256);
      default -> String.valueOf((char) random.nextInt(0x10000));
    };
  }

  private static TreeMap<String, Object> initial(Object... keyValues) {
    TreeMap<String, Object> model = new TreeMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      model.put((String) keyValues[i], keyValues[i + 1]);
    }
    return model;
  }

  private static Recorded recorded(Operation operation, int start, int end, Object result) {
    return new Recorded(operation, start, end, result);
  }

  private enum Kind { GET, PUT, REMOVE, SIZE, RANGE }

  private record Operation(Kind kind, String key, String to, Object value, Object fallback) {
    static Operation get(String key, Object fallback) {
      return new Operation(Kind.GET, key, null, null, fallback);
    }
    static Operation put(String key, Object value) {
      return new Operation(Kind.PUT, key, null, value, null);
    }
    static Operation remove(String key) {
      return new Operation(Kind.REMOVE, key, null, null, null);
    }
    static Operation range(String from, String to) {
      return new Operation(Kind.RANGE, from, to, null, null);
    }
    static Operation size() {
      return new Operation(Kind.SIZE, null, null, null, null);
    }
  }

  private record Recorded(Operation operation, int start, int end, Object result) {}
  private record Entry(String key, Object value) {}
  private record Value(int value) {}
}
