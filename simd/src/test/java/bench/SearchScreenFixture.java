package bench;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HexFormat;

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

  static void verifyCandidateClasses(Class<?> treeClass) {
    Class<?> nodeClass = nodeClass(treeClass);
    verifyLoadedClass("candidate", "tree", treeClass);
    verifyLoadedClass("candidate", "node", nodeClass);
  }

  static void verifyBaselineClasses(Class<?> treeClass) {
    Class<?> nodeClass = nodeClass(treeClass);
    verifyLoadedClass("baseline", "tree", treeClass);
    verifyLoadedClass("baseline", "node", nodeClass);
  }

  private static Class<?> nodeClass(Class<?> treeClass) {
    try {
      return Class.forName(treeClass.getName() + "$Node", false, treeClass.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Cannot locate node class for " + treeClass, e);
    }
  }

  private static void verifyLoadedClass(String arm, String kind, Class<?> type) {
    String prefix = "bench.expected." + arm + "." + kind;
    String expectedLocation = expected(prefix + ".location");
    String expectedHash = expected(prefix + ".sha256");
    if (expectedLocation == null || expectedHash == null) {
      throw new IllegalStateException("Missing runtime class fingerprint properties for " + prefix);
    }
    try {
      URL source = type.getProtectionDomain().getCodeSource().getLocation();
      String resourceName = "/" + type.getName().replace('.', '/') + ".class";
      URL resource = type.getResource(resourceName);
      String hash;
      try (InputStream input = type.getResourceAsStream(resourceName)) {
        if (input == null) throw new IllegalStateException("Cannot read class bytes for " + type);
        hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
      }
      String actualLocation = java.nio.file.Path.of(source.toURI()).toRealPath().toString();
      String expectedReal = java.nio.file.Path.of(expectedLocation).toRealPath().toString();
      System.out.printf("hillclimb-class %s %s source=%s resource=%s sha256=%s%n",
          arm, kind, actualLocation, resource, hash);
      if (!actualLocation.equals(expectedReal) || !hash.equalsIgnoreCase(expectedHash)) {
        throw new IllegalStateException("Unexpected loaded class for " + prefix + ": "
            + actualLocation + " " + hash);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Could not verify runtime class for " + prefix, e);
    }
  }

  private static String expected(String name) {
    String value = System.getProperty(name);
    return value != null ? value : System.getenv(name.toUpperCase().replace('.', '_'));
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
