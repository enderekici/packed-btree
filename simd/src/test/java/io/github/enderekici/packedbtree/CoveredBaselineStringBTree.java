package io.github.enderekici.packedbtree;

import java.util.Arrays;
import java.util.Objects;

/**
 * A compact, mutable B-tree with String keys ordered by {@link String#compareTo(String)}.
 *
 * <p>This class is not thread-safe. Values may be null; keys may not be null. It does not
 * implement {@link java.util.Map}.
 *
 * @param <V> value type
 */
public final class CoveredBaselineStringBTree<V> {
  /**
   * Receives a key/value pair during an ordered range traversal.
   * @param <V> value type
   */
  @FunctionalInterface
  public interface EntryConsumer<V> {
    /** Receives an entry. Do not mutate the tree from this callback.
     * @param key entry key
     * @param value entry value
     */
    void accept(String key, V value);
  }

  private static final int DEGREE = 16;
  private static final int MAX = 2 * DEGREE - 1;
  private Node root = new Node(true);
  private int size;

  /** Creates an empty tree. */
  public CoveredBaselineStringBTree() {}

  private static final class Node {
    final String[] keys = new String[MAX];
    final Object[] values = new Object[MAX];
    final Node[] children;
    int count;

    Node(boolean leaf) { children = leaf ? null : new Node[MAX + 1]; }

    // Returns ~index on an exact match, otherwise the insertion position.
    int index(String key) {
      int low = 0;
      int high = count;
      while (low < high) {
        int middle = (low + high) >>> 1;
        int order = keys[middle].compareTo(key);
        if (order == 0) return ~middle;
        if (order < 0) low = middle + 1;
        else high = middle;
      }
      return low;
    }
  }

  /** Returns the number of mappings.
   * @return number of distinct keys
   */
  public int size() { return size; }

  /** Looks up a key, returning {@code fallback} when the key is absent.
   * @param key key to find
   * @param fallback value returned when absent
   * @return stored value, which may be null, or {@code fallback}
   */
  @SuppressWarnings("unchecked")
  public V getOrDefault(String key, V fallback) {
    Objects.requireNonNull(key, "key");
    Node node = root;
    while (true) {
      int i = node.index(key);
      if (i < 0) return (V) node.values[~i];
      if (node.children == null) return fallback;
      node = node.children[i];
    }
  }

  /** Adds a mapping or replaces the value of an existing key.
   * @param key key to store
   * @param value value to associate with the key
   */
  public void put(String key, V value) {
    Objects.requireNonNull(key, "key");
    if (root.count == MAX) {
      Node parent = new Node(false);
      parent.children[0] = root;
      split(parent, 0);
      root = parent;
    }
    Node node = root;
    while (true) {
      int i = node.index(key);
      if (i < 0) {
        node.values[~i] = value;
        return;
      }
      if (node.children == null) {
        System.arraycopy(node.keys, i, node.keys, i + 1, node.count - i);
        System.arraycopy(node.values, i, node.values, i + 1, node.count - i);
        node.keys[i] = key;
        node.values[i] = value;
        node.count++;
        size++;
        return;
      }
      if (node.children[i].count == MAX) {
        split(node, i);
        int order = key.compareTo(node.keys[i]);
        if (order == 0) { node.values[i] = value; return; }
        if (order > 0) i++;
      }
      node = node.children[i];
    }
  }

  private static void split(Node parent, int i) {
    Node left = parent.children[i];
    Node right = new Node(left.children == null);
    right.count = DEGREE - 1;
    System.arraycopy(left.keys, DEGREE, right.keys, 0, DEGREE - 1);
    System.arraycopy(left.values, DEGREE, right.values, 0, DEGREE - 1);
    if (left.children != null) {
      System.arraycopy(left.children, DEGREE, right.children, 0, DEGREE);
      Arrays.fill(left.children, DEGREE, MAX + 1, null);
    }
    System.arraycopy(parent.children, i + 1, parent.children, i + 2, parent.count - i);
    System.arraycopy(parent.keys, i, parent.keys, i + 1, parent.count - i);
    System.arraycopy(parent.values, i, parent.values, i + 1, parent.count - i);
    parent.keys[i] = left.keys[DEGREE - 1];
    parent.values[i] = left.values[DEGREE - 1];
    parent.children[i + 1] = right;
    Arrays.fill(left.keys, DEGREE - 1, MAX, null);
    Arrays.fill(left.values, DEGREE - 1, MAX, null);
    left.count = DEGREE - 1;
    parent.count++;
  }

  /** Removes a mapping and rebalances the tree.
   * @param key key to remove
   * @return whether the key existed
   */
  public boolean remove(String key) {
    Objects.requireNonNull(key, "key");
    boolean removed = delete(root, key);
    if (root.count == 0 && root.children != null) root = root.children[0];
    if (removed) size--;
    return removed;
  }

  private static boolean delete(Node node, String key) {
    int i = node.index(key);
    if (i < 0) {
      i = ~i;
      if (node.children == null) erase(node, i);
      else if (node.children[i].count >= DEGREE) {
        Node predecessor = node.children[i];
        while (predecessor.children != null) predecessor = predecessor.children[predecessor.count];
        node.keys[i] = predecessor.keys[predecessor.count - 1];
        node.values[i] = predecessor.values[predecessor.count - 1];
        delete(node.children[i], node.keys[i]);
      } else if (node.children[i + 1].count >= DEGREE) {
        Node successor = node.children[i + 1];
        while (successor.children != null) successor = successor.children[0];
        node.keys[i] = successor.keys[0];
        node.values[i] = successor.values[0];
        delete(node.children[i + 1], node.keys[i]);
      } else {
        merge(node, i);
        delete(node.children[i], key);
      }
      return true;
    }
    if (node.children == null) return false;
    if (node.children[i].count == DEGREE - 1) {
      if (i > 0 && node.children[i - 1].count >= DEGREE) borrowLeft(node, i);
      else if (i < node.count && node.children[i + 1].count >= DEGREE) borrowRight(node, i);
      else {
        if (i == node.count) i--;
        merge(node, i);
      }
    }
    return delete(node.children[i], key);
  }

  private static void erase(Node node, int i) {
    int oldCount = node.count;
    System.arraycopy(node.keys, i + 1, node.keys, i, oldCount - i - 1);
    System.arraycopy(node.values, i + 1, node.values, i, oldCount - i - 1);
    node.keys[oldCount - 1] = null;
    node.values[oldCount - 1] = null;
    node.count--;
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
      Arrays.fill(right.children, 0, right.count + 1, null);
    }
    left.count += right.count + 1;
    int oldCount = parent.count;
    erase(parent, i);
    System.arraycopy(parent.children, i + 2, parent.children, i + 1, oldCount - i - 1);
    parent.children[oldCount] = null;
    Arrays.fill(right.keys, 0, right.count, null);
    Arrays.fill(right.values, 0, right.count, null);
    right.count = 0;
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
    sibling.keys[sibling.count - 1] = null;
    sibling.values[sibling.count - 1] = null;
    sibling.count--;
    child.count++;
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
  }

  /** Visits entries in ascending order in {@code [fromInclusive, toExclusive)}.
   * Equal or reversed bounds produce no visits. The visitor must not mutate this tree.
   * @param fromInclusive lower bound, included
   * @param toExclusive upper bound, excluded
   * @param visitor callback for each matching entry
   */
  public void forEach(String fromInclusive, String toExclusive, EntryConsumer<? super V> visitor) {
    Objects.requireNonNull(fromInclusive, "fromInclusive");
    Objects.requireNonNull(toExclusive, "toExclusive");
    Objects.requireNonNull(visitor, "visitor");
    if (fromInclusive.compareTo(toExclusive) < 0) visit(root, fromInclusive, toExclusive, visitor);
  }

  @SuppressWarnings("unchecked")
  private static <V> void visit(Node node, String from, String to, EntryConsumer<? super V> visitor) {
    int i = node.index(from);
    if (i < 0) i = ~i;
    int end = node.index(to);
    if (end < 0) end = ~end;
    if (node.children != null) visit(node.children[i], from, to, visitor);
    while (i < end) {
      visitor.accept(node.keys[i], (V) node.values[i]);
      i++;
      if (node.children != null) visit(node.children[i], from, to, visitor);
    }
  }

  /** Checks ordering, occupancy, child references, equal leaf depth and cardinality. */
  public void validate() {
    int[] leafDepth = {-1};
    int actual = validate(root, true, null, null, 0, leafDepth);
    if (actual != size) throw new AssertionError("Size " + actual + " != " + size);
  }

  private static int validate(Node node, boolean isRoot, String low, String high,
      int depth, int[] leafDepth) {
    if (node.count > MAX || node.count < (isRoot ? 0 : DEGREE - 1)) throw new AssertionError("Occupancy");
    for (int i = 0; i < node.count; i++) {
      String key = node.keys[i];
      if (key == null || (i > 0 && node.keys[i - 1].compareTo(key) >= 0)
          || (low != null && key.compareTo(low) <= 0) || (high != null && key.compareTo(high) >= 0)) {
        throw new AssertionError("Ordering");
      }
    }
    for (int i = node.count; i < MAX; i++) {
      if (node.keys[i] != null || node.values[i] != null) throw new AssertionError("Retained entry");
    }
    int total = node.count;
    if (node.children == null) {
      if (leafDepth[0] == -1) leafDepth[0] = depth;
      if (leafDepth[0] != depth) throw new AssertionError("Leaf depth");
    } else {
      if (isRoot && node.count == 0) throw new AssertionError("Empty internal root");
      for (int i = 0; i <= node.count; i++) {
        if (node.children[i] == null) throw new AssertionError("Missing child");
        total += validate(node.children[i], false, i == 0 ? low : node.keys[i - 1],
            i == node.count ? high : node.keys[i], depth + 1, leafDepth);
      }
      for (int i = node.count + 1; i < node.children.length; i++) {
        if (node.children[i] != null) throw new AssertionError("Retained child");
      }
    }
    return total;
  }
}
