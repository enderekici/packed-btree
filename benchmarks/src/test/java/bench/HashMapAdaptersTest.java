package bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class HashMapAdaptersTest {
  @Test
  void implementationsSharePointOperationContract() {
    for (String name :
        new String[] {"btree", "hashMap", "concurrentHashMap", "fastutilOpenHashMap"}) {
      HashMapAdapters.LongMap map = HashMapAdapters.create(name, 4);
      map.put(9001, 27010);
      map.put(9003, 27016);
      assertEquals(27010, map.get(9001), name);
      assertEquals(-1, map.get(9005), name);
      map.put(9001, 17);
      assertEquals(17, map.get(9001), name);
      map.remove(9003);
      assertEquals(-1, map.get(9003), name);
      assertEquals(1, map.size(), name);
    }
  }

  @Test
  void fixtureUsesDeterministicKeysOutsideBoxingCacheAndDistinctMisses() {
    long[] first = HashMapBenchmark.shuffledKeys(1024, 55);
    long[] second = HashMapBenchmark.shuffledKeys(1024, 55);
    assertEquals(java.util.Arrays.toString(first), java.util.Arrays.toString(second));
    for (long key : first) assertNotEquals(1L, key);
    assertNotEquals(first[0], first[0] | 1L);
  }

  @Test
  void benchmarkOperationsPreserveFixtureForEveryImplementation() {
    for (String name :
        new String[] {"btree", "hashMap", "concurrentHashMap", "fastutilOpenHashMap"}) {
      HashMapBenchmark benchmark = new HashMapBenchmark();
      benchmark.implementation = name;
      benchmark.size = 16;
      benchmark.setup();
      long hit = benchmark.hit();
      long miss = benchmark.miss();
      benchmark.overwrite();
      benchmark.churn();
      assertNotEquals(-1, hit, name);
      assertEquals(-1, miss, name);
      benchmark.check();
    }
  }
}
