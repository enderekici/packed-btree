package bench.singleconcurrent;

import io.github.enderekici.packedbtree.ConcurrentLongBTree;
import io.github.enderekici.packedbtree.ConcurrentStringBTree;
import io.github.enderekici.packedbtree.LongBTree;
import io.github.enderekici.packedbtree.StringBTree;
import com.sun.management.ThreadMXBean;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;
import java.util.function.LongSupplier;

/** Warming and ThreadMXBean allocation probe; only the selected map is instantiated. */
public final class TreeAllocationProbe {
  private static final int SIZE = 262144;
  private static final int OPERATIONS = 100000;
  private static volatile long sink;
  private TreeAllocationProbe() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("arm type shape");
    ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Thread allocation unsupported");
    bean.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().getId();
    if (args[1].equals("long")) new LongProbe(args[0]).run(bean, thread);
    else if (args[1].equals("string")) new StringProbe(args[0], args[2]).run(bean, thread);
    else throw new IllegalArgumentException(args[1]);
  }

  private static void measure(ThreadMXBean bean, long thread, String arm, String type,
      String operation, LongSupplier body) {
    sink = body.getAsLong();
    long before = bean.getThreadAllocatedBytes(thread);
    sink = body.getAsLong();
    long bytes = bean.getThreadAllocatedBytes(thread) - before;
    System.out.printf("ALLOC arm=%s type=%s operation=%s operations=%d bytes=%d bytesPerOp=%.4f sink=%d%n",
        arm, type, operation, OPERATIONS, bytes, (double) bytes / OPERATIONS, sink);
  }

  private static final class LongProbe implements LongBTree.EntryConsumer,
      ConcurrentLongBTree.EntryConsumer {
    final String arm;
    final LongMap map;
    final long[] keys = new long[SIZE], alternate = new long[SIZE], misses = new long[SIZE];
    final long[] a = new long[SIZE], b = new long[SIZE];
    final boolean[] alternatePresent = new boolean[SIZE], side = new boolean[SIZE];
    final int[] order = permutation(SIZE, 99173), ranges = permutation(SIZE - 63, 91733);
    final long[] from = new long[SIZE - 63], to = new long[SIZE - 63];
    int cursor, rangeCursor, count; long sum;
    final LongSupplier hit = this::runHit, miss = this::runMiss, overwrite = this::runOverwrite,
        churn = this::runChurn, range = this::runRange;

    LongProbe(String arm) throws Exception {
      this.arm = arm;
      if (arm.equals("mutable")) {
        guard(arm, "long", LongBTree.class, "outer");
        guard(arm, "long", Class.forName(LongBTree.class.getName() + "$Node"), "node");
        map = new MutableLong(new LongBTree());
      } else if (arm.equals("concurrent")) {
        guard(arm, "long", ConcurrentLongBTree.class, "outer");
        guard(arm, "long", Class.forName(ConcurrentLongBTree.class.getName() + "$Node"), "node");
        guard(arm, "long", Class.forName(ConcurrentLongBTree.class.getName() + "$State"), "state");
        map = new ConcurrentLong(new ConcurrentLongBTree());
      } else throw new IllegalArgumentException(arm);
      for (int i = 0; i < SIZE; i++) {
        keys[i] = 2L * i + 1_000_000L; alternate[i] = keys[i] + 1;
        misses[i] = keys[i] + 2L * SIZE + 1; a[i] = ((long) i) << 1; b[i] = a[i] | 1;
      }
      for (int i = 0; i < from.length; i++) {
        from[i] = keys[i]; to[i] = i + 64 < SIZE ? keys[i + 64] : keys[SIZE - 1] + 2;
      }
      int[] insertion = permutation(SIZE, 7319);
      for (int i : insertion) map.put(keys[i], a[i]);
    }

    void run(ThreadMXBean bean, long thread) {
      measure(bean, thread, arm, "long", "hit", hit);
      measure(bean, thread, arm, "long", "miss_above_max", miss);
      measure(bean, thread, arm, "long", "range64", range);
      measure(bean, thread, arm, "long", "overwrite", overwrite);
      measure(bean, thread, arm, "long", "churn", churn);
      if (map.size() != SIZE) throw new AssertionError("cardinality");
      for (int i = 0; i < SIZE; i++) {
        long key = alternatePresent[i] ? alternate[i] : keys[i];
        long expected = alternatePresent[i] || side[i] ? b[i] : a[i];
        if (map.get(key, Long.MIN_VALUE) != expected) throw new AssertionError("mapping " + i);
        if (map.get(alternatePresent[i] ? keys[i] : alternate[i], Long.MIN_VALUE) != Long.MIN_VALUE)
          throw new AssertionError("duplicate " + i);
      }
      map.validate();
    }

    long runHit() { long s = 0; cursor = 0; for (int n = 0; n < OPERATIONS; n++) s += map.get(keys[order[cursor++ & (SIZE - 1)]], Long.MIN_VALUE); return s; }
    long runMiss() { long s = 0; cursor = 0; for (int n = 0; n < OPERATIONS; n++) s += map.get(misses[order[cursor++ & (SIZE - 1)]], Long.MIN_VALUE); return s; }
    long runOverwrite() { cursor = 0; for (int n = 0; n < OPERATIONS; n++) { int i = order[cursor++ & (SIZE - 1)]; side[i] = !side[i]; map.put(keys[i], side[i] ? b[i] : a[i]); } return cursor; }
    long runChurn() {
      cursor = 0;
      for (int n = 0; n < OPERATIONS; n++) {
        int i = order[cursor++ & (SIZE - 1)];
        long removed = alternatePresent[i] ? alternate[i] : keys[i];
        if (!map.remove(removed)) throw new AssertionError("churn removal");
        if (alternatePresent[i]) map.put(keys[i], side[i] ? b[i] : a[i]);
        else map.put(alternate[i], b[i]);
        alternatePresent[i] = !alternatePresent[i];
      }
      return cursor;
    }
    long runRange() { long s = 0; rangeCursor = 0; for (int n = 0; n < OPERATIONS; n++) { int i = ranges[rangeCursor++]; if (rangeCursor == ranges.length) rangeCursor = 0; count = 0; sum = 0; map.forEach(from[i], to[i], this); if (count != 64) throw new AssertionError("range count"); s += sum; } return s; }
    public void accept(long key, long value) { count++; sum += value; }
  }

  private static final class StringProbe implements StringBTree.EntryConsumer<Object>,
      ConcurrentStringBTree.EntryConsumer<Object> {
    final String arm, shape;
    final StringMap map;
    final String[] keys = new String[SIZE], queries = new String[SIZE], alternate = new String[SIZE], misses = new String[SIZE];
    final String[] from = new String[SIZE - 63], to = new String[SIZE - 63];
    final Object[] a = new Object[SIZE], b = new Object[SIZE];
    final boolean[] alternatePresent = new boolean[SIZE], side = new boolean[SIZE];
    final int[] order = permutation(SIZE, 99173), ranges = permutation(SIZE - 63, 91733);
    int cursor, rangeCursor, count; long sum;
    final LongSupplier hit = this::runHit, miss = this::runMiss, overwrite = this::runOverwrite,
        churn = this::runChurn, range = this::runRange;

    StringProbe(String arm, String shape) throws Exception {
      this.arm = arm; this.shape = shape;
      if (!shape.equals("random") && !shape.equals("prefix")) throw new IllegalArgumentException(shape);
      if (arm.equals("mutable")) {
        guard(arm, "string", StringBTree.class, "outer");
        guard(arm, "string", Class.forName(StringBTree.class.getName() + "$Node"), "node");
        map = new MutableString(new StringBTree<>());
      } else if (arm.equals("concurrent")) {
        guard(arm, "string", ConcurrentStringBTree.class, "outer");
        guard(arm, "string", Class.forName(ConcurrentStringBTree.class.getName() + "$Node"), "node");
        guard(arm, "string", Class.forName(ConcurrentStringBTree.class.getName() + "$State"), "state");
        map = new ConcurrentString(new ConcurrentStringBTree<>());
      } else throw new IllegalArgumentException(arm);
      for (int i = 0; i < SIZE; i++) {
        String encoded = String.format("%08x", mix(i));
        keys[i] = shape.equals("prefix") ? "shared-prefix-that-is-long-" + encoded : encoded;
        a[i] = new String(("value-a-" + i).toCharArray());
        b[i] = new String(("value-b-" + i).toCharArray());
      }
      Arrays.sort(keys);
      for (int i = 0; i < SIZE; i++) {
        queries[i] = new String(keys[i].toCharArray()); alternate[i] = keys[i] + "!";
        misses[i] = keys[i] + "~";
      }
      for (int i = 0; i < from.length; i++) {
        from[i] = keys[i]; to[i] = i + 64 < SIZE ? keys[i + 64] : keys[SIZE - 1] + "~";
      }
      int[] insertion = permutation(SIZE, 7319);
      for (int i : insertion) map.put(keys[i], a[i]);
    }

    void run(ThreadMXBean bean, long thread) {
      measure(bean, thread, arm, "string_" + shape, "hit", hit);
      measure(bean, thread, arm, "string_" + shape, "miss_suffix", miss);
      measure(bean, thread, arm, "string_" + shape, "range64", range);
      measure(bean, thread, arm, "string_" + shape, "overwrite", overwrite);
      measure(bean, thread, arm, "string_" + shape, "churn", churn);
      if (map.size() != SIZE) throw new AssertionError("cardinality");
      for (int i = 0; i < SIZE; i++) {
        String key = alternatePresent[i] ? alternate[i] : keys[i];
        Object expected = alternatePresent[i] || side[i] ? b[i] : a[i];
        if (map.get(key, null) != expected) throw new AssertionError("mapping " + i);
        if (map.get(alternatePresent[i] ? keys[i] : alternate[i], null) != null)
          throw new AssertionError("duplicate " + i);
      }
      map.validate();
    }

    long runHit() { long s = 0; cursor = 0; for (int n = 0; n < OPERATIONS; n++) { Object v = map.get(queries[order[cursor++ & (SIZE - 1)]], null); s += ((String) v).length(); } return s; }
    long runMiss() { long s = 0; cursor = 0; for (int n = 0; n < OPERATIONS; n++) { if (map.get(misses[order[cursor++ & (SIZE - 1)]], null) != null) throw new AssertionError("miss"); } return s; }
    long runOverwrite() { cursor = 0; for (int n = 0; n < OPERATIONS; n++) { int i = order[cursor++ & (SIZE - 1)]; side[i] = !side[i]; map.put(queries[i], side[i] ? b[i] : a[i]); } return cursor; }
    long runChurn() {
      cursor = 0;
      for (int n = 0; n < OPERATIONS; n++) {
        int i = order[cursor++ & (SIZE - 1)];
        String removed = alternatePresent[i] ? alternate[i] : queries[i];
        if (!map.remove(removed)) throw new AssertionError("churn removal");
        if (alternatePresent[i]) map.put(queries[i], side[i] ? b[i] : a[i]);
        else map.put(alternate[i], b[i]);
        alternatePresent[i] = !alternatePresent[i];
      }
      return cursor;
    }
    long runRange() { long s = 0; rangeCursor = 0; for (int n = 0; n < OPERATIONS; n++) { int i = ranges[rangeCursor++]; if (rangeCursor == ranges.length) rangeCursor = 0; count = 0; sum = 0; map.forEach(from[i], to[i], this); if (count != 64) throw new AssertionError("range count"); s += sum; } return s; }
    public void accept(String key, Object value) { count++; sum += ((String) value).length(); }
  }

  private interface LongMap { long get(long k, long d); void put(long k, long v); boolean remove(long k); int size(); void forEach(long f, long t, LongBTree.EntryConsumer c); void validate(); }
  private record MutableLong(LongBTree tree) implements LongMap {
    public long get(long k,long d){return tree.getOrDefault(k,d);} public void put(long k,long v){tree.put(k,v);}
    public boolean remove(long k){return tree.remove(k);} public int size(){return tree.size();}
    public void forEach(long f,long t,LongBTree.EntryConsumer c){tree.forEach(f,t,c);} public void validate(){tree.validate();}
  }
  private record ConcurrentLong(ConcurrentLongBTree tree) implements LongMap {
    public long get(long k,long d){return tree.getOrDefault(k,d);} public void put(long k,long v){tree.put(k,v);}
    public boolean remove(long k){return tree.remove(k);} public int size(){return tree.size();}
    public void forEach(long f,long t,LongBTree.EntryConsumer c){tree.forEach(f,t,(ConcurrentLongBTree.EntryConsumer)c);} public void validate(){tree.validate();}
  }
  private interface StringMap { Object get(String k,Object d); void put(String k,Object v); boolean remove(String k); int size(); void forEach(String f,String t,StringBTree.EntryConsumer<Object> c); void validate(); }
  private record MutableString(StringBTree<Object> tree) implements StringMap {
    public Object get(String k,Object d){return tree.getOrDefault(k,d);} public void put(String k,Object v){tree.put(k,v);}
    public boolean remove(String k){return tree.remove(k);} public int size(){return tree.size();}
    public void forEach(String f,String t,StringBTree.EntryConsumer<Object> c){tree.forEach(f,t,c);} public void validate(){tree.validate();}
  }
  private record ConcurrentString(ConcurrentStringBTree<Object> tree) implements StringMap {
    public Object get(String k,Object d){return tree.getOrDefault(k,d);} public void put(String k,Object v){tree.put(k,v);}
    public boolean remove(String k){return tree.remove(k);} public int size(){return tree.size();}
    public void forEach(String f,String t,StringBTree.EntryConsumer<Object> c){tree.forEach(f,t,(ConcurrentStringBTree.EntryConsumer<Object>)c);} public void validate(){tree.validate();}
  }

  private static void guard(String arm,String type,Class<?> cls,String part)throws Exception{
    String p="tree.guard."+arm+"."+type, expected=System.getProperty(p+"."+part), dir=System.getProperty("tree.guard."+arm+".location");
    URL location=cls.getProtectionDomain().getCodeSource().getLocation();
    if(expected==null||dir==null||!new File(location.toURI()).getCanonicalFile().equals(new File(dir).getCanonicalFile()))throw new IllegalStateException("origin guard "+cls.getName());
    byte[] b;try(InputStream in=cls.getResourceAsStream("/"+cls.getName().replace('.','/')+".class")){b=in.readAllBytes();}
    StringBuilder s=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",x&255));
    System.out.println("CLASS-GUARD "+cls.getName()+" source="+location+" sha256="+s);
    if(!expected.equals(s.toString()))throw new IllegalStateException("class SHA mismatch "+cls.getName());
  }
  private static int mix(int v){v^=v>>>16;v*=0x7feb352d;v^=v>>>15;v*=0x846ca68b;return v^(v>>>16);}
  private static int[] permutation(int n,long seed){int[]a=new int[n];for(int i=0;i<n;i++)a[i]=i;Random r=new Random(seed);for(int i=n-1;i>0;i--){int j=r.nextInt(i+1),x=a[i];a[i]=a[j];a[j]=x;}return a;}
}
