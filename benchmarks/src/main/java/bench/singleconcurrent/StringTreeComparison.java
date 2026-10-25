package bench.singleconcurrent;

import io.github.enderekici.packedbtree.ConcurrentStringBTree;
import io.github.enderekici.packedbtree.StringBTree;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import java.util.concurrent.TimeUnit;

/** One-thread comparison of mutable and concurrent String B-tree APIs. */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class StringTreeComparison {
  @Param({"mutable", "concurrent"}) public String arm;
  @Param({"262144"}) public int size;
  @Param({"random"}) public String shape;

  private StringMap map;
  private String[] keys;
  private String[] queries;
  private String[] alternateKeys;
  private String[] misses;
  private String[] rangeFrom;
  private String[] rangeTo;
  private Object[] valuesA;
  private Object[] valuesB;
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
    if (!shape.equals("random") && !shape.equals("prefix")) throw new IllegalArgumentException(shape);
    if (arm.equals("mutable")) {
      guard(StringBTree.class, "outer");
      guard(Class.forName(StringBTree.class.getName() + "$Node"), "node");
      map = new MutableString(new StringBTree<>());
    } else if (arm.equals("concurrent")) {
      guard(ConcurrentStringBTree.class, "outer");
      guard(Class.forName(ConcurrentStringBTree.class.getName() + "$Node"), "node");
      guard(Class.forName(ConcurrentStringBTree.class.getName() + "$State"), "state");
      map = new ConcurrentString(new ConcurrentStringBTree<>());
    } else throw new IllegalArgumentException(arm);

    keys = new String[size]; queries = new String[size]; alternateKeys = new String[size];
    misses = new String[size]; valuesA = new Object[size]; valuesB = new Object[size];
    alternatePresent = new boolean[size]; overwriteSide = new boolean[size];
    order = permutation(size, 99173);
    for (int i = 0; i < size; i++) {
      String encoded = String.format("%08x", mix(i));
      keys[i] = shape.equals("prefix") ? "shared-prefix-that-is-long-" + encoded : encoded;
    }
    java.util.Arrays.sort(keys);
    for (int i = 0; i < size; i++) {
      queries[i] = new String(keys[i].toCharArray());
      alternateKeys[i] = keys[i] + "!";
      misses[i] = keys[i] + "~";
      valuesA[i] = new String(("value-a-" + i).toCharArray());
      valuesB[i] = new String(("value-b-" + i).toCharArray());
    }
    int[] insertion = permutation(size, 7319);
    for (int rank : insertion) map.put(keys[rank], valuesA[rank]);
    rangeFrom = new String[size - 63]; rangeTo = new String[size - 63];
    for (int i = 0; i < size - 64; i++) {
      rangeFrom[i] = keys[i]; rangeTo[i] = keys[i + 64];
    }
    rangeFrom[size - 64] = keys[size - 64];
    rangeTo[size - 64] = keys[size - 1] + "~";
    rangeOrder = permutation(rangeFrom.length, 91733);
    cursor = rangeCursor = 0;
    checkPositiveFixtures();
    System.out.println("GUARD type=string arm=" + arm + " shape=" + shape + " size=" + size
        + " keysSeed=7319 querySeed=99173 equalQueryDistinctBacking=true");
  }

  private int next() { int i = order[cursor++]; if (cursor == size) cursor = 0; return i; }

  @Benchmark public Object hit() { int i = next(); return map.get(queries[i], null); }
  @Benchmark public Object miss() { int i = next(); return map.get(misses[i], null); }

  @Benchmark public void overwrite() {
    int i = next();
    overwriteSide[i] = !overwriteSide[i];
    map.put(queries[i], overwriteSide[i] ? valuesB[i] : valuesA[i]);
  }

  @Benchmark public void churn() {
    int i = next();
    if (alternatePresent[i]) {
      if (!map.remove(alternateKeys[i])) throw new AssertionError("missing alternate");
      map.put(queries[i], valuesA[i]);
    } else {
      if (!map.remove(queries[i])) throw new AssertionError("missing primary");
      map.put(alternateKeys[i], valuesB[i]);
    }
    alternatePresent[i] = !alternatePresent[i];
  }

  @Benchmark public long range64() {
    int i = rangeOrder[rangeCursor++];
    if (rangeCursor == rangeOrder.length) rangeCursor = 0;
    timedRange.reset();
    map.forEach(rangeFrom[i], rangeTo[i], timedRange.mutableVisitor, timedRange.concurrentVisitor);
    if (timedRange.entries != 64) throw new AssertionError("range count " + timedRange.entries);
    return timedRange.checksum;
  }

  private void checkPositiveFixtures() {
    for (int start : new int[] {0, size / 2, size - 64}) {
      verifyRange.reset();
      map.forEach(rangeFrom[start], rangeTo[start], verifyRange.mutableVisitor,
          verifyRange.concurrentVisitor);
      long expected = 0;
      for (int i = start; i < start + 64; i++) expected += ((String) valuesA[i]).length();
      if (verifyRange.entries != 64 || verifyRange.checksum != expected)
        throw new AssertionError("fixture range " + start);
    }
    for (int rank : new int[] {0, size / 2, size - 1}) {
      if (map.get(queries[rank], null) != valuesA[rank]) throw new AssertionError("point fixture " + rank);
    }
    if (map.get(misses[0], null) != null) throw new AssertionError("miss fixture");
    map.validate();
  }

  @TearDown(Level.Trial)
  public void check() {
    if (map.size() != size) throw new AssertionError("cardinality " + map.size());
    for (int i = 0; i < size; i++) {
      String key = alternatePresent[i] ? alternateKeys[i] : keys[i];
      Object got = map.get(key, null);
      Object expected = alternatePresent[i] || overwriteSide[i] ? valuesB[i] : valuesA[i];
      if (got != expected)
        throw new AssertionError("wrong mapping rank " + i);
      String absent = alternatePresent[i] ? keys[i] : alternateKeys[i];
      if (map.get(absent, null) != null) throw new AssertionError("churn left duplicate rank " + i);
      if (!alternatePresent[i] && map.get(queries[i], null) != got)
        throw new AssertionError("equal-backing lookup rank " + i);
    }
    map.validate();
  }

  private void guard(Class<?> type, String part) throws Exception {
    String key = "tree.guard." + arm + ".string." + part;
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

  private static int mix(int value) {
    value ^= value >>> 16; value *= 0x7feb352d; value ^= value >>> 15;
    value *= 0x846ca68b; return value ^ (value >>> 16);
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

  private static final class SumConsumer implements StringBTree.EntryConsumer<Object>,
      ConcurrentStringBTree.EntryConsumer<Object> {
    final StringBTree.EntryConsumer<Object> mutableVisitor = this::accept;
    final ConcurrentStringBTree.EntryConsumer<Object> concurrentVisitor = this::accept;
    int entries; long checksum;
    void reset() { entries = 0; checksum = 0; }
    public void accept(String key, Object value) { entries++; checksum += ((String) value).length(); }
  }
  private final class VerifyConsumer implements StringBTree.EntryConsumer<Object>,
      ConcurrentStringBTree.EntryConsumer<Object> {
    final StringBTree.EntryConsumer<Object> mutableVisitor = this::accept;
    final ConcurrentStringBTree.EntryConsumer<Object> concurrentVisitor = this::accept;
    int entries; long checksum; String previous;
    void reset() { entries = 0; checksum = 0; previous = null; }
    public void accept(String key, Object value) {
      if (entries > 0 && previous.compareTo(key) >= 0) throw new AssertionError("range order");
      int rank = Arrays.binarySearch(keys, key);
      if (rank < 0 || keys[rank] != key || valuesA[rank] != value)
        throw new AssertionError("range mapping");
      previous = key; entries++; checksum += ((String) value).length();
    }
  }

  private interface StringMap {
    Object get(String key, Object fallback); void put(String key, Object value); boolean remove(String key);
    int size(); void forEach(String from, String to, StringBTree.EntryConsumer<Object> mutable,
        ConcurrentStringBTree.EntryConsumer<Object> concurrent); void validate();
  }
  private record MutableString(StringBTree<Object> tree) implements StringMap {
    public Object get(String k, Object d) { return tree.getOrDefault(k, d); }
    public void put(String k, Object v) { tree.put(k, v); }
    public boolean remove(String k) { return tree.remove(k); }
    public int size() { return tree.size(); }
    public void forEach(String f, String t, StringBTree.EntryConsumer<Object> m,
        ConcurrentStringBTree.EntryConsumer<Object> c) { tree.forEach(f, t, m); }
    public void validate() { tree.validate(); }
  }
  private record ConcurrentString(ConcurrentStringBTree<Object> tree) implements StringMap {
    public Object get(String k, Object d) { return tree.getOrDefault(k, d); }
    public void put(String k, Object v) { tree.put(k, v); }
    public boolean remove(String k) { return tree.remove(k); }
    public int size() { return tree.size(); }
    public void forEach(String f, String t, StringBTree.EntryConsumer<Object> m,
        ConcurrentStringBTree.EntryConsumer<Object> c) { tree.forEach(f, t, c); }
    public void validate() { tree.validate(); }
  }
}
