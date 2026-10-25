package bench;

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

/** Paired hill-climb screen for the frozen 57ea447 baseline; one concrete map per trial. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--add-modules=jdk.incubator.vector", "-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class BaselineSearchScreen {
  @Param({"4096", "262144"})
  public int size;

  @Param({"dense", "fullWidth"})
  public String distribution;

  private CurrentBaselineLongBTree tree;
  private long[] keys;
  private long[] misses;
  private long[] replacements;
  private long[][] rangeBounds;
  private int cursor;
  private int rangeCursor;
  private long writeCounter;
  private final Probe probe = new Probe();

  @Setup(Level.Trial)
  public void setup() {
    SearchScreenFixture.verifyBaselineClasses(CurrentBaselineLongBTree.class);
    tree = new CurrentBaselineLongBTree();
    keys = SearchScreenFixture.keys(distribution, size);
    misses = SearchScreenFixture.absentAlternates(keys);
    replacements = SearchScreenFixture.absentAlternates(keys);
    rangeBounds = SearchScreenFixture.ranges(keys);
    for (long key : keys) tree.put(key, key + 1);
    if (tree.size() != size) throw new AssertionError("Fixture size");
    for (int i = 0; i < SearchScreenFixture.QUERY_COUNT; i++) {
      long key = keys[i];
      if (tree.getOrDefault(key, key) != key + 1) {
        throw new AssertionError("Fixture hit");
      }
      if (tree.getOrDefault(misses[i], ~misses[i]) != ~misses[i]) {
        throw new AssertionError("Fixture miss");
      }
    }
    checkRangeControl(0);
    checkRangeControl(SearchScreenFixture.QUERY_COUNT / 2);
    checkRangeControl(SearchScreenFixture.QUERY_COUNT - 1);
    cursor = 0;
    rangeCursor = 0;
    writeCounter = 0;
    tree.validate();
  }

  private void checkRangeControl(int index) {
    long from = rangeBounds[0][index];
    long to = rangeBounds[1][index];
    probe.sum = 0;
    probe.count = 0;
    tree.forEach(from, to, probe);
    if (probe.count != 64 || probe.sum != rangeSum(from, to)) {
      throw new AssertionError("Fixture range at control " + index);
    }
  }

  private long rangeSum(long from, long to) {
    long sum = 0;
    for (long key : keys) if (key >= from && key < to) sum += key + 1;
    return sum;
  }

  @Benchmark
  public long hit() {
    long key = keys[cursor++ & (size - 1)];
    return tree.getOrDefault(key, key);
  }

  @Benchmark
  public long miss() {
    long key = misses[cursor++ & (size - 1)];
    return tree.getOrDefault(key, ~key);
  }

  @Benchmark
  public long range64() {
    int index = rangeCursor++ & (SearchScreenFixture.QUERY_COUNT - 1);
    probe.sum = 0;
    probe.count = 0;
    tree.forEach(rangeBounds[0][index], rangeBounds[1][index], probe);
    return probe.sum;
  }

  @Benchmark
  public void overwrite() {
    long key = keys[cursor++ & (size - 1)];
    tree.put(key, key + (++writeCounter));
  }

  @Benchmark
  public long churn() {
    int index = cursor++ & (size - 1);
    long oldKey = keys[index];
    long newKey = replacements[index];
    if (!tree.remove(oldKey)) throw new AssertionError("Churn remove");
    tree.put(newKey, newKey + 1);
    keys[index] = newKey;
    replacements[index] = oldKey;
    return newKey;
  }

  @TearDown(Level.Trial)
  public void check() {
    if (tree.size() != size) throw new AssertionError("Cardinality");
    for (long key : keys) {
      if (tree.getOrDefault(key, key) == key) throw new AssertionError("Lost key");
    }
    tree.validate();
  }

  private static final class Probe implements CurrentBaselineLongBTree.EntryConsumer {
    long sum;
    int count;
    @Override
    public void accept(long key, long value) {
      sum += value;
      count++;
    }
  }
}
