package bench;

import io.github.enderekici.packedbtree.CoveredBaselineStringBTree;
import io.github.enderekici.packedbtree.StringBTree;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
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

/** Selected-map, runtime-fingerprinted StringBTree hill-climb harness. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class StringCoveredRangeBenchmark {
  @Param({"candidate", "baseline", "treemap"}) public String implementation;
  @Param({"16384", "262144"}) public int size;
  @Param({"short", "random", "prefix"}) public String shape;
  @Param({"string"}) public String valueType;
  @Param({"0", "1", "8", "64", "1024"}) public int rangeWidth;

  private MapOps map;
  private String[] keys;
  private String[] hitQueries;
  private String[] alternateKeys;
  private String[] missQueries;
  private String[] currentKeys;
  private Object[] valueA;
  private Object[] valueB;
  private Object[] expectedValues;
  private boolean[] valueIsB;
  private boolean[] keyIsAlternate;
  private int[] ranks;
  private int[] rangeRanks;
  private String finalRangeUpperBound;
  private int cursor;
  private int rangeCursor;
  private final RangeProbe probe = new RangeProbe();

  @Setup(Level.Trial)
  public void setup() {
    verifyProductionClasses();
    verifyBaselineClasses();
    keys = new String[size];
    hitQueries = new String[size];
    alternateKeys = new String[size];
    missQueries = new String[size];
    currentKeys = new String[size];
    valueA = new Object[size];
    valueB = new Object[size];
    expectedValues = new Object[size];
    valueIsB = new boolean[size];
    keyIsAlternate = new boolean[size];
    for (int i = 0; i < size; i++) keys[i] = makeKey(i);
    Arrays.sort(keys);
    for (int i = 0; i < size; i++) {
      hitQueries[i] = copyString(keys[i]);
      alternateKeys[i] = keys[i] + "\u0000";
      missQueries[i] = copyString(alternateKeys[i]);
      currentKeys[i] = keys[i];
      valueA[i] = makeValue(i, false);
      valueB[i] = makeValue(i, true);
      expectedValues[i] = valueA[i];
    }
    int[] insertionOrder = permutation(size, 0x51a7L);
    ranks = permutation(size, 0x5eedL);
    rangeRanks = permutation(size - rangeWidth + 1, 0x64L);
    finalRangeUpperBound = keys[size - 1] + "\u0000";
    map = createMap();
    for (int i = 0; i < size; i++) {
      int rank = insertionOrder[i];
      map.put(keys[rank], valueA[rank]);
    }
    checkFixture();
    cursor = 0;
    rangeCursor = 0;
  }

  @Benchmark public Object hit() {
    int rank = ranks[cursor++ & (size - 1)];
    return map.getOrDefault(hitQueries[rank], MISS);
  }

  @Benchmark public Object miss() {
    int rank = ranks[cursor++ & (size - 1)];
    return map.getOrDefault(missQueries[rank], MISS);
  }

  @Benchmark public void overwrite() {
    int rank = ranks[cursor++ & (size - 1)];
    boolean nextB = !valueIsB[rank];
    Object next = nextB ? valueB[rank] : valueA[rank];
    map.put(hitQueries[rank], next);
    valueIsB[rank] = nextB;
    expectedValues[rank] = next;
  }

  @Benchmark public Object churn() {
    int rank = ranks[cursor++ & (size - 1)];
    String oldKey = currentKeys[rank];
    String newKey = keyIsAlternate[rank] ? keys[rank] : alternateKeys[rank];
    if (!map.remove(oldKey)) throw new AssertionError("Churn removal failed");
    map.put(newKey, expectedValues[rank]);
    currentKeys[rank] = newKey;
    keyIsAlternate[rank] = !keyIsAlternate[rank];
    return newKey;
  }

  @Benchmark public long range() {
    int start = rangeRanks[rangeCursor];
    if (++rangeCursor == rangeRanks.length) rangeCursor = 0;
    String from = rangeBound(start);
    String to = rangeBound(start + rangeWidth);
    probe.reset();
    map.forEach(from, to, probe);
    if (probe.count != rangeWidth) throw new AssertionError("Timed range cardinality " + probe.count);
    return probe.checksum;
  }

  @TearDown(Level.Trial)
  public void checkTrial() {
    if (map.size() != size) throw new AssertionError("Trial cardinality");
    for (int i = 0; i < size; i++) {
      Object found = map.getOrDefault(currentKeys[i], MISS);
      if (found != expectedValues[i]) throw new AssertionError("Trial value identity " + i);
      String absent = keyIsAlternate[i] ? hitQueries[i] : alternateKeys[i];
      if (map.getOrDefault(absent, MISS) != MISS) throw new AssertionError("Unexpected alternate key " + i);
    }
    map.validate();
  }

  private void checkFixture() {
    if (map.size() != size) throw new AssertionError("Setup cardinality");
    int[] probes = {0, size / 2, size - 1};
    for (int rank : probes) {
      if (hitQueries[rank] == keys[rank] || !hitQueries[rank].equals(keys[rank]))
        throw new AssertionError("Lookup fixture identity/content");
      if (!java.util.Objects.equals(map.getOrDefault(hitQueries[rank], MISS), valueA[rank]))
        throw new AssertionError("Hit fixture " + rank);
      if (map.getOrDefault(missQueries[rank], MISS) != MISS) throw new AssertionError("Miss fixture " + rank);
    }
    int removeRank = size / 3;
    if (!map.remove(keys[removeRank]) || map.getOrDefault(hitQueries[removeRank], MISS) != MISS)
      throw new AssertionError("Delete fixture");
    map.put(keys[removeRank], valueA[removeRank]);
    if (!java.util.Objects.equals(map.getOrDefault(hitQueries[removeRank], MISS), valueA[removeRank]))
      throw new AssertionError("Delete/refill fixture");
    checkRange(0);
    checkRange(size / 2 - rangeWidth / 2);
    checkRange(size - rangeWidth);
    map.validate();
  }

  private void checkRange(int start) {
    int countExpected = Math.min(rangeWidth, size - start);
    String from = rangeBound(start);
    String to = rangeBound(start + countExpected);
    probe.reset();
    map.forEach(from, to, probe);
    long expected = 0;
    int count = 0;
    for (int i = start; i < start + countExpected; i++) {
      expected += payload(valueA[i]);
      count++;
    }
    if (probe.count != count || probe.checksum != expected)
      throw new AssertionError("Range fixture start=" + start + " got=" + probe.count + "/" + probe.checksum);
  }

  private String rangeBound(int rank) { return rank == size ? finalRangeUpperBound : keys[rank]; }

  private Object makeValue(int rank, boolean alternate) {
    return valueType.equals("string")
        ? (alternate ? "value-b-" : "value-a-") + Integer.toString(rank, 36)
        : Long.valueOf((long) rank * 31 + (alternate ? 1 : 0));
  }

  private String makeKey(int rank) {
    return switch (shape) {
      case "short" -> "k" + Integer.toString(rank, 36);
      case "random" -> hex8(mix(rank));
      case "prefix" -> "shared-prefix-for-string-btree-" + hex8(mix(rank));
      default -> throw new IllegalArgumentException("Unknown key shape " + shape);
    };
  }

  private static int mix(int value) {
    value ^= value >>> 16; value *= 0x7feb352d; value ^= value >>> 15;
    value *= 0x846ca68b; return value ^ (value >>> 16);
  }

  private static String hex8(int value) {
    char[] out = new char[8];
    for (int i = 7; i >= 0; i--) { out[i] = Character.forDigit(value & 15, 16); value >>>= 4; }
    return new String(out);
  }

  private static String copyString(String value) { return new String(value.toCharArray()); }

  private static int[] permutation(int length, long seed) {
    int[] order = new int[length];
    for (int i = 0; i < length; i++) order[i] = i;
    Random random = new Random(seed);
    for (int i = length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1); int temp = order[i]; order[i] = order[j]; order[j] = temp;
    }
    return order;
  }

  private static long payload(Object value) {
    return value instanceof String string ? string.hashCode() : ((Long) value).longValue();
  }

  private MapOps createMap() {
    return switch (implementation) {
      case "candidate" -> new CandidateMap(new StringBTree<>());
      case "baseline" -> new BaselineMap(new CoveredBaselineStringBTree<>());
      case "treemap" -> new TreeMapMap(new TreeMap<>());
      default -> throw new IllegalArgumentException("Unknown implementation " + implementation);
    };
  }

  private void verifyProductionClasses() {
    verify("candidate", "tree", StringBTree.class);
    verify("candidate", "node", nested(StringBTree.class, "$Node"));
  }

  private void verifyBaselineClasses() {
    verify("baseline", "tree", CoveredBaselineStringBTree.class);
    verify("baseline", "node", nested(CoveredBaselineStringBTree.class, "$Node"));
  }

  private static Class<?> nested(Class<?> outer, String suffix) {
    try { return Class.forName(outer.getName() + suffix, false, outer.getClassLoader()); }
    catch (ClassNotFoundException e) { throw new IllegalStateException("Missing " + outer.getName() + suffix, e); }
  }

  private static void verify(String arm, String kind, Class<?> type) {
    String prefix = "covered.expected." + arm + "." + kind;
    String expectedLocation = required(prefix + ".location");
    String expectedHash = required(prefix + ".sha256");
    try {
      URL source = type.getProtectionDomain().getCodeSource().getLocation();
      String actualLocation = Path.of(source.toURI()).toRealPath().toString();
      String hash = classHash(type);
      System.out.printf("string-class %s %s source=%s module=%s sha256=%s%n",
          arm, kind, actualLocation, type.getModule().getName(), hash);
      if (!actualLocation.equals(Path.of(expectedLocation).toRealPath().toString())
          || !hash.equalsIgnoreCase(expectedHash)) {
        throw new IllegalStateException("Unexpected loaded class for " + prefix + ": " + actualLocation + " " + hash);
      }
    } catch (Exception e) { throw new IllegalStateException("Cannot verify " + prefix, e); }
  }

  private static String classHash(Class<?> type) {
    String resource = "/" + type.getName().replace('.', '/') + ".class";
    try (InputStream input = type.getResourceAsStream(resource)) {
      if (input == null) throw new IllegalStateException("Missing class bytes " + resource);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
    } catch (Exception e) { throw new IllegalStateException("Cannot hash " + type.getName(), e); }
  }

  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null) throw new IllegalStateException("Missing runtime class manifest property " + property);
    return value;
  }

  private interface MapOps {
    Object getOrDefault(String key, Object fallback);
    void put(String key, Object value);
    boolean remove(String key);
    int size();
    void forEach(String from, String to, RangeProbe probe);
    void validate();
  }

  private static final class CandidateMap implements MapOps {
    private final StringBTree<Object> tree;
    private RangeProbe active;
    private final StringBTree.EntryConsumer<Object> consumer = (key, value) -> active.accept(key, value);
    CandidateMap(StringBTree<Object> tree) { this.tree = tree; }
    public Object getOrDefault(String key, Object fallback) { return tree.getOrDefault(key, fallback); }
    public void put(String key, Object value) { tree.put(key, value); }
    public boolean remove(String key) { return tree.remove(key); }
    public int size() { return tree.size(); }
    public void forEach(String from, String to, RangeProbe probe) { active = probe; tree.forEach(from, to, consumer); }
    public void validate() { tree.validate(); }
  }

  private static final class BaselineMap implements MapOps {
    private final CoveredBaselineStringBTree<Object> tree;
    private RangeProbe active;
    private final CoveredBaselineStringBTree.EntryConsumer<Object> consumer = (key, value) -> active.accept(key, value);
    BaselineMap(CoveredBaselineStringBTree<Object> tree) { this.tree = tree; }
    public Object getOrDefault(String key, Object fallback) { return tree.getOrDefault(key, fallback); }
    public void put(String key, Object value) { tree.put(key, value); }
    public boolean remove(String key) { return tree.remove(key); }
    public int size() { return tree.size(); }
    public void forEach(String from, String to, RangeProbe probe) { active = probe; tree.forEach(from, to, consumer); }
    public void validate() { tree.validate(); }
  }

  private static final class TreeMapMap implements MapOps {
    private final NavigableMap<String, Object> tree;
    TreeMapMap(NavigableMap<String, Object> tree) { this.tree = tree; }
    public Object getOrDefault(String key, Object fallback) { return tree.getOrDefault(key, fallback); }
    public void put(String key, Object value) { tree.put(key, value); }
    public boolean remove(String key) { return tree.remove(key) != null; }
    public int size() { return tree.size(); }
    public void forEach(String from, String to, RangeProbe probe) {
      for (Map.Entry<String, Object> entry : tree.subMap(from, true, to, false).entrySet())
        probe.accept(entry.getKey(), entry.getValue());
    }
    public void validate() { }
  }

  private static final Object MISS = new Object();

  private static final class RangeProbe {
    private int count;
    private long checksum;
    void accept(String key, Object value) { count++; checksum += payload(value); }
    void reset() { count = 0; checksum = 0; }
  }
}
