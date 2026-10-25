package io.github.enderekici.packedbtree.simd;

import bench.PreHookLongBTree;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/** Measures only the pre-hook implementation in each JMH trial. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {
    "--add-modules=jdk.incubator.vector", "-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class LegacySearchRegressionBenchmark {
  @Param({"4096", "262144"})
  public int size;

  private PreHookLongBTree tree;
  private long[] queries;
  private long[] expected;
  private int cursor;

  @Setup(Level.Trial)
  public void setup() {
    tree = new PreHookLongBTree();
    for (int i = 0; i < size; i++) {
      long key = (long) i * 3 - size;
      tree.put(key, key);
    }
    queries = new long[4096];
    expected = new long[queries.length];
    Random random = new Random(0x5eedL);
    for (int i = 0; i < queries.length; i++) {
      long key = random.nextLong(-size, 2L * size);
      queries[i] = key;
      expected[i] = key >= -size && key <= 2L * size - 3 && (key + size) % 3 == 0
          ? key : -1;
    }
    cursor = 0;
    if (tree.size() != size || tree.getOrDefault(-size, Long.MIN_VALUE) != -size) {
      throw new AssertionError("Pre-hook fixture");
    }
    tree.validate();
  }

  @Benchmark
  public long preHookBaseline() {
    int index = cursor++ & (queries.length - 1);
    return tree.getOrDefault(queries[index], -1);
  }

  @TearDown(Level.Trial)
  public void check() {
    for (int i = 0; i < queries.length; i++) {
      if (tree.getOrDefault(queries[i], -1) != expected[i]) {
        throw new AssertionError("Pre-hook query fixture at " + i);
      }
    }
    tree.validate();
  }
}
