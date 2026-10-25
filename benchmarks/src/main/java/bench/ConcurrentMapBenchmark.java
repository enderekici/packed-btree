package bench;

import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;

/** Shared-map point workloads. Set thread count to 1, 2, 4 or 8 on the JMH command line. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Benchmark)
public class ConcurrentMapBenchmark {
  @Param({"concurrentBtree", "skiplist", "hash"})
  public String implementation;

  @Param({"1048576"})
  public int size;

  private ConcurrentLongBTree btree;
  private ConcurrentMap<Long, Long> jdkMap;
  private long[] keys;

  @Setup(Level.Trial)
  public void setup() {
    keys = Maps.shuffledKeys(size, 42);
    if (implementation.equals("concurrentBtree")) btree = new ConcurrentLongBTree();
    else if (implementation.equals("skiplist")) jdkMap = new ConcurrentSkipListMap<>();
    else if (implementation.equals("hash")) jdkMap = new ConcurrentHashMap<>();
    else throw new IllegalArgumentException(implementation);
    for (int i = 0; i < size; i++) {
      long key = keys[i];
      put(key, key * 3);
    }
    if (size() != size || get(keys[0]) != keys[0] * 3) {
      throw new AssertionError("Concurrent point fixture");
    }
  }

  private long nextKey(ThreadCursor cursor) {
    return keys[cursor.cursor++ & (size - 1)];
  }

  private long get(long key) {
    return btree == null ? jdkMap.getOrDefault(key, -1L) : btree.getOrDefault(key, -1);
  }

  private void put(long key, long value) {
    if (btree == null) jdkMap.put(key, value);
    else btree.put(key, value);
  }

  private int size() { return btree == null ? jdkMap.size() : btree.size(); }

  @Benchmark
  public long pointReadOnly(ThreadCursor cursor) {
    return get(nextKey(cursor));
  }

  /** One benchmark invocation performs one read and one overwrite at the same key. */
  @Benchmark
  public long pointMixed(ThreadCursor cursor) {
    long key = nextKey(cursor);
    long value = get(key) + 1;
    put(key, value);
    return value;
  }

  @State(Scope.Thread)
  public static class ThreadCursor {
    int cursor;
    @Setup(Level.Iteration)
    public void setup() { cursor = ThreadLocalRandom.current().nextInt(); }
  }
}
