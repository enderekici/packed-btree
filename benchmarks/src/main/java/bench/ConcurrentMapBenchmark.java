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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.ThreadParams;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;

/** Shared-map point workloads. Set thread count to 1, 2, 4 or 8 on the JMH command line. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
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
    return keys[cursor.next(size - 1)];
  }

  private long ownedKey(ThreadCursor cursor, ThreadParams threads) {
    int threadCount = threads.getThreadCount();
    int partitionSize = size / threadCount;
    if (partitionSize * threadCount != size || Integer.bitCount(partitionSize) != 1) {
      throw new IllegalStateException("Size must divide into power-of-two thread partitions");
    }
    int index = threads.getThreadIndex() + cursor.next(partitionSize - 1) * threadCount;
    return keys[index];
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
    long key = nextKey(cursor);
    long value = get(key);
    cursor.readOnly = true;
    cursor.lastKey = key;
    cursor.lastRead = value;
    return value;
  }

  /** One benchmark invocation reads and increments one thread-owned key. */
  @Benchmark
  public long pointMixed(ThreadCursor cursor, ThreadParams threads) {
    long key = ownedKey(cursor, threads);
    long oldValue = get(key);
    long newValue = oldValue + 1;
    put(key, newValue);
    cursor.mixed = true;
    cursor.writes++;
    cursor.lastKey = key;
    cursor.lastRead = oldValue;
    cursor.lastWrite = newValue;
    return newValue;
  }

  @TearDown(Level.Trial)
  public void check() {
    if (size() != size) throw new AssertionError("Concurrent benchmark cardinality changed");
    for (long key : keys) {
      long value = get(key);
      if (value < key * 3) throw new AssertionError("Lost/invalid benchmark key " + key);
    }
    if (btree != null) btree.validate();
  }

  @State(Scope.Thread)
  public static class ThreadCursor {
    int random;
    boolean readOnly;
    boolean mixed;
    long writes;
    long lastKey;
    long lastRead;
    long lastWrite;
    @Setup(Level.Iteration)
    public void setup() {
      random = ThreadLocalRandom.current().nextInt();
      if (random == 0) random = 1;
      readOnly = false;
      mixed = false;
      writes = 0;
    }
    @TearDown(Level.Trial)
    public void check() {
      if (readOnly && lastRead != lastKey * 3) {
        throw new AssertionError("Read-only point result mismatch");
      }
      if (mixed && (writes == 0 || lastWrite != lastRead + 1)) {
        throw new AssertionError("Mixed point result mismatch");
      }
    }
    int next(int mask) {
      random ^= random << 13;
      random ^= random >>> 17;
      random ^= random << 5;
      return random & mask;
    }
  }
}
