package io.github.enderekici.packedbtree.simd;

import io.github.enderekici.packedbtree.LongBTree;
import java.util.Random;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 3, jvmArgsAppend = {
    "--add-modules=jdk.incubator.vector", "-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class SearchBenchmark {
  private static final LongBTree.SearchStrategy SCALAR_HOOK = (keys, count, key) -> {
    int low = 0;
    int high = count;
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (keys[middle] < key) low = middle + 1;
      else high = middle;
    }
    return low;
  };

  @Param({"4096", "262144"})
  public int size;

  private LongBTree baseline;
  private LongBTree scalarHook;
  private LongBTree vector;
  private long[] queries;
  private int cursor;

  @Setup(Level.Trial)
  public void setup() {
    baseline = new LongBTree();
    scalarHook = new LongBTree(SCALAR_HOOK);
    vector = new LongBTree(new VectorSearch());
    for (int i = 0; i < size; i++) {
      long key = (long) i * 3 - size;
      baseline.put(key, key);
      scalarHook.put(key, key);
      vector.put(key, key);
    }
    queries = new long[4096];
    Random random = new Random(0x5eedL);
    for (int i = 0; i < queries.length; i++) {
      queries[i] = random.nextLong(-size, 2L * size);
    }
  }

  private long query() {
    return queries[(cursor++) & (queries.length - 1)];
  }

  @Benchmark
  public long scalarBaseline() {
    return baseline.getOrDefault(query(), -1);
  }

  @Benchmark
  public long scalarHook() {
    return scalarHook.getOrDefault(query(), -1);
  }

  @Benchmark
  public long vectorSearch() {
    return vector.getOrDefault(query(), -1);
  }
}
