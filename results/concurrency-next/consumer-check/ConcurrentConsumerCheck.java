import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import io.github.enderekici.packedbtree.ConcurrentStringBTree;
import java.util.ArrayList;
import java.util.List;

public final class ConcurrentConsumerCheck {
  private ConcurrentConsumerCheck() {}

  public static void main(String[] args) {
    checkLongSnapshot();
    checkStringContracts();
    System.out.println("packaged consumer checks passed: Long snapshot/reentry; String null/fallback, "
        + "identity, UTF-16 ordering, snapshot mutation");
  }

  private static void checkLongSnapshot() {
    ConcurrentLongBTree tree = new ConcurrentLongBTree();
    tree.put(-1, 10);
    tree.put(0, 20);
    tree.put(1, 30);
    List<String> seen = new ArrayList<>();
    tree.forEach(-1, 2, (key, value) -> {
      seen.add(key + ":" + value);
      if (key == -1) {
        tree.remove(0);
        tree.put(2, 40);
      }
    });
    if (!seen.equals(List.of("-1:10", "0:20", "1:30"))) {
      throw new AssertionError("Long range did not keep its original snapshot: " + seen);
    }
    if (tree.getOrDefault(0, -1) != -1 || tree.getOrDefault(2, -1) != 40 || tree.size() != 3) {
      throw new AssertionError("Long callback mutation missing");
    }
    tree.validate();
  }

  private static void checkStringContracts() {
    ConcurrentStringBTree<Object> tree = new ConcurrentStringBTree<>();
    Object fallback = new Object();
    Object stored = new Object();
    String original = new String("identity");
    String equalButDistinct = new String("identity");
    tree.put(original, null);
    if (tree.getOrDefault(original, fallback) != null) {
      throw new AssertionError("present null value was replaced by fallback");
    }
    if (tree.getOrDefault("missing", fallback) != fallback) {
      throw new AssertionError("absent key did not return exact fallback");
    }
    if (!tree.remove(equalButDistinct) || tree.remove(equalButDistinct)) {
      throw new AssertionError("remove did not report key presence exactly once");
    }
    tree.put(original, stored);
    tree.put(equalButDistinct, fallback);
    String[] retained = {null};
    tree.forEach("identity", "identity\u0000", (key, value) -> retained[0] = key);
    if (retained[0] != original || tree.getOrDefault(original, null) != fallback) {
      throw new AssertionError("compare-equal overwrite changed key identity or value");
    }

    String nul = "\u0000";
    String ascii = "A";
    String high = "\ud800";
    String pair = "\ud800\udc00";
    tree.put(nul, null);
    tree.put(ascii, stored);
    tree.put(high, "high-surrogate");
    tree.put(pair, "surrogate-pair");
    List<String> seen = new ArrayList<>();
    tree.forEach(nul, "\ue000", (key, value) -> {
      seen.add(key);
      if (key.equals(nul)) {
        tree.remove(nul);
        tree.remove(ascii);
        tree.put("Z", "inserted-during-callback");
        if (tree.getOrDefault("Z", null) == null) throw new AssertionError("reentrant get");
      }
    });
    if (!seen.equals(List.of(nul, ascii, "identity", high, pair))) {
      throw new AssertionError("String range/order/snapshot mismatch: " + seen);
    }
    if (tree.size() != 4 || tree.getOrDefault("Z", null) == null) {
      throw new AssertionError("String callback mutation missing");
    }
    tree.validate();
  }
}
