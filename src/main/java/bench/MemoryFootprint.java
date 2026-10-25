package bench;

import org.openjdk.jol.info.GraphLayout;

public final class MemoryFootprint {
  public static void main(String[] args) {
    int size = Integer.parseInt(args[1]);
    Maps.LongMap map = Maps.create(args[0]);
    for (long key : Maps.shuffledKeys(size, 42)) map.put(key, key * 3);
    GraphLayout graph = GraphLayout.parseInstance(map.storage());
    System.out.printf("%s,%d,%d,%.2f,%d%n", args[0], size, graph.totalSize(),
        (double) graph.totalSize() / size, graph.totalCount());
  }
}
