package bench;

import io.github.enderekici.packedbtree.LongBTree;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/** Matched three-way one-million-entry comparison; one selected map per JMH trial. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class ThreeWaySearchBenchmark {
  private static final int SIZE = 1_048_576;
  private static final int RANGE_COUNT = 4096;
  private static final int RANGE_WIDTH = 64;
  private static final long KEY_SEED = 0x51a7L;

  @Param({"original", "optimized", "treemap"})
  public String implementation;

  private MapOps map;
  private long[] keys;
  private long[] misses;
  private long[] replacementKeys;
  private long[] values;
  private long[][] rangeBounds;
  private long[] rangeSums;
  private int cursor;
  private int rangeCursor;
  private long writeCounter;
  private final RangeProbe probe = new RangeProbe();

  @Setup(Level.Trial)
  public void setup() {
    keys = makeKeys();
    misses = new long[SIZE];
    replacementKeys = new long[SIZE];
    values = new long[SIZE];
    for (int i = 0; i < SIZE; i++) {
      misses[i] = keys[i] + 1;
      replacementKeys[i] = misses[i];
      values[i] = keys[i] + 1;
    }
    makeRanges();
    map = switch (implementation) {
      case "original" -> {
        verifyOriginalClasses();
        yield new OriginalMapOps(new OriginalPrototypeLongBTree());
      }
      case "optimized" -> {
        verifyOptimizedClasses();
        yield new OptimizedMapOps(new LongBTree());
      }
      case "treemap" -> {
        verifyTreeMapClasses();
        yield new TreeMapOps(new TreeMap<>());
      }
      default -> throw new IllegalArgumentException("Unknown map: " + implementation);
    };
    for (int i = 0; i < SIZE; i++) map.put(keys[i], values[i]);
    if (map.size() != SIZE) throw new AssertionError("Fixture cardinality");
    for (int i = 0; i < RANGE_COUNT; i++) {
      long key = keys[i];
      if (map.getOrDefault(key, Long.MIN_VALUE) != key + 1) {
        throw new AssertionError("Fixture hit");
      }
      long miss = misses[i];
      if (map.getOrDefault(miss, ~miss) != ~miss) throw new AssertionError("Fixture miss");
    }
    checkRange(0);
    checkRange(RANGE_COUNT / 2);
    checkRange(RANGE_COUNT - 1);
    cursor = 0;
    rangeCursor = 0;
    writeCounter = 0;
    map.validate();
  }

  @Benchmark
  public long hit() {
    long key = keys[cursor++ & (SIZE - 1)];
    return map.getOrDefault(key, key);
  }

  @Benchmark
  public long miss() {
    long key = misses[cursor++ & (SIZE - 1)];
    return map.getOrDefault(key, ~key);
  }

  @Benchmark
  public void overwrite() {
    int index = cursor++ & (SIZE - 1);
    long newValue = keys[index] + (++writeCounter);
    map.put(keys[index], newValue);
    values[index] = newValue;
  }

  @Benchmark
  public long churn() {
    int index = cursor++ & (SIZE - 1);
    long oldKey = keys[index];
    long newKey = replacementKeys[index];
    if (!map.remove(oldKey)) throw new AssertionError("Churn removal");
    long newValue = newKey + 1;
    map.put(newKey, newValue);
    keys[index] = newKey;
    values[index] = newValue;
    replacementKeys[index] = oldKey;
    return newKey;
  }

  @Benchmark
  public long range64() {
    int index = rangeCursor++ & (RANGE_COUNT - 1);
    probe.reset();
    map.forEach(rangeBounds[index][0], rangeBounds[index][1], probe);
    if (probe.count != RANGE_WIDTH) throw new AssertionError("Timed range count");
    return probe.sum;
  }

  @TearDown(Level.Trial)
  public void check() {
    if (map.size() != SIZE) throw new AssertionError("Trial cardinality");
    for (int i = 0; i < SIZE; i++) {
      if (map.getOrDefault(keys[i], Long.MIN_VALUE) != values[i]) {
        throw new AssertionError("Trial content at " + i);
      }
    }
    map.validate();
  }

  private void makeRanges() {
    long[] sorted = keys.clone();
    java.util.Arrays.sort(sorted);
    rangeBounds = new long[RANGE_COUNT][2];
    rangeSums = new long[RANGE_COUNT];
    Random random = new Random(0x64L);
    for (int i = 0; i < RANGE_COUNT; i++) {
      int start = i == 0 ? 0
          : i == 1 ? SIZE / 2
          : i == 2 ? SIZE - RANGE_WIDTH
          : random.nextInt(SIZE - RANGE_WIDTH + 1);
      rangeBounds[i][0] = sorted[start];
      rangeBounds[i][1] = sorted[start] + 2L * RANGE_WIDTH;
      for (int j = start; j < start + RANGE_WIDTH; j++) rangeSums[i] += sorted[j] + 1;
    }
  }

  private void checkRange(int index) {
    probe.reset();
    map.forEach(rangeBounds[index][0], rangeBounds[index][1], probe);
    if (probe.count != RANGE_WIDTH || probe.sum != rangeSums[index]) {
      throw new AssertionError("Range fixture control " + index);
    }
  }

  private static long[] makeKeys() {
    long[] result = new long[SIZE];
    for (int i = 0; i < SIZE; i++) result[i] = 1024L + 2L * i;
    Random random = new Random(KEY_SEED);
    for (int i = result.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      long value = result[i];
      result[i] = result[j];
      result[j] = value;
    }
    return result;
  }

  private static void verifyOriginalClasses() {
    verifyClass("original", "tree", OriginalPrototypeLongBTree.class);
    verifyClass("original", "node", nested(OriginalPrototypeLongBTree.class, "$Node"));
  }

  private static void verifyOptimizedClasses() {
    verifyClass("optimized", "tree", LongBTree.class);
    verifyClass("optimized", "node", nested(LongBTree.class, "$Node"));
  }

  private static void verifyTreeMapClasses() {
    verifyBootstrapClass("treemap", "tree", TreeMap.class);
    verifyBootstrapClass("treemap", "node", nested(TreeMap.class, "$Entry"));
  }

  private static Class<?> nested(Class<?> outer, String suffix) {
    try {
      return Class.forName(outer.getName() + suffix, false, outer.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Cannot load nested class " + outer.getName() + suffix, e);
    }
  }

  private static void verifyClass(String arm, String kind, Class<?> type) {
    String prefix = "threeway.expected." + arm + "." + kind;
    String location = required(prefix + ".location");
    String expectedHash = required(prefix + ".sha256");
    try {
      URL source = type.getProtectionDomain().getCodeSource().getLocation();
      String resourceName = "/" + type.getName().replace('.', '/') + ".class";
      String hash = classHash(type, resourceName);
      String actualPath = Path.of(source.toURI()).toRealPath().toString();
      String expectedPath = Path.of(location).toRealPath().toString();
      System.out.printf("threeway-class %s %s source=%s module=%s sha256=%s%n",
          arm, kind, actualPath, type.getModule().getName(), hash);
      if (!actualPath.equals(expectedPath) || !hash.equalsIgnoreCase(expectedHash)) {
        throw new IllegalStateException("Unexpected class " + prefix + ": " + actualPath + " " + hash);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Could not verify " + prefix, e);
    }
  }

  private static void verifyBootstrapClass(String arm, String kind, Class<?> type) {
    String prefix = "threeway.expected." + arm + "." + kind;
    String expectedHash = required(prefix + ".sha256");
    String resourceName = "/" + type.getName().replace('.', '/') + ".class";
    String hash = classHash(type, resourceName);
    System.out.printf("threeway-class %s %s source=<bootstrap> module=%s sha256=%s%n",
        arm, kind, type.getModule().getName(), hash);
    if (type.getProtectionDomain().getCodeSource() != null
        || !"java.base".equals(type.getModule().getName())
        || !hash.equalsIgnoreCase(expectedHash)) {
      throw new IllegalStateException("Unexpected bootstrap class " + prefix + ": " + hash);
    }
  }

  private static String classHash(Class<?> type, String resourceName) {
    try (InputStream input = type.getResourceAsStream(resourceName)) {
      if (input == null) throw new IllegalStateException("Missing class bytes: " + resourceName);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot hash class " + type.getName(), e);
    }
  }

  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null) value = System.getenv(property.toUpperCase().replace('.', '_'));
    if (value == null) throw new IllegalStateException("Missing runtime class manifest: " + property);
    return value;
  }

  private interface MapOps {
    long getOrDefault(long key, long fallback);
    void put(long key, long value);
    boolean remove(long key);
    int size();
    void forEach(long from, long to, RangeProbe probe);
    void validate();
  }

  private static final class OriginalMapOps implements MapOps {
    private final OriginalPrototypeLongBTree tree;
    private RangeProbe active;
    private final OriginalPrototypeLongBTree.EntryConsumer consumer = (key, value) -> active.accept(key, value);

    OriginalMapOps(OriginalPrototypeLongBTree tree) { this.tree = tree; }
    @Override public long getOrDefault(long key, long fallback) { return tree.getOrDefault(key, fallback); }
    @Override public void put(long key, long value) { tree.put(key, value); }
    @Override public boolean remove(long key) { return tree.remove(key); }
    @Override public int size() { return tree.size(); }
    @Override public void forEach(long from, long to, RangeProbe probe) {
      active = probe;
      tree.forEach(from, to, consumer);
    }
    @Override public void validate() { tree.validate(); }
  }

  private static final class OptimizedMapOps implements MapOps {
    private final LongBTree tree;
    private RangeProbe active;
    private final LongBTree.EntryConsumer consumer = (key, value) -> active.accept(key, value);

    OptimizedMapOps(LongBTree tree) { this.tree = tree; }
    @Override public long getOrDefault(long key, long fallback) { return tree.getOrDefault(key, fallback); }
    @Override public void put(long key, long value) { tree.put(key, value); }
    @Override public boolean remove(long key) { return tree.remove(key); }
    @Override public int size() { return tree.size(); }
    @Override public void forEach(long from, long to, RangeProbe probe) {
      active = probe;
      tree.forEach(from, to, consumer);
    }
    @Override public void validate() { tree.validate(); }
  }

  private static final class TreeMapOps implements MapOps {
    private final NavigableMap<Long, Long> tree;
    TreeMapOps(NavigableMap<Long, Long> tree) { this.tree = tree; }
    @Override public long getOrDefault(long key, long fallback) { return tree.getOrDefault(key, fallback); }
    @Override public void put(long key, long value) { tree.put(key, value); }
    @Override public boolean remove(long key) { return tree.remove(key) != null; }
    @Override public int size() { return tree.size(); }
    @Override public void forEach(long from, long to, RangeProbe probe) {
      for (Map.Entry<Long, Long> entry : tree.subMap(from, true, to, false).entrySet()) {
        probe.accept(entry.getKey(), entry.getValue());
      }
    }
    @Override public void validate() {
      Long previous = null;
      for (Map.Entry<Long, Long> entry : tree.entrySet()) {
        if (previous != null && previous >= entry.getKey()) throw new AssertionError("TreeMap ordering");
        previous = entry.getKey();
      }
    }
  }

  private static final class RangeProbe {
    long sum;
    int count;
    void reset() { sum = 0; count = 0; }
    void accept(long key, long value) { sum += value; count++; }
  }
}
