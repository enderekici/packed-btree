import io.github.enderekici.packedbtree.StringBTree;
import java.util.ArrayList;
import java.util.List;

public final class StringConsumer {
  public static void main(String[] args) {
    StringBTree<Object> tree = new StringBTree<>();
    Object identity = new Object();
    String supplementary = "\uD800\uDC00";
    tree.put("\uE000", identity);
    tree.put(supplementary, null);
    tree.put("a", "value");
    if (tree.getOrDefault("\uE000", null) != identity) throw new AssertionError("identity");
    if (tree.getOrDefault(supplementary, "fallback") != null) throw new AssertionError("null value");
    if (!"fallback".equals(tree.getOrDefault("missing", "fallback"))) throw new AssertionError("miss");

    List<String> keys = new ArrayList<>();
    tree.forEach("a", "\uE001", (key, value) -> keys.add(key));
    if (!keys.equals(List.of("a", supplementary, "\uE000"))) {
      throw new AssertionError("UTF-16 order/range: " + keys);
    }
    if (!tree.remove(supplementary) || tree.getOrDefault(supplementary, "gone") != "gone") {
      throw new AssertionError("delete");
    }
    tree.validate();
    if (tree.size() != 2) throw new AssertionError("size");

    StringBTree<String> covered = new StringBTree<>();
    for (int i = 0; i < 512; i++) {
      String key = "covered-" + String.format("%04d", i);
      covered.put(key, new String(("value-" + i).toCharArray()));
    }
    List<String> coveredKeys = new ArrayList<>();
    covered.forEach("covered-0000", "covered-0512", (key, value) -> {
      if (!value.equals("value-" + coveredKeys.size())) throw new AssertionError("covered value");
      coveredKeys.add(key);
    });
    if (coveredKeys.size() != 512) throw new AssertionError("covered range size");
    List<String> narrow = new ArrayList<>();
    covered.forEach("covered-0255", "covered-0256", (key, value) -> narrow.add(key));
    if (!narrow.equals(List.of("covered-0255"))) throw new AssertionError("narrow range");
    if (!covered.remove("covered-0255") || covered.size() != 511) {
      throw new AssertionError("covered delete");
    }
    covered.validate();
    System.out.println("StringBTree packaged consumer passed");
  }
}
