package bench;

import io.github.enderekici.packedbtree.StringBTree;
import java.util.Arrays;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/** Compares ordered range traversal while consuming the same value payloads. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class OrderedStringRangeBenchmark {
  @Param({"btree", "tree", "skiplist"}) public String implementation;
  @Param({"string", "long"}) public String valueType;
  @Param({"short", "random", "prefix"}) public String shape;
  @Param({"16384"}) public int size;

  private RangeRunner range;
  private StringBTree<Object> btree;
  private String[] keys;
  private Object[] values;
  private long[] expectedChecksums;
  private int[] rangeStarts;
  private int cursor;
  private final Accumulator accumulator = new Accumulator();

  @Setup(Level.Trial)
  public void setup() {
    if (size <= 64) throw new IllegalArgumentException("size must exceed range width");
    btree = new StringBTree<>();
    NavigableMap<String, Object> jdk = implementation.equals("tree")
        ? new TreeMap<>() : implementation.equals("skiplist") ? new ConcurrentSkipListMap<>() : null;
    TreeMap<String, Object> oracle = new TreeMap<>();
    keys = new String[size]; values = new Object[size]; expectedChecksums = new long[size - 64];
    for (int i = 0; i < size; i++) keys[i] = makeKey(i);
    Arrays.sort(keys);
    for (int i = 0; i < size; i++) {
      values[i] = value(i);
      btree.put(keys[i], values[i]); oracle.put(keys[i], values[i]);
      if (jdk != null) jdk.put(keys[i], values[i]);
    }
    range = jdk == null ? (from, to, visitor) -> btree.forEach(from, to, visitor)
        : (from, to, visitor) -> {
          for (Map.Entry<String, Object> entry : jdk.subMap(from, true, to, false).entrySet())
            visitor.accept(entry.getKey(), entry.getValue());
        };
    rangeStarts = permutation(size - 64, 50117);
    for (int start = 0; start < size - 64; start++) {
      expectedChecksums[start] = checksum(oracle.subMap(keys[start], true, keys[start + 64], false));
      accumulator.reset(); range.run(keys[start], keys[start + 64], accumulator);
      if (accumulator.count != 64 || accumulator.checksum != expectedChecksums[start])
        throw new AssertionError("range positive fixture at " + start);
    }
    btree.validate();
    cursor = 0;
  }

  private String makeKey(int i) {
    int n = mix(i);
    return switch (shape) {
      case "short" -> "k" + Integer.toString(i, 36);
      case "random" -> String.format("%08x", n);
      default -> "shared-prefix-that-is-long-" + String.format("%08x", n);
    };
  }
  private static int mix(int value) { value ^= value >>> 16; value *= 0x7feb352d; value ^= value >>> 15; value *= 0x846ca68b; return value ^ (value >>> 16); }
  private Object value(int i) { return valueType.equals("string") ? "v" + Integer.toString(i, 36) : Long.valueOf(i * 31L); }
  private static long payload(Object value) { return value instanceof String s ? s.hashCode() : ((Long) value).longValue(); }
  private static int[] permutation(int length, long seed) {
    int[] ranks = new int[length]; for (int i = 0; i < length; i++) ranks[i] = i;
    java.util.Random random = new java.util.Random(seed);
    for (int i = length - 1; i > 0; i--) { int j = random.nextInt(i + 1); int t = ranks[i]; ranks[i] = ranks[j]; ranks[j] = t; }
    return ranks;
  }
  private static long checksum(Map<String, Object> values) { long sum = 0; for (Object value : values.values()) sum += payload(value); return sum; }

  @Benchmark
  public long range64() {
    int start = rangeStarts[cursor++];
    if (cursor == rangeStarts.length) cursor = 0;
    accumulator.reset();
    range.run(keys[start], keys[start + 64], accumulator);
    return accumulator.checksum;
  }

  @TearDown(Level.Trial)
  public void check() {
    btree.validate();
    rangeStarts = permutation(size - 64, 50117);
    for (int start = 0; start < size - 64; start++) {
      accumulator.reset(); range.run(keys[start], keys[start + 64], accumulator);
      if (accumulator.count != 64 || accumulator.checksum != expectedChecksums[start])
        throw new AssertionError("range changed at " + start);
    }
  }

  @FunctionalInterface private interface RangeRunner {
    void run(String from, String to, StringBTree.EntryConsumer<Object> visitor);
  }
  private static final class Accumulator implements StringBTree.EntryConsumer<Object> {
    int count;
    long checksum;
    public void accept(String key, Object value) { count++; checksum += payload(value); }
    void reset() { count = 0; checksum = 0; }
  }
}
