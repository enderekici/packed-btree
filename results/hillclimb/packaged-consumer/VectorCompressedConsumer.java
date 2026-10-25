import io.github.enderekici.packedbtree.CompressedLongBTree;
import io.github.enderekici.packedbtree.LongBTree;
import io.github.enderekici.packedbtree.simd.VectorSearch;

public final class VectorCompressedConsumer {
  public static void main(String[] args) {
    LongBTree scalar = new LongBTree(new VectorSearch());
    CompressedLongBTree compressed = new CompressedLongBTree(new VectorSearch());
    for (int i = 0; i < 4096; i++) {
      long key = (1L << 40) + i;
      scalar.put(key, key ^ 9);
      compressed.put(key, key ^ 9);
    }
    for (int i = 0; i < 4096; i++) {
      long key = (1L << 40) + i;
      if (scalar.getOrDefault(key, 0) != (key ^ 9) || compressed.getOrDefault(key, 0) != (key ^ 9)) throw new AssertionError("compact lookup");
    }
    long outside = -(1L << 50);
    scalar.put(outside, 1234);
    compressed.put(outside, 1234);
    if (scalar.getOrDefault(outside, 0) != 1234 || compressed.getOrDefault(outside, 0) != 1234) throw new AssertionError("fallback lookup");
    scalar.validate();
    compressed.validate();
    System.out.println("vector and compressed fallback consumer passed");
  }
}
