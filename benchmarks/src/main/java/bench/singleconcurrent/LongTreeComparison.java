package bench.singleconcurrent;

import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import io.github.enderekici.packedbtree.LongBTree;
import java.io.InputStream;
import java.io.File;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

/** One-thread comparison of the mutable and concurrent primitive Long B-tree APIs. */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class LongTreeComparison {
  @Param({"mutable", "concurrent"}) public String arm;
  @Param({"262144"}) public int size;

  private LongMap map;
  private long[] keys;
  private long[] alternateKeys;
  private long[] hitQueries;
  private long[] misses;
  private long[] rangeFrom;
  private long[] rangeTo;
  private long[] valuesA;
  private long[] valuesB;
  private boolean[] alternatePresent;
  private boolean[] overwriteSide;
  private int[] order;
  private int[] rangeOrder;
  private int cursor;
  private int rangeCursor;
  private final SumConsumer timedRange = new SumConsumer();
  private final VerifyConsumer verifyRange = new VerifyConsumer();

  @Setup(Level.Trial)
  public void setup() throws Exception {
    if (Integer.bitCount(size) != 1 || size < 128) throw new IllegalArgumentException("bad size");
    if (arm.equals("mutable")) {
      guard(LongBTree.class, "outer");
      guard(Class.forName(LongBTree.class.getName() + "$Node"), "node");
      map = new MutableLong(new LongBTree());
    } else if (arm.equals("concurrent")) {
      guard(ConcurrentLongBTree.class, "outer");
      guard(Class.forName(ConcurrentLongBTree.class.getName() + "$Node"), "node");
      guard(Class.forName(ConcurrentLongBTree.class.getName() + "$State"), "state");
      map = new ConcurrentLong(new ConcurrentLongBTree());
    } else throw new IllegalArgumentException(arm);

    keys = new long[size]; alternateKeys = new long[size]; hitQueries = new long[size];
    misses = new long[size]; valuesA = new long[size]; valuesB = new long[size];
    alternatePresent = new boolean[size]; overwriteSide = new boolean[size];
    order = permutation(size, 99173);
    for (int i = 0; i < size; i++) {
      keys[i] = 2L * i + 1_000_000L;
      alternateKeys[i] = keys[i] + 1;
      hitQueries[i] = keys[i];
      misses[i] = keys[i] + 2L * size + 1;
      valuesA[i] = value(i, 0); valuesB[i] = value(i, 1);
    }
    int[] insertion = permutation(size, 7319);
    for (int rank : insertion) map.put(keys[rank], valuesA[rank]);
    rangeFrom = new long[size - 63]; rangeTo = new long[size - 63];
    for (int i = 0; i < size - 64; i++) {
      rangeFrom[i] = keys[i]; rangeTo[i] = keys[i + 64];
    }
    rangeFrom[size - 64] = keys[size - 64];
    rangeTo[size - 64] = keys[size - 1] + 2;
    rangeOrder = permutation(rangeFrom.length, 91733);
    cursor = rangeCursor = 0;
    checkPositiveFixtures();
    System.out.println("GUARD type=long arm=" + arm + " size=" + size + " keysSeed=7319 querySeed=99173");
  }

  private long value(int rank, int side) { return ((long) rank << 1) | side; }
  private int next() { int i = order[cursor++]; if (cursor == size) cursor = 0; return i; }

  @Benchmark public long hit() { int i = next(); return map.get(hitQueries[i], Long.MIN_VALUE); }
  @Benchmark public long miss() { int i = next(); return map.get(misses[i], Long.MIN_VALUE); }

  @Benchmark public void overwrite() {
    int i = next();
    overwriteSide[i] = !overwriteSide[i];
    map.put(keys[i], overwriteSide[i] ? valuesB[i] : valuesA[i]);
  }

  @Benchmark public void churn() {
    int i = next();
    if (alternatePresent[i]) {
      if (!map.remove(alternateKeys[i])) throw new AssertionError("missing alternate");
      map.put(keys[i], valuesA[i]);
    } else {
      if (!map.remove(keys[i])) throw new AssertionError("missing primary");
      map.put(alternateKeys[i], valuesB[i]);
    }
    alternatePresent[i] = !alternatePresent[i];
  }

  @Benchmark public long range64() {
    int i = rangeOrder[rangeCursor++];
    if (rangeCursor == rangeOrder.length) rangeCursor = 0;
    timedRange.reset();
    map.forEach(rangeFrom[i], rangeTo[i], timedRange);
    if (timedRange.entries != 64) throw new AssertionError("range count " + timedRange.entries);
    return timedRange.sum;
  }

  private void checkPositiveFixtures() {
    for (int start : new int[] {0, size / 2, size - 64}) {
      verifyRange.reset();
      map.forEach(rangeFrom[start], rangeTo[start], verifyRange);
      long expected = 0;
      for (int i = start; i < start + 64; i++) expected += valuesA[i];
      if (verifyRange.entries != 64 || verifyRange.sum != expected)
        throw new AssertionError("fixture range " + start);
    }
    if (map.get(keys[0], Long.MIN_VALUE) != valuesA[0]
        || map.get(keys[size / 2], Long.MIN_VALUE) != valuesA[size / 2]
        || map.get(keys[size - 1], Long.MIN_VALUE) != valuesA[size - 1])
      throw new AssertionError("point fixture failed");
    if (map.get(misses[0], Long.MIN_VALUE) != Long.MIN_VALUE) throw new AssertionError("miss fixture");
    map.validate();
  }

  @TearDown(Level.Trial)
  public void check() {
    if (map.size() != size) throw new AssertionError("cardinality " + map.size());
    for (int i = 0; i < size; i++) {
      long key = alternatePresent[i] ? alternateKeys[i] : keys[i];
      long got = map.get(key, Long.MIN_VALUE);
      if (got == Long.MIN_VALUE) throw new AssertionError("missing rank " + i);
      long expected = alternatePresent[i] || overwriteSide[i] ? valuesB[i] : valuesA[i];
      if (got != expected)
        throw new AssertionError("invalid value rank " + i + " got=" + got);
      long absent = alternatePresent[i] ? keys[i] : alternateKeys[i];
      if (map.get(absent, Long.MIN_VALUE) != Long.MIN_VALUE)
        throw new AssertionError("churn left duplicate rank " + i);
    }
    map.validate();
  }

  private void guard(Class<?> type, String part) throws Exception {
    String key = "tree.guard." + arm + ".long." + part;
    String expected = System.getProperty(key);
    if (expected == null || !expected.matches("[0-9a-f]{64}")) throw new IllegalStateException("missing " + key);
    URL location = type.getProtectionDomain().getCodeSource().getLocation();
    String expectedLocation = System.getProperty("tree.guard." + arm + ".location");
    if (expectedLocation == null || !new File(location.toURI()).getCanonicalFile()
        .equals(new File(expectedLocation).getCanonicalFile()))
      throw new IllegalStateException("CodeSource mismatch " + type.getName() + " " + location);
    String resource = "/" + type.getName().replace('.', '/') + ".class";
    byte[] bytes;
    try (InputStream in = type.getResourceAsStream(resource)) { bytes = in.readAllBytes(); }
    String actual = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    System.out.println("CLASS-GUARD " + type.getName() + " codeSource=" + location + " sha256=" + actual);
    if (!expected.equals(actual)) throw new IllegalStateException("class guard mismatch " + type.getName());
  }

  private static String hex(byte[] bytes) {
    StringBuilder b = new StringBuilder(bytes.length * 2);
    for (byte x : bytes) b.append(String.format("%02x", x & 255));
    return b.toString();
  }
  private static int[] permutation(int n, long seed) {
    int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i;
    Random r = new Random(seed);
    for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1), x = a[i]; a[i] = a[j]; a[j] = x; }
    return a;
  }

  private static final class SumConsumer implements LongBTree.EntryConsumer,
      ConcurrentLongBTree.EntryConsumer {
    int entries; long sum;
    void reset() { entries = 0; sum = 0; }
    public void accept(long key, long value) { entries++; sum += value; }
  }
  private final class VerifyConsumer implements LongBTree.EntryConsumer,
      ConcurrentLongBTree.EntryConsumer {
    int entries; long sum; long previous;
    void reset() { entries = 0; sum = 0; previous = Long.MIN_VALUE; }
    public void accept(long key, long value) {
      if (entries > 0 && key <= previous) throw new AssertionError("range order");
      int rank = Math.toIntExact((key - 1_000_000L) / 2);
      if (rank < 0 || rank >= size || keys[rank] != key || valuesA[rank] != value)
        throw new AssertionError("range mapping");
      previous = key; entries++; sum += value;
    }
  }

  private interface LongMap {
    long get(long key, long fallback); void put(long key, long value); boolean remove(long key);
    int size(); void forEach(long from, long to, LongBTree.EntryConsumer consumer); void validate();
  }
  private record MutableLong(LongBTree tree) implements LongMap {
    public long get(long k, long d) { return tree.getOrDefault(k, d); }
    public void put(long k, long v) { tree.put(k, v); }
    public boolean remove(long k) { return tree.remove(k); }
    public int size() { return tree.size(); }
    public void forEach(long f, long t, LongBTree.EntryConsumer c) { tree.forEach(f, t, c); }
    public void validate() { tree.validate(); }
  }
  private record ConcurrentLong(ConcurrentLongBTree tree) implements LongMap {
    public long get(long k, long d) { return tree.getOrDefault(k, d); }
    public void put(long k, long v) { tree.put(k, v); }
    public boolean remove(long k) { return tree.remove(k); }
    public int size() { return tree.size(); }
    public void forEach(long f, long t, LongBTree.EntryConsumer c) {
      tree.forEach(f, t, (ConcurrentLongBTree.EntryConsumer) c);
    }
    public void validate() { tree.validate(); }
  }
}
