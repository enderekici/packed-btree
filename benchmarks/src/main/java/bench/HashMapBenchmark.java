package bench;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/** Single-thread point operations; ordered range operations do not apply to hash maps. */
@State(Scope.Thread)
@org.openjdk.jmh.annotations.BenchmarkMode(org.openjdk.jmh.annotations.Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class HashMapBenchmark {
  private static final long SEED = 0x5eed1234L;

  @Param({"btree", "hashMap", "concurrentHashMap", "fastutilOpenHashMap"})
  public String implementation;

  @Param({"16384", "1048576"})
  public int size;

  private HashMapAdapters.LongMap map;
  private long[] keys;
  private long[] expectedValues;
  private int cursor;

  @Setup(Level.Trial)
  public void setup() {
    map = HashMapAdapters.create(implementation, size);
    keys = shuffledKeys(size, SEED);
    expectedValues = new long[size];
    for (int i = 0; i < size; i++) {
      expectedValues[i] = valueFor(keys[i]);
      map.put(keys[i], expectedValues[i]);
    }
    cursor = 0;
    validateFixture();
  }

  private int next() {
    cursor = (cursor + 1) & (size - 1);
    return cursor;
  }

  @Benchmark
  public long hit() {
    return map.get(keys[next()]);
  }

  @Benchmark
  public long miss() {
    // Keep the miss domain beyond every key used by churn.
    return map.get(2L * size + 4096 + next());
  }

  @Benchmark
  public void overwrite() {
    int index = next();
    expectedValues[index] = valueFor(keys[index]) + cursor;
    map.put(keys[index], expectedValues[index]);
  }

  /** A remove plus insert, with parity toggled in a disjoint odd-key domain. */
  @Benchmark
  public long churn() {
    int index = next();
    long oldKey = keys[index];
    map.remove(oldKey);
    long newKey = oldKey ^ 1L;
    long newValue = valueFor(newKey);
    map.put(newKey, newValue);
    keys[index] = newKey;
    expectedValues[index] = newValue;
    return newKey;
  }

  @TearDown(Level.Trial)
  public void check() {
    if (map.size() != size) throw new AssertionError("Cardinality changed: " + map.size());
    for (int i = 0; i < size; i++) {
      long actual = map.get(keys[i]);
      if (actual != expectedValues[i]) {
        throw new AssertionError("Lost or changed key " + keys[i] + ": " + actual);
      }
    }
  }

  private void validateFixture() {
    if (map.size() != size || map.get(keys[0]) != expectedValues[0]) {
      throw new AssertionError("Benchmark hit fixture");
    }
    long absent = 2L * size + 4096;
    if (map.get(absent) != -1L) throw new AssertionError("Benchmark miss fixture");
  }

  private static long valueFor(long key) {
    return key * 3 + 7;
  }

  static long[] shuffledKeys(int size, long seed) {
    long[] result = new long[size];
    for (int i = 0; i < size; i++) result[i] = 2L * i + 4096;
    java.util.Random random = new java.util.Random(seed);
    for (int i = result.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      long value = result[i];
      result[i] = result[j];
      result[j] = value;
    }
    return result;
  }
}
