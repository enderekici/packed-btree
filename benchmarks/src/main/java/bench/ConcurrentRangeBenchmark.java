package bench;

import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
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

/** Ordered point range comparison; hash maps are intentionally excluded. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Benchmark)
public class ConcurrentRangeBenchmark {
  @Param({"concurrentBtree", "skiplist"})
  public String implementation;

  @Param({"1048576"})
  public int size;

  private ConcurrentLongBTree btree;
  private ConcurrentSkipListMap<Long, Long> skiplist;
  private long[] keys;

  @Setup(Level.Trial)
  public void setup() {
    keys = Maps.shuffledKeys(size, 42);
    if (implementation.equals("concurrentBtree")) btree = new ConcurrentLongBTree();
    else skiplist = new ConcurrentSkipListMap<>();
    for (int i = 0; i < size; i++) {
      long key = keys[i];
      if (btree == null) skiplist.put(key, key * 3);
      else btree.put(key, key * 3);
    }
    long expected = 0;
    for (int i = 0; i < 64; i++) expected += (1024L + 2L * i) * 3;
    long observed;
    if (btree != null) {
      Sum sum = new Sum();
      btree.forEach(1024, 1152, sum);
      observed = sum.value;
    } else {
      observed = 0;
      for (Map.Entry<Long, Long> entry : skiplist.subMap(1024L, true, 1152L, false).entrySet()) {
        observed += entry.getValue();
      }
    }
    if (observed != expected) throw new AssertionError("Concurrent range fixture");
  }

  @Benchmark
  public long range64(ThreadState thread) {
    int firstIndex = thread.next(size - 1) % (size - 63);
    long from = 1024L + 2L * firstIndex;
    long to = from + 128;
    if (btree != null) {
      thread.sum.value = 0;
      btree.forEach(from, to, thread.sum);
      return thread.sum.value;
    }
    long sum = 0;
    for (Map.Entry<Long, Long> entry : skiplist.subMap(from, true, to, false).entrySet()) {
      sum += entry.getValue();
    }
    return sum;
  }

  @TearDown(Level.Trial)
  public void check() {
    int actualSize = btree == null ? skiplist.size() : btree.size();
    if (actualSize != size) throw new AssertionError("Concurrent range cardinality changed");
    if (btree != null) btree.validate();
  }

  @State(Scope.Thread)
  public static class ThreadState {
    int random;
    final Sum sum = new Sum();
    @Setup(Level.Iteration)
    public void setup() {
      random = java.util.concurrent.ThreadLocalRandom.current().nextInt();
      if (random == 0) random = 1;
    }
    int next(int mask) {
      random ^= random << 13;
      random ^= random >>> 17;
      random ^= random << 5;
      return random & mask;
    }
  }

  private static final class Sum implements ConcurrentLongBTree.EntryConsumer {
    long value;
    public void accept(long key, long value) { this.value += value; }
  }
}
