package bench;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

final class SearchScreenFixture {
  static final int QUERY_COUNT = 4096;
  private SearchScreenFixture() {}

  static long[] keys(String distribution, int size) {
    long[] keys = new long[size];
    for (int i = 0; i < size; i++) {
      keys[i] = switch (distribution) {
        case "dense" -> 2L * i + 1024;
        case "fullWidth" -> mix64(i);
        default -> throw new IllegalArgumentException(distribution);
      };
    }
    shuffle(keys, 42L);
    return keys;
  }

  static long[] absentAlternates(long[] keys) {
    HashSet<Long> used = new HashSet<>(keys.length * 2);
    for (long key : keys) used.add(key);
    long[] absent = new long[keys.length];
    for (int i = 0; i < keys.length; i++) {
      long candidate = keys[i] ^ 1L;
      while (!used.add(candidate)) candidate += 2;
      absent[i] = candidate;
    }
    return absent;
  }

  static long[][] ranges(long[] keys) {
    long[] sorted = keys.clone();
    Arrays.sort(sorted);
    long[][] bounds = new long[2][QUERY_COUNT];
    Random random = new Random(0x64L);
    for (int i = 0; i < QUERY_COUNT; i++) {
      int start = random.nextInt(sorted.length - 64);
      bounds[0][i] = sorted[start];
      bounds[1][i] = sorted[start + 64];
    }
    return bounds;
  }

  private static void shuffle(long[] values, long seed) {
    Random random = new Random(seed);
    for (int i = values.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      long value = values[i];
      values[i] = values[j];
      values[j] = value;
    }
  }

  private static long mix64(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }
}
