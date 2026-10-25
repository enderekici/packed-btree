package bench;

import java.util.List;
import org.junit.jupiter.api.Test;

final class MapsTest {
  @Test
  void allAdaptersHonorTheWorkloadContract() {
    for (String name : List.of("btree", "tree", "skiplist", "primitiveAvl")) {
      Maps.LongMap map = Maps.create(name);
      if (map.get(1024) != -1 || map.size() != 0) throw new AssertionError(name);
      map.put(1024, 3);
      map.put(1026, 5);
      map.put(1024, 7);
      if (map.get(1024) != 7 || map.rangeSum(1024, 1026) != 7
          || map.rangeSum(1024, 1027) != 12 || map.size() != 2) {
        throw new AssertionError("Adapter update/range " + name);
      }
      map.remove(1024);
      map.remove(1024);
      if (map.get(1024) != -1 || map.get(1026) != 5 || map.size() != 1) {
        throw new AssertionError("Adapter removal " + name);
      }
    }
  }
}
