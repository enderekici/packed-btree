package bench;

import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/** Identical primitive-facing workload API; JDK adapters include required boxing. */
public final class Maps {
  public interface LongMap {
    long get(long key);
    void put(long key, long value);
    void remove(long key);
    long rangeSum(long from, long to);
    int size();
    Object storage();
  }

  public static LongMap create(String name) {
    return switch (name) {
      case "btree" -> new BTree();
      case "tree" -> new JdkTree(new TreeMap<>());
      case "skiplist" -> new JdkTree(new ConcurrentSkipListMap<>());
      case "primitiveAvl" -> new PrimitiveAvl();
      default -> throw new IllegalArgumentException(name);
    };
  }

  private static final class BTree implements LongMap, LongBTree.EntryConsumer {
    final LongBTree map = new LongBTree();
    long sum;
    public long get(long key) { return map.getOrDefault(key, -1); }
    public void put(long key, long value) { map.put(key, value); }
    public void remove(long key) { map.remove(key); }
    public int size() { return map.size(); }
    public Object storage() { return map; }
    public void accept(long key, long value) { sum += value; }
    public long rangeSum(long from, long to) {
      sum = 0;
      map.forEach(from, to, this);
      return sum;
    }
  }

  private static final class JdkTree implements LongMap {
    final NavigableMap<Long, Long> map;
    JdkTree(NavigableMap<Long, Long> map) { this.map = map; }
    public long get(long key) { return map.getOrDefault(key, -1L); }
    public void put(long key, long value) { map.put(key, value); }
    public void remove(long key) { map.remove(key); }
    public int size() { return map.size(); }
    public Object storage() { return map; }
    public long rangeSum(long from, long to) {
      long sum = 0;
      for (Map.Entry<Long, Long> entry : map.subMap(from, true, to, false).entrySet()) {
        sum += entry.getValue();
      }
      return sum;
    }
  }

  private static final class PrimitiveAvl implements LongMap {
    final Long2LongAVLTreeMap map = new Long2LongAVLTreeMap();
    PrimitiveAvl() { map.defaultReturnValue(-1); }
    public long get(long key) { return map.get(key); }
    public void put(long key, long value) { map.put(key, value); }
    public void remove(long key) { map.remove(key); }
    public int size() { return map.size(); }
    public Object storage() { return map; }
    public long rangeSum(long from, long to) {
      long sum = 0;
      var iterator = map.subMap(from, to).long2LongEntrySet().iterator();
      while (iterator.hasNext()) sum += iterator.next().getLongValue();
      return sum;
    }
  }

  public static long[] shuffledKeys(int size, long seed) {
    long[] keys = new long[size];
    for (int i = 0; i < size; i++) keys[i] = 2L * i + 1024;
    java.util.Random random = new java.util.Random(seed);
    for (int i = size - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      long key = keys[i];
      keys[i] = keys[j];
      keys[j] = key;
    }
    return keys;
  }
}
