package io.github.enderekici.packedbtree;

/**
 * A compact, mutable, primitive long-to-long B-tree with signed key ordering.
 *
 * <p>This class is not thread-safe. Every signed {@code long} is a valid key and value;
 * no value is reserved as a missing-key sentinel. It does not implement {@link java.util.Map}.
 */
public class LongBTree {
  /** Receives a primitive key/value pair during an ordered range traversal. */
  @FunctionalInterface
  public interface EntryConsumer {
    /**
     * Receives an entry. Do not mutate the tree from this callback.
     *
     * @param key entry key
     * @param value entry value
     */
    void accept(long key, long value);
  }
  /** Finds the first key in a sorted node that is greater than or equal to a search key. */
  @FunctionalInterface
  public interface SearchStrategy {
    /**
     * Returns the lower-bound index in {@code keys[0..count)} using signed long ordering. The
     * supplied array is internal tree state: do not modify or retain it, and do not re-enter or
     * mutate the tree from this strategy.
     *
     * @param keys sorted key array
     * @param count number of active keys
     * @param key search key
     * @return first index whose key is greater than or equal to {@code key}, or {@code count}
     */
    int lowerBound(long[] keys, int count, long key);
  }

  private static final int DEGREE = 16;
  private static final int MAX = 2 * DEGREE - 1;
  private final SearchStrategy searchStrategy;
  private Node root;
  private int size;

  /** Creates a tree using the dependency-free scalar node search. */
  public LongBTree() {
    this.searchStrategy = null;
    this.root = createNode(true, usesCompressedRouting());
  }

  /**
   * Creates a tree using the supplied signed-long lower-bound search implementation.
   *
   * @param searchStrategy lower-bound implementation
   */
  public LongBTree(SearchStrategy searchStrategy) {
    this.searchStrategy = java.util.Objects.requireNonNull(searchStrategy, "searchStrategy");
    this.root = createNode(true, usesCompressedRouting());
  }

  boolean usesCompressedRouting() {
    return false;
  }

  private static Node createNode(boolean leaf, boolean compressedRouting) {
    return compressedRouting ? new CompressedNode(leaf) : new Node(leaf);
  }

  private static class Node {
    final long[] keys = new long[MAX];
    final long[] values = new long[MAX];
    final Node[] children;
    int count;

    Node(boolean leaf) {
      children = leaf ? null : new Node[MAX + 1];
    }

    void refreshRoutes() {}

    void validateRoutes() {}

    int index(long key) {
      int low = 0;
      int high = count;
      while (low < high) {
        int middle = (low + high) >>> 1;
        if (keys[middle] < key) low = middle + 1;
        else high = middle;
      }
      return low;
    }

    int index(SearchStrategy strategy, long key) {
      if (strategy == null) return index(key);
      return checkedIndex(strategy, key);
    }

    int checkedIndex(SearchStrategy strategy, long key) {
      int index = strategy.lowerBound(keys, count, key);
      if (index < 0 || index > count) throw new IllegalStateException("Invalid lower-bound index");
      return index;
    }
  }

  private static final class CompressedNode extends Node {
    final short[] routes = new short[MAX];
    long routePrefix;
    boolean compact;

    CompressedNode(boolean leaf) {
      super(leaf);
    }

    @Override
    void refreshRoutes() {
      compact = count > 0;
      if (!compact) return;
      routePrefix = keys[0] >> 16;
      if ((keys[count - 1] >> 16) != routePrefix) {
        compact = false;
        return;
      }
      for (int i = 0; i < count; i++) routes[i] = (short) keys[i];
    }

    @Override
    void validateRoutes() {
      boolean expectedCompact = count > 0 && (keys[0] >> 16) == (keys[count - 1] >> 16);
      if (compact != expectedCompact) throw new AssertionError("Stale compact routing flag");
      if (compact) {
        if (routePrefix != (keys[0] >> 16)) throw new AssertionError("Stale routing prefix");
        for (int i = 0; i < count; i++) {
          if (Short.toUnsignedInt(routes[i]) != ((int) keys[i] & 0xffff)) {
            throw new AssertionError("Stale routing suffix");
          }
        }
      }
    }

    @Override
    int index(long key) {
      if (compact) {
        long prefix = key >> 16;
        if (prefix < routePrefix) return 0;
        if (prefix > routePrefix) return count;
        int suffix = (int) key & 0xffff;
        int low = 0;
        int high = count;
        while (low < high) {
          int middle = (low + high) >>> 1;
          if (Short.toUnsignedInt(routes[middle]) < suffix) low = middle + 1;
          else high = middle;
        }
        return low;
      }
      return super.index(key);
    }

    @Override
    int index(SearchStrategy strategy, long key) {
      if (count == 0 || compact) return index(key);
      return strategy == null ? index(key) : checkedIndex(strategy, key);
    }
  }

  /**
   * Returns the number of mappings.
   *
   * @return number of distinct keys
   */
  public int size() {
    return size;
  }

  /**
   * Looks up a key without boxing.
   *
   * @param key key to find
   * @param fallback value returned if the key is absent
   * @return stored value or {@code fallback}
   */
  public long getOrDefault(long key, long fallback) {
    Node node = root;
    while (true) {
      int i = node.index(searchStrategy, key);
      if (i < node.count && node.keys[i] == key) return node.values[i];
      if (node.children == null) return fallback;
      node = node.children[i];
    }
  }

  /**
   * Adds a mapping or replaces the value of an existing key.
   *
   * @param key key to store
   * @param value value to associate with the key
   */
  public void put(long key, long value) {
    if (root.count == MAX) {
      Node parent = createNode(false, usesCompressedRouting());
      parent.children[0] = root;
      split(parent, 0);
      root = parent;
    }
    Node node = root;
    while (true) {
      int i = node.index(searchStrategy, key);
      if (i < node.count && node.keys[i] == key) {
        node.values[i] = value;
        return;
      }
      if (node.children == null) {
        System.arraycopy(node.keys, i, node.keys, i + 1, node.count - i);
        System.arraycopy(node.values, i, node.values, i + 1, node.count - i);
        node.keys[i] = key;
        node.values[i] = value;
        node.count++;
        node.refreshRoutes();
        size++;
        return;
      }
      if (node.children[i].count == MAX) {
        split(node, i);
        if (key == node.keys[i]) {
          node.values[i] = value;
          return;
        }
        if (key > node.keys[i]) i++;
      }
      node = node.children[i];
    }
  }

  private static void split(Node parent, int i) {
    Node left = parent.children[i];
    Node right = createNode(left.children == null, left instanceof CompressedNode);
    right.count = DEGREE - 1;
    System.arraycopy(left.keys, DEGREE, right.keys, 0, DEGREE - 1);
    System.arraycopy(left.values, DEGREE, right.values, 0, DEGREE - 1);
    if (left.children != null) {
      System.arraycopy(left.children, DEGREE, right.children, 0, DEGREE);
      java.util.Arrays.fill(left.children, DEGREE, MAX + 1, null);
    }
    System.arraycopy(parent.children, i + 1, parent.children, i + 2, parent.count - i);
    System.arraycopy(parent.keys, i, parent.keys, i + 1, parent.count - i);
    System.arraycopy(parent.values, i, parent.values, i + 1, parent.count - i);
    parent.keys[i] = left.keys[DEGREE - 1];
    parent.values[i] = left.values[DEGREE - 1];
    parent.children[i + 1] = right;
    left.count = DEGREE - 1;
    parent.count++;
    left.refreshRoutes();
    right.refreshRoutes();
    parent.refreshRoutes();
  }

  /**
   * Removes a mapping, rebalancing the tree as needed.
   *
   * @param key key to remove
   * @return whether the key existed
   */
  public boolean remove(long key) {
    boolean removed = delete(root, key);
    if (root.count == 0 && root.children != null) root = root.children[0];
    if (removed) size--;
    return removed;
  }

  private boolean delete(Node node, long key) {
    int i = node.index(searchStrategy, key);
    if (i < node.count && node.keys[i] == key) {
      if (node.children == null) {
        erase(node, i);
      } else if (node.children[i].count >= DEGREE) {
        Node predecessor = node.children[i];
        while (predecessor.children != null) {
          predecessor = predecessor.children[predecessor.count];
        }
        node.keys[i] = predecessor.keys[predecessor.count - 1];
        node.values[i] = predecessor.values[predecessor.count - 1];
        node.refreshRoutes();
        delete(node.children[i], node.keys[i]);
      } else if (node.children[i + 1].count >= DEGREE) {
        Node successor = node.children[i + 1];
        while (successor.children != null) successor = successor.children[0];
        node.keys[i] = successor.keys[0];
        node.values[i] = successor.values[0];
        node.refreshRoutes();
        delete(node.children[i + 1], node.keys[i]);
      } else {
        merge(node, i);
        delete(node.children[i], key);
      }
      return true;
    }
    if (node.children == null) return false;
    if (node.children[i].count == DEGREE - 1) {
      if (i > 0 && node.children[i - 1].count >= DEGREE) {
        borrowLeft(node, i);
      } else if (i < node.count && node.children[i + 1].count >= DEGREE) {
        borrowRight(node, i);
      } else {
        if (i == node.count) i--;
        merge(node, i);
      }
    }
    return delete(node.children[i], key);
  }

  private static void erase(Node node, int i) {
    System.arraycopy(node.keys, i + 1, node.keys, i, node.count - i - 1);
    System.arraycopy(node.values, i + 1, node.values, i, node.count - i - 1);
    node.count--;
    node.refreshRoutes();
  }

  private static void merge(Node parent, int i) {
    Node left = parent.children[i];
    Node right = parent.children[i + 1];
    int offset = left.count;
    left.keys[offset] = parent.keys[i];
    left.values[offset] = parent.values[i];
    System.arraycopy(right.keys, 0, left.keys, offset + 1, right.count);
    System.arraycopy(right.values, 0, left.values, offset + 1, right.count);
    if (left.children != null) {
      System.arraycopy(right.children, 0, left.children, offset + 1, right.count + 1);
    }
    left.count += right.count + 1;
    left.refreshRoutes();
    int oldCount = parent.count;
    erase(parent, i);
    System.arraycopy(parent.children, i + 2, parent.children, i + 1, oldCount - i - 1);
    parent.children[oldCount] = null;
    parent.refreshRoutes();
  }

  private static void borrowLeft(Node parent, int i) {
    Node child = parent.children[i];
    Node sibling = parent.children[i - 1];
    System.arraycopy(child.keys, 0, child.keys, 1, child.count);
    System.arraycopy(child.values, 0, child.values, 1, child.count);
    if (child.children != null) {
      System.arraycopy(child.children, 0, child.children, 1, child.count + 1);
      child.children[0] = sibling.children[sibling.count];
      sibling.children[sibling.count] = null;
    }
    child.keys[0] = parent.keys[i - 1];
    child.values[0] = parent.values[i - 1];
    parent.keys[i - 1] = sibling.keys[sibling.count - 1];
    parent.values[i - 1] = sibling.values[sibling.count - 1];
    sibling.count--;
    child.count++;
    child.refreshRoutes();
    sibling.refreshRoutes();
    parent.refreshRoutes();
  }

  private static void borrowRight(Node parent, int i) {
    Node child = parent.children[i];
    Node sibling = parent.children[i + 1];
    child.keys[child.count] = parent.keys[i];
    child.values[child.count] = parent.values[i];
    if (child.children != null) {
      child.children[child.count + 1] = sibling.children[0];
      System.arraycopy(sibling.children, 1, sibling.children, 0, sibling.count);
      sibling.children[sibling.count] = null;
    }
    parent.keys[i] = sibling.keys[0];
    parent.values[i] = sibling.values[0];
    erase(sibling, 0);
    child.count++;
    child.refreshRoutes();
    parent.refreshRoutes();
  }

  /**
   * Visits entries in ascending order in {@code [fromInclusive, toExclusive)}.
   *
   * <p>Equal or reversed bounds produce no visits. A half-open range cannot include
   * {@link Long#MAX_VALUE}; use {@link #getOrDefault(long, long)} for that key.
   * The callback must not mutate this tree.
   *
   * @param fromInclusive lower bound, included
   * @param toExclusive upper bound, excluded
   * @param visitor callback for each matching entry
   */
  public void forEach(long fromInclusive, long toExclusive, EntryConsumer visitor) {
    if (fromInclusive < toExclusive) {
      visit(root, fromInclusive, toExclusive, visitor);
    }
  }

  private void visit(Node node, long from, long to, EntryConsumer visitor) {
    int i = node.index(searchStrategy, from);
    if (node.children != null) visit(node.children[i], from, to, visitor);
    while (i < node.count && node.keys[i] < to) {
      visitor.accept(node.keys[i], node.values[i]);
      i++;
      if (node.children != null) visit(node.children[i], from, to, visitor);
    }
  }

  /**
   * Checks ordering, occupancy, child references, equal leaf depth and cardinality.
   *
   * <p>This diagnostic traverses the entire tree and is not intended for hot paths.
   *
   * @throws AssertionError if an invariant is violated
   */
  public void validate() {
    int[] leafDepth = {-1};
    int actual = validate(root, true, null, null, 0, leafDepth);
    if (actual != size) throw new AssertionError("Size " + actual + " != " + size);
  }

  private static int validate(Node node, boolean isRoot, Long low, Long high,
      int depth, int[] leafDepth) {
    node.validateRoutes();
    if (node.count > MAX || node.count < (isRoot ? 0 : DEGREE - 1)) {
      throw new AssertionError("Occupancy");
    }
    for (int i = 0; i < node.count; i++) {
      long key = node.keys[i];
      if ((i > 0 && node.keys[i - 1] >= key)
          || (low != null && key <= low) || (high != null && key >= high)) {
        throw new AssertionError("Ordering");
      }
    }
    int total = node.count;
    if (node.children == null) {
      if (leafDepth[0] == -1) leafDepth[0] = depth;
      if (leafDepth[0] != depth) throw new AssertionError("Leaf depth");
    } else {
      if (isRoot && node.count == 0) throw new AssertionError("Empty internal root");
      for (int i = 0; i <= node.count; i++) {
        if (node.children[i] == null) throw new AssertionError("Missing child");
        total += validate(node.children[i], false,
            i == 0 ? low : Long.valueOf(node.keys[i - 1]),
            i == node.count ? high : Long.valueOf(node.keys[i]), depth + 1, leafDepth);
      }
      for (int i = node.count + 1; i < node.children.length; i++) {
        if (node.children[i] != null) throw new AssertionError("Retained child");
      }
    }
    return total;
  }
}
