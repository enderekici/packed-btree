import io.github.enderekici.packedbtree.LongBTree;

public final class DefaultConsumer {
  public static void main(String[] args) {
    LongBTree tree = new LongBTree();
    for (long i = -1024; i < 1024; i++) tree.put(i, i * 3);
    if (tree.size() != 2048 || tree.getOrDefault(Long.MIN_VALUE, 7) != 7) throw new AssertionError();
    for (long i = -1024; i < 1024; i++) {
      if (tree.getOrDefault(i, Long.MIN_VALUE) != i * 3) throw new AssertionError("lookup " + i);
    }
    long[] pairs = {0, 0};
    tree.forEach(-32, 32, (key, value) -> { pairs[0]++; pairs[1] += value; });
    if (pairs[0] != 64 || pairs[1] != -96) throw new AssertionError("range");
    for (long i = -1024; i < 1024; i += 2) if (!tree.remove(i)) throw new AssertionError("remove " + i);
    tree.validate();
    if (tree.size() != 1024 || tree.getOrDefault(17, 0) != 51) throw new AssertionError("final");
    System.out.println("default packaged consumer passed");
  }
}
