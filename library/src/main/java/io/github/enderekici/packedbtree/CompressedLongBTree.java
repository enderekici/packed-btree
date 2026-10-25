package io.github.enderekici.packedbtree;

/**
 * A primitive signed-long B-tree that keeps 16-bit routing suffixes alongside full keys.
 *
 * <p>Nodes whose keys share their signed high 48 bits route through the suffix array;
 * other nodes use the full keys. The full key arrays remain authoritative.
 */
public final class CompressedLongBTree extends LongBTree {
  /** Creates an empty tree with compressed routing enabled. */
  public CompressedLongBTree() {
    super();
  }

  @Override
  boolean usesCompressedRouting() {
    return true;
  }
}
