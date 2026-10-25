package bench;

import io.github.enderekici.packedbtree.LongBTree;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/** Scalar versus compressed lower-bound search on three routing-key distributions. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
@State(Scope.Thread)
public class RoutingBenchmark {
  @Param({"scalar", "compressed"})
  public String search;

  @Param({"sequential", "fullWidth", "commonPrefix"})
  public String distribution;

  @Param({"1048576"})
  public int size;

  private Maps.LongMap map;
  private long[] keys;
  private int cursor;

  @Setup(Level.Trial)
  public void setup() {
    map = Maps.create(search.equals("scalar") ? "btree" : "btreeCompressed");
    keys = RoutingKeys.create(distribution, size);
    for (long key : keys) map.put(key, key ^ 0x5a5a5a5a5a5a5a5aL);
    cursor = 0;
    if (map.size() != size || map.get(keys[0]) != (keys[0] ^ 0x5a5a5a5a5a5a5a5aL)) {
      throw new AssertionError("Routing benchmark fixture");
    }
  }

  @Benchmark
  public long hit() {
    long key = keys[(cursor++) & (size - 1)];
    return map.get(key);
  }

  @TearDown(Level.Trial)
  public void check() {
    if (map.size() != size) throw new AssertionError("Cardinality changed");
    if (map.storage() instanceof LongBTree tree) tree.validate();
    for (long key : keys) {
      if (map.get(key) != (key ^ 0x5a5a5a5a5a5a5a5aL)) {
        throw new AssertionError("Lost key " + key);
      }
    }
  }
}
