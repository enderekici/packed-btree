package bench;

/** A single-threaded, primitive long-to-long B-tree. Not safe for concurrent access. */
public final class OriginalPrototypeLongBTree {
  @FunctionalInterface
  public interface EntryConsumer {
    void accept(long key, long value);
  }
  private static final int DEGREE = 16;
  private static final int MAX = 2 * DEGREE - 1;
  private Node root = new Node(true);
  private int size;

  private static final class Node {
    final long[] keys = new long[MAX];
    final long[] values = new long[MAX];
    final Node[] children;
    int count;

    Node(boolean leaf) {
      children = leaf ? null : new Node[MAX + 1];
    }

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
  }

  public int size() {
    return size;
  }

  public long getOrDefault(long key, long fallback) {
    Node node = root;
    while (true) {
      int i = node.index(key);
      if (i < node.count && node.keys[i] == key) return node.values[i];
      if (node.children == null) return fallback;
      node = node.children[i];
    }
  }

  public void put(long key, long value) {
    if (root.count == MAX) {
      Node parent = new Node(false);
      parent.children[0] = root;
      split(parent, 0);
      root = parent;
    }
    Node node = root;
    while (true) {
      int i = node.index(key);
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
    Node right = new Node(left.children == null);
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
  }

  public boolean remove(long key) {
    boolean removed = delete(root, key);
    if (root.count == 0 && root.children != null) root = root.children[0];
    if (removed) size--;
    return removed;
  }

  private static boolean delete(Node node, long key) {
    int i = node.index(key);
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

  /** Visits entries in ascending order in [fromInclusive, toExclusive). */
  public void forEach(long fromInclusive, long toExclusive, EntryConsumer visitor) {
    if (fromInclusive < toExclusive) visit(root, fromInclusive, toExclusive, visitor);
  }

  private static void visit(Node node, long from, long to, EntryConsumer visitor) {
    int i = node.index(from);
    if (node.children != null) visit(node.children[i], from, to, visitor);
    while (i < node.count && node.keys[i] < to) {
      visitor.accept(node.keys[i], node.values[i]);
      i++;
      if (node.children != null) visit(node.children[i], from, to, visitor);
    }
  }

  /** Exhaustive structural check for qualification; not used on the benchmark path. */
  public void validate() {
    int[] leafDepth = {-1};
    int actual = validate(root, true, null, null, 0, leafDepth);
    if (actual != size) throw new AssertionError("Size " + actual + " != " + size);
  }

  private static int validate(Node node, boolean isRoot, Long low, Long high,
      int depth, int[] leafDepth) {
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
