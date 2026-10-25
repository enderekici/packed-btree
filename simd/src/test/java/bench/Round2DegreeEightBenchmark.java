package bench;

import io.github.enderekici.packedbtree.LongBTree;
import java.util.Arrays;
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

/** Degree-eight selected-map screen; candidate and frozen baseline are separate JMH trials. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {
    "--add-modules=jdk.incubator.vector", "-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class Round2DegreeEightBenchmark {
  @Param({"4096", "262144"})
  public int size;

  @Param({"dense", "fullWidth"})
  public String distribution;

  @Param({"baseline", "candidate"})
  public String implementation;

  private TreeOps tree;
  private long[] keys;
  private long[] misses;
  private long[] replacements;
  private long[] values;
  private long[][] rangeBounds;
  private int cursor;
  private int rangeCursor;
  private long writeCounter;
  private final Probe probe = new Probe();

  @Setup(Level.Trial)
  public void setup() {
    keys = SearchScreenFixture.keys(distribution, size);
    misses = SearchScreenFixture.absentAlternates(keys);
    replacements = SearchScreenFixture.absentAlternates(keys);
    values = new long[size];
    for (int i = 0; i < size; i++) values[i] = keys[i] + 1;
    rangeBounds = SearchScreenFixture.ranges(keys);
    tree = switch (implementation) {
      case "baseline" -> {
        SearchScreenFixture.verifyBaselineClasses(Round2BaselineLongBTree.class);
        yield new BaselineOps(new Round2BaselineLongBTree());
      }
      case "candidate" -> {
        SearchScreenFixture.verifyCandidateClasses(LongBTree.class);
        yield new CandidateOps(new LongBTree());
      }
      default -> throw new IllegalArgumentException("Unknown implementation: " + implementation);
    };
    for (int i = 0; i < size; i++) tree.put(keys[i], values[i]);
    if (tree.size() != size) throw new AssertionError("Fixture size");
    for (int i = 0; i < SearchScreenFixture.QUERY_COUNT; i++) {
      long key = keys[i];
      long value = key + 1;
      if (tree.getOrDefault(key, ~value) != value) throw new AssertionError("Fixture hit");
      if (tree.getOrDefault(misses[i], ~misses[i]) != ~misses[i]) {
        throw new AssertionError("Fixture miss");
      }
    }
    checkRangeControl(0);
    checkRangeControl(SearchScreenFixture.QUERY_COUNT / 2);
    checkRangeControl(SearchScreenFixture.QUERY_COUNT - 1);
    long[] sorted = keys.clone();
    Arrays.sort(sorted);
    checkRange(sorted[0], sorted[64]);
    checkRange(sorted[sorted.length / 2], sorted[sorted.length / 2 + 64]);
    long greatestKey = sorted[sorted.length - 1];
    if (greatestKey == Long.MAX_VALUE) {
      throw new AssertionError("Fixture needs a representable exclusive range upper bound");
    }
    checkRange(sorted[sorted.length - 64], greatestKey + 1);
    cursor = 0;
    rangeCursor = 0;
    writeCounter = 0;
    tree.validate();
  }

  private void checkRangeControl(int index) {
    checkRange(rangeBounds[0][index], rangeBounds[1][index]);
  }

  private void checkRange(long from, long to) {
    probe.sum = 0;
    probe.count = 0;
    tree.forEach(from, to, probe);
    if (probe.count != 64 || probe.sum != rangeSum(from, to)) {
      throw new AssertionError("Range fixture window");
    }
  }

  private long rangeSum(long from, long to) {
    long sum = 0;
    for (int i = 0; i < keys.length; i++) {
      if (keys[i] >= from && keys[i] < to) sum += values[i];
    }
    return sum;
  }

  @Benchmark
  public long hit() {
    int index = cursor++ & (size - 1);
    return tree.getOrDefault(keys[index], ~values[index]);
  }

  @Benchmark
  public long miss() {
    int index = cursor++ & (size - 1);
    return tree.getOrDefault(misses[index], ~misses[index]);
  }

  @Benchmark
  public long range64() {
    int index = rangeCursor++ & (SearchScreenFixture.QUERY_COUNT - 1);
    probe.sum = 0;
    probe.count = 0;
    tree.forEach(rangeBounds[0][index], rangeBounds[1][index], probe);
    if (probe.count != 64) throw new AssertionError("Timed range count");
    return probe.sum;
  }

  @Benchmark
  public void overwrite() {
    int index = cursor++ & (size - 1);
    long value = keys[index] + (++writeCounter);
    tree.put(keys[index], value);
    values[index] = value;
  }

  @Benchmark
  public long churn() {
    int index = cursor++ & (size - 1);
    long oldKey = keys[index];
    long newKey = replacements[index];
    if (!tree.remove(oldKey)) throw new AssertionError("Churn remove");
    long value = newKey + 1;
    tree.put(newKey, value);
    keys[index] = newKey;
    values[index] = value;
    replacements[index] = oldKey;
    return newKey;
  }

  @TearDown(Level.Trial)
  public void check() {
    if (tree.size() != size) throw new AssertionError("Cardinality");
    for (int i = 0; i < size; i++) {
      long value = values[i];
      if (tree.getOrDefault(keys[i], ~value) != value) {
        throw new AssertionError("Trial content at " + i);
      }
    }
    tree.validate();
  }

  private interface TreeOps {
    long getOrDefault(long key, long fallback);
    void put(long key, long value);
    boolean remove(long key);
    int size();
    void forEach(long from, long to, Probe probe);
    void validate();
  }

  private static final class CandidateOps implements TreeOps {
    private final LongBTree tree;
    private Probe active;
    private final LongBTree.EntryConsumer consumer = (key, value) -> active.accept(key, value);
    CandidateOps(LongBTree tree) { this.tree = tree; }
    @Override
    public long getOrDefault(long key, long fallback) {
      return tree.getOrDefault(key, fallback);
    }
    @Override
    public void put(long key, long value) { tree.put(key, value); }
    @Override
    public boolean remove(long key) { return tree.remove(key); }
    @Override
    public int size() { return tree.size(); }
    @Override
    public void forEach(long from, long to, Probe probe) {
      active = probe;
      tree.forEach(from, to, consumer);
    }
    @Override
    public void validate() { tree.validate(); }
  }

  private static final class BaselineOps implements TreeOps {
    private final Round2BaselineLongBTree tree;
    private Probe active;
    private final Round2BaselineLongBTree.EntryConsumer consumer =
        (key, value) -> active.accept(key, value);
    BaselineOps(Round2BaselineLongBTree tree) { this.tree = tree; }
    @Override
    public long getOrDefault(long key, long fallback) {
      return tree.getOrDefault(key, fallback);
    }
    @Override
    public void put(long key, long value) { tree.put(key, value); }
    @Override
    public boolean remove(long key) { return tree.remove(key); }
    @Override
    public int size() { return tree.size(); }
    @Override
    public void forEach(long from, long to, Probe probe) {
      active = probe;
      tree.forEach(from, to, consumer);
    }
    @Override
    public void validate() { tree.validate(); }
  }

  private static final class Probe implements LongBTree.EntryConsumer,
      Round2BaselineLongBTree.EntryConsumer {
    long sum;
    int count;
    @Override public void accept(long key, long value) { sum += value; count++; }
  }
}
