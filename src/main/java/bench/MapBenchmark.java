package bench;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class MapBenchmark {
  @Param({"btree", "tree", "skiplist", "primitiveAvl"})
  public String implementation;

  @Param({"16384", "1048576"})
  public int size;

  private Maps.LongMap map;
  private long[] keys;
  private int cursor;

  @Setup(Level.Trial)
  public void setup() {
    map = Maps.create(implementation);
    keys = Maps.shuffledKeys(size, 42);
    for (long key : keys) map.put(key, key * 3);
    cursor = 0;
    if (map.size() != size || map.get(1024) != 3072 || map.get(1025) != -1) {
      throw new AssertionError("Benchmark lookup fixture");
    }
    long expected = 0;
    for (long key = 1024; key < 1152; key += 2) expected += key * 3;
    if (map.rangeSum(1024, 1152) != expected) {
      throw new AssertionError("Benchmark range fixture");
    }
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
    return map.get(keys[next()] | 1);
  }

  @Benchmark
  public void overwrite() {
    long key = keys[next()];
    map.put(key, key * 3 + cursor);
  }

  /** One operation is a remove plus insert; toggling parity keeps cardinality constant. */
  @Benchmark
  public long churn() {
    int index = next();
    long old = keys[index];
    map.remove(old);
    long replacement = old ^ 1;
    map.put(replacement, replacement * 3);
    keys[index] = replacement;
    return replacement;
  }

  /** Exactly 64 adjacent entries, including ranges near the upper boundary. */
  @Benchmark
  public long range64() {
    long key = keys[next()];
    long from = Math.min(key, 1024L + 2L * (size - 64));
    return map.rangeSum(from, from + 128);
  }

  @TearDown(Level.Trial)
  public void check() {
    if (map.size() != size) throw new AssertionError("Cardinality changed");
    for (long key : keys) {
      if (map.get(key) == -1) throw new AssertionError("Lost key " + key);
    }
    if (map.storage() instanceof LongBTree tree) tree.validate();
  }
}
