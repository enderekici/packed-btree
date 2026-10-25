package bench;

import org.openjdk.jol.info.GraphLayout;

public final class MemoryFootprint {
  public static void main(String[] args) {
    int size = Integer.parseInt(args[1]);
    String distribution = args.length > 2 ? args[2] : "sequential";
    Maps.LongMap map = Maps.create(args[0]);
    for (long key : RoutingKeys.create(distribution, size)) map.put(key, key * 3);
    GraphLayout graph = GraphLayout.parseInstance(map.storage());
    System.out.printf("%s,%s,%d,%d,%.2f,%d%n", args[0], distribution, size,
        graph.totalSize(), (double) graph.totalSize() / size, graph.totalCount());
  }
}
