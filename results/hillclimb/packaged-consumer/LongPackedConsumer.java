import io.github.enderekici.packedbtree.LongBTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public final class LongPackedConsumer {
  public static void main(String[] args) {
    LongBTree actual = new LongBTree();
    TreeMap<Long, Long> expected = new TreeMap<>();
    put(actual, expected, Long.MIN_VALUE);
    put(actual, expected, Long.MAX_VALUE);
    put(actual, expected, -1);
    put(actual, expected, 0);
    put(actual, expected, 1);
    Random random = new Random(0x62a11L);
    for (int i = 0; i < 4096; i++) put(actual, expected, random.nextLong());

    for (Map.Entry<Long, Long> entry : expected.entrySet()) {
      if (actual.getOrDefault(entry.getKey(), Long.MIN_VALUE) != entry.getValue()) {
        throw new AssertionError("lookup " + entry.getKey());
      }
    }
    if (actual.getOrDefault(Long.MAX_VALUE, 0) != expected.get(Long.MAX_VALUE)) {
      throw new AssertionError("maximum key/value");
    }

    List<Long> rangeKeys = new ArrayList<>();
    List<Long> rangeValues = new ArrayList<>();
    actual.forEach(Long.MIN_VALUE, Long.MAX_VALUE,
        (key, value) -> { rangeKeys.add(key); rangeValues.add(value); });
    List<Long> expectedKeys = new ArrayList<>(expected.headMap(Long.MAX_VALUE, false).keySet());
    if (!rangeKeys.equals(expectedKeys)) throw new AssertionError("range keys");
    for (int i = 0; i < rangeValues.size(); i++) {
      if (!rangeValues.get(i).equals(expected.get(rangeKeys.get(i)))) {
        throw new AssertionError("range value " + rangeKeys.get(i));
      }
    }

    int index = 0;
    for (Long key : new ArrayList<>(expected.keySet())) {
      if ((index++ & 1) == 0) {
        if (!actual.remove(key)) throw new AssertionError("remove " + key);
        expected.remove(key);
      }
    }
    actual.validate();
    if (actual.size() != expected.size()) throw new AssertionError("size");
    for (Map.Entry<Long, Long> entry : expected.entrySet()) {
      if (actual.getOrDefault(entry.getKey(), Long.MIN_VALUE) != entry.getValue()) {
        throw new AssertionError("survivor " + entry.getKey());
      }
    }
    System.out.println("packed LongBTree packaged consumer passed");
  }

  private static void put(LongBTree actual, TreeMap<Long, Long> expected, long key) {
    long value = mix64(key ^ 0x74a1b3c5d6e7f809L);
    actual.put(key, value);
    expected.put(key, value);
  }

  private static long mix64(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }
}
