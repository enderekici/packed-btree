package io.github.enderekici.packedbtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ConcurrentLongBTreeTest {
  @Test
  void sequentialDifferentialAndLongBoundaries() {
    ConcurrentLongBTree actual = new ConcurrentLongBTree();
    TreeMap<Long, Long> expected = new TreeMap<>();
    long[] boundaries = {Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 1};
    for (long key : boundaries) {
      actual.put(key, ~key);
      expected.put(key, ~key);
    }
    for (int i = 0; i < 20_000; i++) {
      long key = (i * 7919L) % 8192 - 4096;
      if ((i % 3) == 0) {
        actual.remove(key);
        expected.remove(key);
      } else {
        actual.put(key, i * 17L);
        expected.put(key, i * 17L);
      }
      assertEquals(expected.getOrDefault(key, Long.MIN_VALUE),
          actual.getOrDefault(key, Long.MIN_VALUE));
    }
    assertEquals(expected.size(), actual.size());
    actual.validate();
    List<String> found = new ArrayList<>();
    actual.forEach(Long.MIN_VALUE, Long.MAX_VALUE, (key, value) -> found.add(key + ":" + value));
    List<String> ordered = new ArrayList<>();
    expected.headMap(Long.MAX_VALUE, false)
        .forEach((key, value) -> ordered.add(key + ":" + value));
    assertEquals(ordered, found);
    assertEquals(~Long.MAX_VALUE, actual.getOrDefault(Long.MAX_VALUE, 0));
  }

  @Test
  void concurrentHistoriesAreLinearizableAndCheckerHasNegativeControl() throws Exception {
    for (int repetition = 0; repetition < 40; repetition++) {
      ConcurrentLongBTree tree = new ConcurrentLongBTree();
      Map<Long, Long> initial = new TreeMap<>();
      for (long key = 0; key < 64; key++) {
        tree.put(key, key * 10);
        initial.put(key, key * 10);
      }
      AtomicInteger clock = new AtomicInteger();
      List<Recorded> history = java.util.Collections.synchronizedList(new ArrayList<>());
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(3);
      List<Future<?>> futures = new ArrayList<>();
      try {
        for (int worker = 0; worker < 3; worker++) {
          final int id = worker;
          futures.add(pool.submit(() -> {
            start.await();
            Operation first = switch (id) {
              case 0 -> Operation.put(31, 311);
              case 1 -> Operation.put(31, 312);
              default -> Operation.get(31);
            };
            Operation second = switch (id) {
              case 0 -> Operation.range(28, 37);
              case 1 -> Operation.remove(32);
              default -> Operation.size();
            };
            invoke(tree, first, history, clock);
            invoke(tree, second, history, clock);
            return null;
          }));
        }
        start.countDown();
        for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        assertTrue(isLinearizable(history, initial));
      } finally {
        start.countDown();
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
      }
    }
    List<Recorded> legal = List.of(
        new Recorded(Operation.put(1, 10), 0, 1, null),
        new Recorded(Operation.get(1), 2, 3, 10L));
    assertTrue(isLinearizable(legal, Map.of()));
    List<Recorded> impossible = List.of(
        new Recorded(Operation.remove(1), 0, 1, 1L),
        new Recorded(Operation.remove(1), 2, 3, 1L));
    assertFalse(isLinearizable(impossible, Map.of(1L, 10L)));
    Map<Long, Long> twoKeys = Map.of(30L, 0L, 31L, 0L);
    List<Recorded> mixedRange = List.of(
        new Recorded(Operation.put(30, 1), 0, 1, null),
        new Recorded(Operation.put(31, 1), 2, 3, null),
        new Recorded(Operation.range(30, 32), 4, 5, "30:0,31:1"));
    assertFalse(isLinearizable(mixedRange, twoKeys));
  }

  @Test
  void simultaneousSplitDeleteReadersAndSnapshotRanges() throws Exception {
    ConcurrentLongBTree tree = new ConcurrentLongBTree();
    for (int i = 0; i < 8_000; i++) tree.put(i, i * 3L);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(6);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int writer = 0; writer < 3; writer++) {
        final int lane = writer;
        futures.add(pool.submit(() -> {
          start.await();
          for (int i = 0; i < 12_000; i++) {
            long key = 8_000L + lane * 12_000L + i;
            tree.put(key, ~key);
            if ((i & 1) == 0) tree.remove(key);
            tree.put(i % 8_000, key);
          }
          return null;
        }));
      }
      for (int reader = 0; reader < 3; reader++) {
        final int lane = reader;
        futures.add(pool.submit(() -> {
          start.await();
          for (int i = 0; i < 6_000; i++) {
            long key = (i * 97L + lane) % (8_000 - 128);
            assertKnownValue(key, tree.getOrDefault(key, -1));
            List<LongEntry> entries = new ArrayList<>();
            tree.forEach(key, key + 128, (found, value) -> entries.add(new LongEntry(found, value)));
            assertEquals(128, entries.size());
            for (int j = 0; j < entries.size(); j++) {
              LongEntry entry = entries.get(j);
              assertEquals(key + j, entry.key);
              assertKnownValue(entry.key, entry.value);
            }
          }
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) future.get(60, TimeUnit.SECONDS);
    } finally {
      start.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
    tree.validate();
    assertEquals(8_000 + 3 * 6_000, tree.size());

    ConcurrentLongBTree callbackTree = new ConcurrentLongBTree();
    for (int i = 0; i < 10; i++) callbackTree.put(i, i);
    List<Long> snapshot = new ArrayList<>();
    callbackTree.forEach(0, 10, (key, value) -> {
      snapshot.add(key);
      if (key == 0) {
        callbackTree.put(20, 20);
        assertEquals(20, callbackTree.getOrDefault(20, -1));
      }
    });
    assertEquals(10, snapshot.size());
    assertEquals(9, snapshot.get(9));
    List<Long> nested = new ArrayList<>();
    callbackTree.forEach(0, 10, (key, value) -> {
      if (key == 0) {
        callbackTree.forEach(0, 10, (nestedKey, nestedValue) -> nested.add(nestedKey));
        callbackTree.remove(1);
      }
    });
    assertEquals(10, nested.size());
    assertEquals(10, callbackTree.size());
  }

  @Test
  void pausedRangeKeepsOldRootAcrossSplitBorrowMergeAndRootCollapse() throws Exception {
    ConcurrentLongBTree tree = new ConcurrentLongBTree();
    List<String> before = new ArrayList<>();
    for (int i = 0; i < 2_000; i++) {
      tree.put(i, i * 5L);
      before.add(i + ":" + i * 5L);
    }
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch resume = new CountDownLatch(1);
    List<String> observed = java.util.Collections.synchronizedList(new ArrayList<>());
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> traversal = pool.submit(() -> tree.forEach(0, 2_000, (key, value) -> {
      if (key == 0) {
        entered.countDown();
        try {
          if (!resume.await(10, TimeUnit.SECONDS)) throw new AssertionError("Range not resumed");
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          throw new AssertionError(error);
        }
      }
      observed.add(key + ":" + value);
    }));
    assertTrue(entered.await(10, TimeUnit.SECONDS));
    for (int i = 0; i < 1_000; i++) {
      assertTrue(tree.remove(i));
      assertTrue(tree.remove(1_999 - i));
    }
    assertEquals(0, tree.size());
    for (int i = 4_000; i < 6_000; i++) tree.put(i, ~i);
    resume.countDown();
    traversal.get();
    pool.shutdown();
    assertEquals(before, observed);
    assertEquals(2_000, tree.size());
    tree.validate();
  }

  private static void invoke(ConcurrentLongBTree tree, Operation operation,
      List<Recorded> history, AtomicInteger clock) {
    int start = clock.getAndIncrement();
    Object result = switch (operation.kind) {
      case PUT -> { tree.put(operation.key, operation.value); yield null; }
      case REMOVE -> tree.remove(operation.key) ? 1L : 0L;
      case GET -> tree.getOrDefault(operation.key, -99);
      case SIZE -> tree.size();
      case RANGE -> {
        List<String> entries = new ArrayList<>();
        tree.forEach(operation.key, operation.to, (key, value) -> entries.add(key + ":" + value));
        yield String.join(",", entries);
      }
    };
    int end = clock.getAndIncrement();
    history.add(new Recorded(operation, start, end, result));
  }

  private static boolean isLinearizable(List<Recorded> history, Map<Long, Long> initial) {
    return search(history, new boolean[history.size()], new TreeMap<>(initial));
  }

  private static boolean search(List<Recorded> history, boolean[] used, Map<Long, Long> model) {
    boolean all = true;
    for (boolean item : used) all &= item;
    if (all) return true;
    for (int i = 0; i < history.size(); i++) {
      if (used[i] || hasUnfinishedPredecessor(history, used, i)) continue;
      Recorded item = history.get(i);
      Long old = model.get(item.operation.key);
      if (!matches(item, old, model)) continue;
      Map<Long, Long> next = new TreeMap<>(model);
      apply(item.operation, next);
      used[i] = true;
      if (search(history, used, next)) return true;
      used[i] = false;
    }
    return false;
  }

  private static boolean hasUnfinishedPredecessor(List<Recorded> history, boolean[] used, int index) {
    Recorded candidate = history.get(index);
    for (int i = 0; i < history.size(); i++) {
      if (!used[i] && i != index && history.get(i).end < candidate.start) return true;
    }
    return false;
  }

  private static boolean matches(Recorded item, Long old, Map<Long, Long> model) {
    return switch (item.operation.kind) {
      case PUT -> true;
      case GET -> item.result.equals(old == null ? -99L : old);
      case REMOVE -> item.result.equals(old == null ? 0L : 1L);
      case SIZE -> item.result.equals(model.size());
      case RANGE -> item.result.equals(rangeResult(model, item.operation.key, item.operation.to));
    };
  }

  private static String rangeResult(Map<Long, Long> model, long from, long to) {
    StringBuilder result = new StringBuilder();
    if (from >= to) return "";
    new TreeMap<>(model).subMap(from, true, to, false).forEach((key, value) -> {
      if (result.length() > 0) result.append(',');
      result.append(key).append(':').append(value);
    });
    return result.toString();
  }

  private static void assertKnownValue(long key, long observed) {
    if (observed == key * 3) return;
    for (int lane = 0; lane < 3; lane++) {
      if (observed == 8_000L + lane * 12_000L + key) return;
      if (key < 4_000 && observed == 16_000L + lane * 12_000L + key) return;
    }
    throw new AssertionError("Unexpected value " + observed + " for key " + key);
  }

  private static void apply(Operation operation, Map<Long, Long> model) {
    if (operation.kind == Kind.PUT) model.put(operation.key, operation.value);
    if (operation.kind == Kind.REMOVE) model.remove(operation.key);
  }

  private enum Kind { GET, PUT, REMOVE, SIZE, RANGE }
  private record Operation(Kind kind, long key, long value, long to) {
    static Operation get(long key) { return new Operation(Kind.GET, key, 0, 0); }
    static Operation put(long key, long value) { return new Operation(Kind.PUT, key, value, 0); }
    static Operation remove(long key) { return new Operation(Kind.REMOVE, key, 0, 0); }
    static Operation size() { return new Operation(Kind.SIZE, 0, 0, 0); }
    static Operation range(long from, long to) { return new Operation(Kind.RANGE, from, 0, to); }
  }
  private record Recorded(Operation operation, int start, int end, Object result) {}
  private record LongEntry(long key, long value) {}
}
