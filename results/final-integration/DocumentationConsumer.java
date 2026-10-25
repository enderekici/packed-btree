import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import io.github.enderekici.packedbtree.ConcurrentStringBTree;
import io.github.enderekici.packedbtree.LongBTree;
import io.github.enderekici.packedbtree.StringBTree;
import java.util.ArrayList;
import java.util.List;

/** Compiled against the packaged library to verify README API examples and contracts. */
public final class DocumentationConsumer {
  public static void main(String[] args) {
    LongBTree scores = new LongBTree();
    scores.put(Long.MIN_VALUE, 10);
    scores.put(7, 20);
    if (scores.getOrDefault(7, -1) != 20) throw new AssertionError("long lookup");
    List<Long> longKeys = new ArrayList<>();
    scores.forEach(Long.MIN_VALUE, 8, (key, value) -> longKeys.add(key));
    if (!longKeys.equals(List.of(Long.MIN_VALUE, 7L))) throw new AssertionError("long range");

    StringBTree<String> names = new StringBTree<>();
    names.put("ada", "Ada Lovelace");
    names.put("unknown", null);
    if (!"Ada Lovelace".equals(names.getOrDefault("ada", "fallback"))) {
      throw new AssertionError("string lookup");
    }
    if (names.getOrDefault("unknown", "fallback") != null) {
      throw new AssertionError("nullable value");
    }
    names.validate();

    ConcurrentLongBTree counters = new ConcurrentLongBTree();
    counters.put(1, 10);
    List<Long> snapshotLongKeys = new ArrayList<>();
    counters.forEach(0, 3, (key, value) -> {
      snapshotLongKeys.add(key);
      counters.put(2, 20);
    });
    if (!snapshotLongKeys.equals(List.of(1L)) || counters.getOrDefault(2, -1) != 20) {
      throw new AssertionError("concurrent long snapshot callback");
    }

    ConcurrentStringBTree<String> labels = new ConcurrentStringBTree<>();
    labels.put("a", "first");
    List<String> snapshotStringKeys = new ArrayList<>();
    labels.forEach("a", "z", (key, value) -> {
      snapshotStringKeys.add(key);
      labels.put("b", "second");
    });
    if (!snapshotStringKeys.equals(List.of("a"))
        || !"second".equals(labels.getOrDefault("b", "missing"))) {
      throw new AssertionError("concurrent String snapshot callback");
    }
    labels.validate();
    System.out.println("documentation consumer passed");
  }
}
