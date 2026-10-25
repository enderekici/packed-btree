package io.github.enderekici.packedbtree.simd;

import io.github.enderekici.packedbtree.LongBTree;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/** Optional Java Vector API implementation of signed-long node lower-bound search. */
public final class VectorSearch implements LongBTree.SearchStrategy {
  private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

  @Override
  public int lowerBound(long[] keys, int count, long key) {
    int lanes = SPECIES.length();
    LongVector target = LongVector.broadcast(SPECIES, key);
    for (int base = 0; base < count; base += lanes) {
      int active = Math.min(lanes, count - base);
      VectorMask<Long> activeMask = SPECIES.indexInRange(0, active);
      LongVector values = LongVector.fromArray(SPECIES, keys, base, activeMask);
      VectorMask<Long> atOrAbove = values.compare(VectorOperators.GE, target, activeMask);
      if (atOrAbove.anyTrue()) return base + atOrAbove.firstTrue();
    }
    return count;
  }
}
