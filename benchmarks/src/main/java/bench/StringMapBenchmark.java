package bench;

import io.github.enderekici.packedbtree.StringBTree;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/** Compares String-key point operations using equal prebuilt query strings. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class StringMapBenchmark {
  @Param({"btree", "tree", "hash", "concurrentHash", "skiplist"}) public String implementation;
  @Param({"string", "long"}) public String valueType;
  @Param({"short", "random", "prefix"}) public String shape;
  @Param({"16384"}) public int size;

  private PointMap map;
  private String[] keys;
  private String[] queries;
  private String[] misses;
  private Object[] values;
  private int[] traversal;
  private int cursor;

  @Setup(Level.Trial)
  public void setup() {
    if (Integer.bitCount(size) != 1 || size < 2048) throw new IllegalArgumentException("size must be a power of two >= 2048");
    StringBTree<Object> btree = new StringBTree<>();
    Map<String, Object> jdk = switch (implementation) {
      case "tree" -> new TreeMap<>();
      case "hash" -> new HashMap<>();
      case "concurrentHash" -> new ConcurrentHashMap<>();
      case "skiplist" -> new ConcurrentSkipListMap<>();
      default -> null;
    };
    map = jdk == null ? new BTreePointMap(btree) : new JdkPointMap(jdk);
    keys = new String[size]; queries = new String[size]; misses = new String[size]; values = new Object[size];
    for (int i = 0; i < size; i++) keys[i] = makeKey(i);
    java.util.Arrays.sort(keys);
    int[] insertion = permutation(size, 7319);
    traversal = permutation(size, 19427);
    for (int i = 0; i < size; i++) {
      int rank = insertion[i];
      queries[rank] = copyString(keys[rank]);
      misses[rank] = keys[rank] + "\u0000";
      values[rank] = value(rank);
      map.put(keys[rank], values[rank]);
    }
    cursor = 0;
    if (map.size() != size || !java.util.Objects.equals(map.get(queries[1024]), values[1024]))
      throw new AssertionError("hit/size fixture failed");
    if (map.get(misses[1024]) != null) throw new AssertionError("miss fixture failed");
    Object old = map.get(queries[1024]);
    map.put(queries[1024], values[1024]);
    if (!java.util.Objects.equals(old, values[1024])) throw new AssertionError("overwrite fixture failed");
    if (!map.remove(queries[1024]) || map.get(queries[1024]) != null) throw new AssertionError("remove fixture failed");
    map.put(keys[1024], values[1024]);
  }

  private String makeKey(int i) {
    String number = String.format("%08x", mix(i));
    return switch (shape) {
      case "short" -> "k" + Integer.toString(i, 36);
      case "random" -> number;
      default -> "shared-prefix-that-is-long-" + number;
    };
  }
  private static int mix(int value) { value ^= value >>> 16; value *= 0x7feb352d; value ^= value >>> 15; value *= 0x846ca68b; return value ^ (value >>> 16); }
  private Object value(int i) { return valueType.equals("string") ? "value-" + Integer.toString(i, 36) : Long.valueOf(i * 31L); }
  private static String copyString(String value) { return new String(value.toCharArray()); }
  private static int[] permutation(int size, long seed) {
    int[] values = new int[size]; for (int i = 0; i < size; i++) values[i] = i;
    Random random = new Random(seed);
    for (int i = size - 1; i > 0; i--) { int j = random.nextInt(i + 1); int t = values[i]; values[i] = values[j]; values[j] = t; }
    return values;
  }
  private int nextRank() { int rank = traversal[cursor]; cursor = (cursor + 1) & (size - 1); return rank; }

  @Benchmark public Object hit() { return map.get(queries[nextRank()]); }
  @Benchmark public Object miss() { return map.get(misses[nextRank()]); }
  @Benchmark public void overwrite() { int rank = nextRank(); map.put(queries[rank], values[rank]); }

  @TearDown(Level.Trial)
  public void check() {
    if (map.size() != size) throw new AssertionError("cardinality changed");
    for (int i = 0; i < size; i++) if (!java.util.Objects.equals(map.get(queries[i]), values[i])) throw new AssertionError("lost key rank " + i);
    map.validate();
  }

  private interface PointMap {
    Object get(String key);
    void put(String key, Object value);
    boolean remove(String key);
    int size();
    void validate();
  }
  private static final class BTreePointMap implements PointMap {
    private final StringBTree<Object> tree;
    BTreePointMap(StringBTree<Object> tree) { this.tree = tree; }
    public Object get(String key) { return tree.getOrDefault(key, null); }
    public void put(String key, Object value) { tree.put(key, value); }
    public boolean remove(String key) { return tree.remove(key); }
    public int size() { return tree.size(); }
    public void validate() { tree.validate(); }
  }
  private static final class JdkPointMap implements PointMap {
    private final Map<String, Object> map;
    JdkPointMap(Map<String, Object> map) { this.map = map; }
    public Object get(String key) { return map.get(key); }
    public void put(String key, Object value) { map.put(key, value); }
    public boolean remove(String key) { return map.remove(key) != null; }
    public int size() { return map.size(); }
    public void validate() { }
  }
}
