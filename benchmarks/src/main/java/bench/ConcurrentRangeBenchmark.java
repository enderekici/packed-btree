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
import org.openjdk.jmh.annotations.Warmup;

/** Ordered point range comparison; hash maps are intentionally excluded. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
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
  }

  @Benchmark
  public long range64(ThreadState thread) {
    long from = keys[thread.cursor++ & (size - 1)];
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

  @State(Scope.Thread)
  public static class ThreadState {
    int cursor;
    final Sum sum = new Sum();
    @Setup(Level.Iteration)
    public void setup() { cursor = java.util.concurrent.ThreadLocalRandom.current().nextInt(); }
  }

  private static final class Sum implements ConcurrentLongBTree.EntryConsumer {
    long value;
    public void accept(long key, long value) { this.value += value; }
  }
}
