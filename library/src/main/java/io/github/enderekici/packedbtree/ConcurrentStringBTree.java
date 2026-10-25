package io.github.enderekici.packedbtree;

import java.util.Arrays;
import java.util.Objects;

/**
 * A concurrent B-tree with String keys ordered by {@link String#compareTo(String)}.
 *
 * <p>Writers are serialized. Point readers capture a volatile immutable-root snapshot and do not
 * acquire the writer lock. Each operation is linearizable; separate calls do not share one atomic
 * snapshot. Range traversal retains one root for the complete traversal and permits callbacks to
 * re-enter or mutate the live tree. A slow callback retains its old root until it returns.
 * Values may be null and are retained by reference; snapshots do not copy mutable value objects.
 * Keys may not be null. This class does not implement {@link java.util.Map}.
 *
 * @param <V> value type
 */
public final class ConcurrentStringBTree<V> {
  /** Receives a key/value pair during an ordered range traversal. */
  @FunctionalInterface
  public interface EntryConsumer<V> {
    /**
     * Receives an entry from a stable snapshot. The callback may mutate the live tree.
     *
     * @param key stored key
     * @param value stored value, possibly null
     */
    void accept(String key, V value);
  }

  private static final int DEGREE = 16;
  private static final int MAX = 2 * DEGREE - 1;
  private volatile State state = new State(new Node(true), 0);

  private record State(Node root, int size) {}

  private static final class Node {
    final String[] keys = new String[MAX];
    final Object[] values = new Object[MAX];
    final Node[] children;
    int count;

    Node(boolean leaf) {
      children = leaf ? null : new Node[MAX + 1];
    }

    // Returns ~index for an exact match, otherwise the insertion position.
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

  /** Creates an empty concurrent B-tree. */
  public ConcurrentStringBTree() {}

  /** Returns the number of mappings in one published state. */
  public int size() {
    return state.size;
  }

  /**
   * Looks up a key, returning {@code fallback} only when that key is absent.
   *
   * @param key key to find; must not be null
   * @param fallback value returned when absent
   * @return stored value, possibly null, or {@code fallback}
   * @throws NullPointerException if {@code key} is null
   */
  @SuppressWarnings("unchecked")
  public V getOrDefault(String key, V fallback) {
    Objects.requireNonNull(key, "key");
    Node node = state.root;
    while (true) {
      int i = node.index(key);
      if (i < 0) return (V) node.values[~i];
      if (node.children == null) return fallback;
      node = node.children[i];
    }
  }

  /**
   * Adds a mapping or replaces an existing value.
   *
   * <p>Replacing a key that compares equal retains the originally stored key object.
   *
   * @param key key to store; must not be null
   * @param value value to associate with the key, possibly null
   * @throws NullPointerException if {@code key} is null
   */
  public synchronized void put(String key, V value) {
    Objects.requireNonNull(key, "key");
    State before = state;
    Node root = copy(before.root);
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
        state = new State(root, before.size);
        return;
      }
      if (node.children == null) {
        System.arraycopy(node.keys, i, node.keys, i + 1, node.count - i);
        System.arraycopy(node.values, i, node.values, i + 1, node.count - i);
        node.keys[i] = key;
        node.values[i] = value;
        node.count++;
        state = new State(root, before.size + 1);
        return;
      }
      node.children[i] = copy(node.children[i]);
      if (node.children[i].count == MAX) {
        split(node, i);
        int order = key.compareTo(node.keys[i]);
        if (order == 0) {
          node.values[i] = value;
          state = new State(root, before.size);
          return;
        }
        if (order > 0) i++;
      }
      node = node.children[i];
    }
  }

  private static Node copy(Node source) {
    Node target = new Node(source.children == null);
    target.count = source.count;
    System.arraycopy(source.keys, 0, target.keys, 0, source.count);
    System.arraycopy(source.values, 0, target.values, 0, source.count);
    if (source.children != null) {
      System.arraycopy(source.children, 0, target.children, 0, source.count + 1);
    }
    return target;
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

  /**
   * Removes a mapping and rebalances the writer-private path.
   *
   * @param key key to remove; must not be null
   * @return whether the key existed, including when its value was null
   * @throws NullPointerException if {@code key} is null
   */
  public synchronized boolean remove(String key) {
    Objects.requireNonNull(key, "key");
    State before = state;
    Node root = copy(before.root);
    boolean removed = delete(root, key);
    if (root.count == 0 && root.children != null) root = root.children[0];
    if (removed) state = new State(root, before.size - 1);
    return removed;
  }

  private static boolean delete(Node node, String key) {
    int i = node.index(key);
    if (i < 0) {
      i = ~i;
      if (node.children == null) {
        erase(node, i);
      } else if (node.children[i].count >= DEGREE) {
        node.children[i] = copy(node.children[i]);
        Node predecessor = node.children[i];
        while (predecessor.children != null) {
          predecessor = predecessor.children[predecessor.count];
        }
        String replacementKey = predecessor.keys[predecessor.count - 1];
        node.keys[i] = replacementKey;
        node.values[i] = predecessor.values[predecessor.count - 1];
        delete(node.children[i], replacementKey);
      } else if (node.children[i + 1].count >= DEGREE) {
        node.children[i + 1] = copy(node.children[i + 1]);
        Node successor = node.children[i + 1];
        while (successor.children != null) successor = successor.children[0];
        String replacementKey = successor.keys[0];
        node.keys[i] = replacementKey;
        node.values[i] = successor.values[0];
        delete(node.children[i + 1], replacementKey);
      } else {
        node.children[i] = copy(node.children[i]);
        merge(node, i);
        delete(node.children[i], key);
      }
      return true;
    }
    if (node.children == null) return false;
    node.children[i] = copy(node.children[i]);
    if (node.children[i].count == DEGREE - 1) {
      if (i > 0 && node.children[i - 1].count >= DEGREE) {
        node.children[i - 1] = copy(node.children[i - 1]);
        borrowLeft(node, i);
      } else if (i < node.count && node.children[i + 1].count >= DEGREE) {
        node.children[i + 1] = copy(node.children[i + 1]);
        borrowRight(node, i);
      } else {
        if (i == node.count) i--;
        node.children[i] = copy(node.children[i]);
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
    }
    left.count += right.count + 1;
    int oldCount = parent.count;
    erase(parent, i);
    System.arraycopy(parent.children, i + 2, parent.children, i + 1, oldCount - i - 1);
    parent.children[oldCount] = null;
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
    erase(sibling, sibling.count - 1);
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

  /**
   * Visits entries in ascending order in the half-open range
   * {@code [fromInclusive, toExclusive)} from one stable snapshot.
   *
   * <p>Equal or reversed bounds produce no visits. The callback may re-enter or mutate this
   * tree. Keys and values in the snapshot are references; mutable values are not deep-copied.
   *
   * @param fromInclusive lower bound, included; must not be null
   * @param toExclusive upper bound, excluded; must not be null
   * @param visitor callback for each matching entry; must not be null
   * @throws NullPointerException if any argument is null
   */
  public void forEach(String fromInclusive, String toExclusive,
      EntryConsumer<? super V> visitor) {
    Objects.requireNonNull(fromInclusive, "fromInclusive");
    Objects.requireNonNull(toExclusive, "toExclusive");
    Objects.requireNonNull(visitor, "visitor");
    State snapshot = state;
    if (fromInclusive.compareTo(toExclusive) < 0) {
      visit(snapshot.root, fromInclusive, toExclusive, visitor);
    }
  }

  @SuppressWarnings("unchecked")
  private static <V> void visit(Node node, String from, String to,
      EntryConsumer<? super V> visitor) {
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
    State snapshot = state;
    int actual = validate(snapshot.root, true, null, null, 0, leafDepth);
    if (actual != snapshot.size) {
      throw new AssertionError("Size " + actual + " != " + snapshot.size);
    }
  }

  private static int validate(Node node, boolean isRoot, String low, String high,
      int depth, int[] leafDepth) {
    if (node.count > MAX || node.count < (isRoot ? 0 : DEGREE - 1)) {
      throw new AssertionError("Occupancy");
    }
    for (int i = 0; i < node.count; i++) {
      String key = node.keys[i];
      if (key == null || (i > 0 && node.keys[i - 1].compareTo(key) >= 0)
          || (low != null && key.compareTo(low) <= 0)
          || (high != null && key.compareTo(high) >= 0)) {
        throw new AssertionError("Ordering");
      }
    }
    for (int i = node.count; i < MAX; i++) {
      if (node.keys[i] != null || node.values[i] != null) {
        throw new AssertionError("Retained entry");
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
            i == 0 ? low : node.keys[i - 1],
            i == node.count ? high : node.keys[i], depth + 1, leafDepth);
      }
      for (int i = node.count + 1; i < node.children.length; i++) {
        if (node.children[i] != null) throw new AssertionError("Retained child");
      }
    }
    return total;
  }
}
