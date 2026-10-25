package bench.concurrent;

import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URL;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.ThreadParams;

/** Guarded C1-versus-base point and snapshot-range screen. */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ConcurrentLongSearchBenchmark {
  private static final int SIZE = 1 << 18;
  private static final long INITIAL = 3;

  @Param({"baseline", "candidate"}) public String implementation;
  @Param({"read", "disjoint", "hot", "range", "swmr", "swmrPaced"}) public String workload;

  private ConcurrentLongBTree candidate;
  private ConcurrentLongBTreeBaseline baseline;
  private long[] expected = new long[SIZE];
  private final ConcurrentMap<Integer, ThreadState> threads = new ConcurrentHashMap<>();
  private int[] insertionOrder;

  /** Performs an explicit fail-fast bytecode guard check without launching a benchmark. */
  public static void main(String[] args) {
    if (args.length != 1) throw new IllegalArgumentException("expected baseline or candidate");
    ConcurrentLongSearchBenchmark check = new ConcurrentLongSearchBenchmark();
    check.implementation = args[0];
    check.verifySelectedBytecode();
    System.out.println("bytecode guard passed: " + args[0]);
  }

  @Setup(Level.Trial)
  public void setup() {
    threads.clear();
    verifySelectedBytecode();
    if (implementation.equals("baseline")) baseline = new ConcurrentLongBTreeBaseline();
    else if (implementation.equals("candidate")) candidate = new ConcurrentLongBTree();
    else throw new IllegalArgumentException(implementation);
    insertionOrder = new int[SIZE];
    Arrays.fill(expected, INITIAL);
    for (int i = 0; i < SIZE; i++) insertionOrder[i] = i;
    SplittableRandom shuffle = new SplittableRandom(712367L);
    for (int i = SIZE - 1; i > 0; i--) {
      int swap = shuffle.nextInt(i + 1);
      int key = insertionOrder[i];
      insertionOrder[i] = insertionOrder[swap];
      insertionOrder[swap] = key;
    }
    for (int key : insertionOrder) put(key, INITIAL);
    if (size() != SIZE || get(0) != INITIAL || get(SIZE - 1) != INITIAL) {
      throw new AssertionError("bad benchmark fixture");
    }
    validateRangeFixture(0);
    validateRangeFixture((SIZE - 64) / 2);
    validateRangeFixture(SIZE - 64);
  }

  @Benchmark
  public long operation(ThreadState thread, ThreadParams params) {
    int tid = params.getThreadIndex();
    return switch (workload) {
      case "read" -> {
        int key = thread.next(SIZE);
        long value = get(key);
        if (value != expected[key]) throw new AssertionError("read mismatch");
        yield value;
      }
      case "disjoint" -> {
        int key = thread.next(SIZE / params.getThreadCount()) * params.getThreadCount() + tid;
        long value = ++thread.counts[key];
        put(key, value);
        yield value;
      }
      case "hot" -> {
        long value = (long) tid << 48 | ++thread.sequence;
        put(0, value);
        yield value;
      }
      case "range" -> {
        int start = thread.next(SIZE - 64 + 1);
        thread.rangeStart = start;
        thread.rangeNext = start;
        thread.rangeCount = 0;
        thread.rangeSum = 0;
        visit(start, start + 64, thread);
        if (thread.rangeCount != 64) throw new AssertionError("range count");
        yield thread.rangeSum;
      }
      case "swmr", "swmrPaced" -> {
        if (params.getThreadCount() != 8) throw new IllegalStateException("SWMR requires 8 threads");
        if (tid == 7) {
          if (workload.equals("swmrPaced")) awaitWriterSlot(thread);
          long value = INITIAL + ++thread.sequence;
          put(0, value);
          thread.writeOps++;
          yield value;
        }
        long value = get(0);
        if (value < INITIAL) throw new AssertionError("invalid SWMR value");
        thread.readOps++;
        yield value;
      }
      default -> throw new IllegalArgumentException(workload);
    };
  }

  @TearDown(Level.Trial)
  public void validate() {
    if (size() != SIZE) throw new AssertionError("cardinality changed");
    if (candidate != null) candidate.validate();
    if (workload.equals("disjoint")) {
      for (int key = 0; key < SIZE; key++) {
        long writes = 0;
        for (ThreadState thread : threads.values()) writes += thread.counts[key];
        if (get(key) != (writes == 0 ? INITIAL : writes)) {
          throw new AssertionError("disjoint write count mismatch at " + key);
        }
      }
      return;
    }
    for (int key = 0; key < SIZE; key++) {
      long value = get(key);
      if (workload.equals("hot") && key == 0) {
        if ((value >>> 48) >= 8 || (value & 0x0000ffffffffffffL) == 0) {
          throw new AssertionError("invalid hot writer value");
        }
      } else if (key != 0 || !(workload.equals("swmr") || workload.equals("swmrPaced"))) {
        if (value != expected[key]) throw new AssertionError("unexpected value at " + key);
      } else if (value < INITIAL) {
        throw new AssertionError("invalid SWMR final value");
      }
    }
  }

  private long get(long key) {
    if (baseline != null) return baseline.getOrDefault(key, Long.MIN_VALUE);
    if (candidate != null) return candidate.getOrDefault(key, Long.MIN_VALUE);
    throw new IllegalStateException("unsupported implementation");
  }

  private void put(long key, long value) {
    if (baseline != null) baseline.put(key, value);
    else if (candidate != null) candidate.put(key, value);
    else throw new IllegalStateException("unsupported implementation");
  }

  private int size() {
    if (baseline != null) return baseline.size();
    if (candidate != null) return candidate.size();
    throw new IllegalStateException("unsupported implementation");
  }

  private void visit(long from, long to, ThreadState action) {
    if (baseline != null) baseline.forEach(from, to, action);
    else if (candidate != null) candidate.forEach(from, to, action);
    else throw new IllegalStateException("unsupported implementation");
  }

  private void validateRangeFixture(int start) {
    ThreadState receiver = new ThreadState();
    receiver.rangeStart = start;
    receiver.rangeNext = start;
    receiver.rangeCount = 0;
    receiver.rangeSum = 0;
    visit(start, start + 64, receiver);
    if (receiver.rangeCount != 64 || receiver.rangeSum != 64L * INITIAL) {
      throw new AssertionError("range fixture mismatch at " + start);
    }
  }

  private void awaitWriterSlot(ThreadState thread) {
    final long intervalNanos = 2_000;
    final long maximumCatchupNanos = intervalNanos * 32;
    long deadline = thread.nextWriteDeadlineNanos;
    long now = System.nanoTime();
    if (now < deadline) {
      do {
        Thread.onSpinWait();
        now = System.nanoTime();
      } while (now < deadline);
    } else if (now - deadline > maximumCatchupNanos) {
      deadline = now;
    } else {
      deadline += ((now - deadline) / intervalNanos) * intervalNanos;
    }
    thread.nextWriteDeadlineNanos = deadline + intervalNanos;
  }

  private void verifySelectedBytecode() {
    Class<?> outer = implementation.equals("baseline")
        ? ConcurrentLongBTreeBaseline.class : ConcurrentLongBTree.class;
    Class<?>[] selected = {outer, outer.getDeclaredClasses()[0], outer.getDeclaredClasses()[1]};
    for (Class<?> type : selected) {
      String resource = type.getName().replace('.', '/') + ".class";
      String suffix = type == outer ? "" : "." + type.getSimpleName().toLowerCase();
      String property = "concurrent.expected." + implementation + suffix.replace('$', '.')
          .toLowerCase();
      String expectedLocation = System.getProperty("concurrent.expected." + implementation
          + ".location");
      String expectedSha = System.getProperty(property);
      if (expectedSha == null || expectedLocation == null) {
        throw new IllegalStateException("missing class guard properties for " + implementation);
      }
      try (InputStream input = type.getClassLoader().getResourceAsStream(resource)) {
        URL actualLocation = type.getProtectionDomain().getCodeSource().getLocation();
        String actualSha = input == null ? "missing" : sha256(input.readAllBytes());
        if (!expectedSha.equals(actualSha)) {
          throw new IllegalStateException("class guard mismatch: " + resource + " expected="
              + expectedSha + " actual=" + actualSha);
        }
        if (!actualLocation.toString().equals(expectedLocation)) {
          throw new IllegalStateException("class location mismatch: " + resource + " from "
              + actualLocation);
        }
        System.out.println("guard " + implementation + " " + resource + " sha256="
            + expectedSha + " source=" + actualLocation);
      } catch (IOException | NoSuchAlgorithmException e) {
        throw new IllegalStateException("unable to verify " + resource, e);
      }
    }
  }

  private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
    StringBuilder result = new StringBuilder();
    for (byte value : digest) result.append(String.format("%02x", value));
    return result.toString();
  }

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.OPERATIONS)
  public static class ThreadState implements ConcurrentLongBTree.EntryConsumer,
      ConcurrentLongBTreeBaseline.EntryConsumer {
    private int random;
    private long sequence;
    private long nextWriteDeadlineNanos;
    private long[] counts;
    private int rangeStart;
    private int rangeNext;
    private int rangeCount;
    private long rangeSum;
    public long readOps;
    public long writeOps;
    @Setup(Level.Trial)
    public void setup(ConcurrentLongSearchBenchmark shared, ThreadParams params) {
      random = 0x6d2b79f5 ^ (params.getThreadIndex() * 0x9e3779b9);
      if (random == 0) random = 1;
      counts = new long[SIZE];
      shared.threads.put(params.getThreadIndex(), this);
    }
    @Setup(Level.Iteration)
    public void resetCounters(ConcurrentLongSearchBenchmark shared) {
      readOps = 0;
      writeOps = 0;
      if (shared.workload.equals("swmrPaced")) nextWriteDeadlineNanos = System.nanoTime();
    }
    int next(int bound) {
      random ^= random << 13;
      random ^= random >>> 17;
      random ^= random << 5;
      return (random & Integer.MAX_VALUE) % bound;
    }
    @Override
    public void accept(long key, long value) {
      if (key != rangeNext++ || value != INITIAL || key < rangeStart
          || key >= rangeStart + 64) {
        throw new AssertionError("range snapshot mismatch at " + rangeStart);
      }
      rangeSum += value;
      rangeCount++;
    }
  }
}
