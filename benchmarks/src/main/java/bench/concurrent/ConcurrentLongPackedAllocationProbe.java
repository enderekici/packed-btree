package bench.concurrent;

import com.sun.management.ThreadMXBean;
import io.github.enderekici.packedbtree.ConcurrentLongBTreeC1Baseline;
import io.github.enderekici.packedbtree.ConcurrentLongBTreeC2Rejected;
import java.lang.management.ManagementFactory;

/** Single-writer allocated-byte probe for the packed key/value candidate and accepted C1. */
public final class ConcurrentLongPackedAllocationProbe {
  private static final int SIZE = 1 << 18;
  private static final int OPERATIONS = 100_000;
  private static final int WARMUP_OPERATIONS = 50_000;

  private ConcurrentLongPackedAllocationProbe() {}

  public static void main(String[] args) {
    if (args.length != 1 || !(args[0].equals("baseline") || args[0].equals("candidate"))) {
      throw new IllegalArgumentException("expected baseline or candidate");
    }
    String implementation = args[0];
    ConcurrentLongPackedBenchmark.main(new String[] {implementation});
    Tree tree = implementation.equals("baseline")
        ? new BaselineTree(new ConcurrentLongBTreeC1Baseline())
        : new CandidateTree(new ConcurrentLongBTreeC2Rejected());
    for (int key = 0; key < SIZE; key++) tree.put(key, 3);

    int[] keys = new int[OPERATIONS];
    long[] values = new long[OPERATIONS];
    long[] expected = new long[SIZE];
    java.util.Arrays.fill(expected, 3);
    for (int i = 0; i < OPERATIONS; i++) {
      keys[i] = (i * 8191) & (SIZE - 1);
      values[i] = 4L + i;
      expected[keys[i]] = values[i];
    }
    for (int i = 0; i < WARMUP_OPERATIONS; i++) {
      int index = i % OPERATIONS;
      tree.put(keys[index], values[index]);
    }

    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) {
      throw new IllegalStateException("thread allocated-byte counter unavailable");
    }
    bean.setThreadAllocatedMemoryEnabled(true);
    long threadId = Thread.currentThread().getId();
    long before = bean.getThreadAllocatedBytes(threadId);
    for (int i = 0; i < OPERATIONS; i++) tree.put(keys[i], values[i]);
    long allocated = bean.getThreadAllocatedBytes(threadId) - before;

    for (int key = 0; key < SIZE; key++) {
      if (tree.get(key) != expected[key]) throw new AssertionError("unexpected value at " + key);
    }
    tree.validate();
    System.out.printf("arm=%s operations=%d allocatedBytes=%d bytesPerPut=%.2f%n",
        implementation, OPERATIONS, allocated, (double) allocated / OPERATIONS);
  }

  private interface Tree {
    void put(long key, long value);
    long get(long key);
    void validate();
  }

  private record BaselineTree(ConcurrentLongBTreeC1Baseline tree) implements Tree {
    public void put(long key, long value) { tree.put(key, value); }
    public long get(long key) { return tree.getOrDefault(key, Long.MIN_VALUE); }
    public void validate() { tree.validate(); }
  }

  private record CandidateTree(ConcurrentLongBTreeC2Rejected tree) implements Tree {
    public void put(long key, long value) { tree.put(key, value); }
    public long get(long key) { return tree.getOrDefault(key, Long.MIN_VALUE); }
    public void validate() { tree.validate(); }
  }
}
