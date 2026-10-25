package bench.singleconcurrent;

import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import io.github.enderekici.packedbtree.ConcurrentStringBTree;
import io.github.enderekici.packedbtree.LongBTree;
import io.github.enderekici.packedbtree.StringBTree;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;

/** Retained reachable graph of one selected B-tree only; query fixtures are excluded. */
public final class TreeGraphFootprint {
  private TreeGraphFootprint() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 4) throw new IllegalArgumentException("arm type shape size");
    String arm = args[0], type = args[1], shape = args[2];
    int size = Integer.parseInt(args[3]);
    if (!arm.equals("mutable") && !arm.equals("concurrent"))
      throw new IllegalArgumentException("arm=" + arm);
    Object tree;
    if (type.equals("long")) {
      if (arm.equals("mutable")) {
        guard(arm, type, LongBTree.class, "outer");
        guard(arm, type, Class.forName(LongBTree.class.getName() + "$Node"), "node");
        tree = new LongBTree();
      } else {
        guard(arm, type, ConcurrentLongBTree.class, "outer");
        guard(arm, type, Class.forName(ConcurrentLongBTree.class.getName() + "$Node"), "node");
        guard(arm, type, Class.forName(ConcurrentLongBTree.class.getName() + "$State"), "state");
        tree = new ConcurrentLongBTree();
      }
      LongAccess map = new LongAccess(tree);
      int[] order = permutation(size, 7319);
      for (int i : order) map.put(2L * i + 1_000_000L, ((long) i << 1));
      if (map.size() != size) throw new AssertionError("cardinality");
      map.validate();
    } else if (type.equals("string")) {
      if (!shape.equals("random") && !shape.equals("prefix")) throw new IllegalArgumentException(shape);
      if (arm.equals("mutable")) {
        guard(arm, type, StringBTree.class, "outer");
        guard(arm, type, Class.forName(StringBTree.class.getName() + "$Node"), "node");
        tree = new StringBTree<>();
      } else {
        guard(arm, type, ConcurrentStringBTree.class, "outer");
        guard(arm, type, Class.forName(ConcurrentStringBTree.class.getName() + "$Node"), "node");
        guard(arm, type, Class.forName(ConcurrentStringBTree.class.getName() + "$State"), "state");
        tree = new ConcurrentStringBTree<>();
      }
      StringAccess map = new StringAccess(tree);
      String[] keys = new String[size];
      for (int i = 0; i < size; i++) {
        String encoded = String.format("%08x", mix(i));
        keys[i] = shape.equals("prefix") ? "shared-prefix-that-is-long-" + encoded : encoded;
      }
      Arrays.sort(keys);
      int[] order = permutation(size, 7319);
      for (int i : order) map.put(keys[i], new String(("value-a-" + i).toCharArray()));
      if (map.size() != size) throw new AssertionError("cardinality");
      map.validate();
    } else throw new IllegalArgumentException(type);

    GraphLayout graph = GraphLayout.parseInstance(tree);
    System.out.println("VM " + VM.current().details());
    System.out.println("GRAPH arm=" + arm + " type=" + type + " shape=" + shape + " size=" + size
        + " bytes=" + graph.totalSize() + " objects=" + graph.totalCount()
        + " bytesPerMapping=" + (double) graph.totalSize() / size + " activeSnapshots=0");
    System.out.println(graph.toFootprint());
  }

  private static void guard(String arm, String type, Class<?> cls, String part) throws Exception {
    String prefix = "tree.guard." + arm + "." + type;
    String expected = System.getProperty(prefix + "." + part);
    String expectedLocation = System.getProperty("tree.guard." + arm + ".location");
    URL location = cls.getProtectionDomain().getCodeSource().getLocation();
    if (expected == null || !expected.matches("[0-9a-f]{64}") || expectedLocation == null
        || !new File(location.toURI()).getCanonicalFile()
            .equals(new File(expectedLocation).getCanonicalFile()))
      throw new IllegalStateException("class origin guard " + cls.getName());
    byte[] bytes;
    try (InputStream in = cls.getResourceAsStream("/" + cls.getName().replace('.', '/') + ".class")) {
      bytes = in.readAllBytes();
    }
    String actual = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    System.out.println("CLASS-GUARD " + cls.getName() + " codeSource=" + location + " sha256=" + actual);
    if (!expected.equals(actual)) throw new IllegalStateException("class SHA mismatch " + cls.getName());
  }

  private static String hex(byte[] bytes) {
    StringBuilder b = new StringBuilder(bytes.length * 2);
    for (byte x : bytes) b.append(String.format("%02x", x & 255));
    return b.toString();
  }
  private static int mix(int value) {
    value ^= value >>> 16; value *= 0x7feb352d; value ^= value >>> 15;
    value *= 0x846ca68b; return value ^ (value >>> 16);
  }
  private static int[] permutation(int n, long seed) {
    int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i;
    Random r = new Random(seed);
    for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1), x = a[i]; a[i] = a[j]; a[j] = x; }
    return a;
  }

  private record LongAccess(LongBTree mutable, ConcurrentLongBTree concurrent) {
    LongAccess(Object map) { this(map instanceof LongBTree t ? t : null,
        map instanceof ConcurrentLongBTree t ? t : null); }
    void put(long k, long v) { if (mutable != null) mutable.put(k, v); else concurrent.put(k, v); }
    int size() { return mutable != null ? mutable.size() : concurrent.size(); }
    void validate() { if (mutable != null) mutable.validate(); else concurrent.validate(); }
  }
  private record StringAccess(StringBTree<String> mutable, ConcurrentStringBTree<String> concurrent) {
    @SuppressWarnings("unchecked") StringAccess(Object map) {
      this(map instanceof StringBTree<?> t ? (StringBTree<String>) t : null,
          map instanceof ConcurrentStringBTree<?> t ? (ConcurrentStringBTree<String>) t : null);
    }
    void put(String k, String v) { if (mutable != null) mutable.put(k, v); else concurrent.put(k, v); }
    int size() { return mutable != null ? mutable.size() : concurrent.size(); }
    void validate() { if (mutable != null) mutable.validate(); else concurrent.validate(); }
  }
}
