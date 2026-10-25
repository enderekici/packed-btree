package bench;

import java.util.Random;

final class RoutingKeys {
  private RoutingKeys() {}

  static long[] create(String distribution, int size) {
    long[] keys = new long[size];
    for (int i = 0; i < size; i++) {
      keys[i] = switch (distribution) {
        case "sequential" -> i;
        case "fullWidth" -> mix64(i);
        case "commonPrefix" -> 0x1234567800000000L + i;
        default -> throw new IllegalArgumentException(distribution);
      };
    }
    Random random = new Random(62419);
    for (int i = size - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      long key = keys[i];
      keys[i] = keys[j];
      keys[j] = key;
    }
    return keys;
  }

  private static long mix64(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }
}
