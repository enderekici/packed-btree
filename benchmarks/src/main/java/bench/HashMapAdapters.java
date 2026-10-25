package bench;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Point-operation adapters for boxed JDK maps and a primitive fastutil control. */
final class HashMapAdapters {
  interface LongMap {
    long get(long key);
    void put(long key, long value);
    void remove(long key);
    int size();
  }

  private HashMapAdapters() {}

  static LongMap create(String implementation, int expectedSize) {
    return switch (implementation) {
      case "btree" -> new BTreeAdapter();
      case "hashMap" -> new BoxedAdapter(new HashMap<>(capacityFor(expectedSize)));
      case "concurrentHashMap" ->
          new BoxedAdapter(new ConcurrentHashMap<>(capacityFor(expectedSize)));
      case "fastutilOpenHashMap" -> new FastutilAdapter(expectedSize);
      default -> throw new IllegalArgumentException("Unknown implementation: " + implementation);
    };
  }

  private static int capacityFor(int expectedSize) {
    return (int) Math.ceil(expectedSize / 0.75d);
  }

  private static final class BTreeAdapter implements LongMap {
    private final io.github.enderekici.packedbtree.LongBTree map =
        new io.github.enderekici.packedbtree.LongBTree();
    public long get(long key) { return map.getOrDefault(key, -1L); }
    public void put(long key, long value) { map.put(key, value); }
    public void remove(long key) { map.remove(key); }
    public int size() { return map.size(); }
  }

  private static final class BoxedAdapter implements LongMap {
    private final Map<Long, Long> map;
    private BoxedAdapter(Map<Long, Long> map) { this.map = map; }
    public long get(long key) { return map.getOrDefault(key, -1L); }
    public void put(long key, long value) { map.put(key, value); }
    public void remove(long key) { map.remove(key); }
    public int size() { return map.size(); }
  }

  private static final class FastutilAdapter implements LongMap {
    private final Long2LongOpenHashMap map;
    private FastutilAdapter(int expectedSize) {
      map = new Long2LongOpenHashMap(expectedSize, 0.75f);
      map.defaultReturnValue(-1L);
    }
    public long get(long key) { return map.get(key); }
    public void put(long key, long value) { map.put(key, value); }
    public void remove(long key) { map.remove(key); }
    public int size() { return map.size(); }
  }
}
