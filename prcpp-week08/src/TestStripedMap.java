import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.IntToDoubleFunction;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class TestStripedMap {
    public static void main(String[] args) {
        testMap(new StripedWriteMap<Integer,String>(77, 7));
        testMap(new WrapConcurrentHashMap<Integer,String>());
        concurrentTest(new StripedWriteMap<>(77,7));
        concurrentTest(new WrapConcurrentHashMap<>());
    }

    private static void concurrentTest(OurMap<Integer, String> map){

        //Creates a new map

        //Initialization of the test settings
        final int threadCount = 16, range = 100, n = 10_000_000;

        //Two new arrays is created to store threads and their results
        Thread[] threads = new Thread[threadCount];
        AtomicIntegerArray sizes = new AtomicIntegerArray(threadCount);


        //A CyclicBarrier is used to synchronize start time
        CyclicBarrier startBarrier = new CyclicBarrier(threadCount);

        //The threads are created
        for (int t=0; t<threadCount ; t++) {
            final int myThread = t;
            threads[t] = new Thread(() -> {
                //A new random generator is created for every thread.
                Random random = new Random(37 * myThread + 78);
                int size = 0;
                String output;  
                try{ startBarrier.await(); }
                catch(Exception e){ throw new RuntimeException(e); }

                for(int i = 0; i < n; i++){
                    //A key is generated at random
                    int key = random.nextInt(range);
                    if(!map.containsKey(key)){
                        //If the output of putIfAbsent is null it means that a new key was added
                        if((output = map.putIfAbsent(key, myThread + ":" + key)) == null) sizes.getAndIncrement(myThread);
                    }else{
                        if(random.nextDouble() < 0.5){
                            //If the output of remove is not null it means that a key was deleted
                            if((output = map.remove(key)) != null) sizes.getAndDecrement(Integer.parseInt(output.split(":")[0]));
                        }else{
                            //If the output of put is not null it means that a key was overridden
                            if((output = map.put(key, myThread + ":" + key)) != null)
                                sizes.getAndDecrement(Integer.parseInt(output.split(":")[0]));
                            sizes.getAndIncrement(myThread);
                        }
                    }
                } 
            });
        }

        //The threads is started
        for (int t=0; t<threadCount; t++)
            threads[t].start();

        //The program block until all threads have terminated
        try {
            for (int t=0; t<threadCount; t++)
                threads[t].join();
        } catch (InterruptedException exn) { System.out.println(exn);}

        //The sum of each threads contribution to the total amount of key-value pairs
        int sum = 0;
        for (int t=0; t<threadCount; t++)
            sum += sizes.get(t);

        //The expected sum of kvp's is asserted to the actual count
        assert map.size() == sum;

        //The contributing to the map for each thread is extracted 
        int[] results = new int[threadCount];
        map.forEach((k,v) -> results[Integer.parseInt(v.split(":")[0])]++ );

        //The expected contribution by each thread is asserted to the actual contribution
        for(int t = 0; t < threadCount; t++){
            assert results[t] == sizes.get(t);
        }

    }

    private static void testMap(final OurMap<Integer, String> map){
        int n = 100000;
        assert map.size() == 0;

        //Checks that an element is correctly added
        assert map.put(0,"0") == null;
        assert map.containsKey(0);
        assert map.size() == 1;

        //To test bucket reallocation
        for(int i = 1; i < n; i++){
            assert map.get(i) == null;
            assert !map.containsKey(i);
            map.put(i, "" + i);
            assert map.get(i) != null;
            assert map.containsKey(i);
        }
        map.reallocateBuckets(); //This is actually not needed for the StripedWriteMap as it reallocates automatically.

        //We check that the elements are still here after reallocation.
        assert map.get(0).equals("0");
        assert map.containsKey(0);
        assert map.size() == n;

        //Checks that no new element is added if the key is already there.
        assert map.putIfAbsent(0,"new1").equals("0");
        assert map.get(0).equals("0");
        assert map.size() == n;

        //Checks that the element is overriden if the key is already there.
        assert map.put(0,"new1").equals("0");
        assert map.get(0).equals("new1");
        assert map.size() == n;

        //Checks that the element is removed correctly
        map.remove(0);
        assert map.get(0) == null;
        assert map.size() == n-1;


        map.forEach((k, v) -> {assert k.toString().equals(v);});

        //Randomized tests
        Random rand = new Random();
        int size = map.size();
        for(int i = 1; i < n; i++){
            int j = rand.nextInt(n);
            if(map.containsKey(j)){
                map.remove(j);
                assert map.get(j) == null;
                assert map.size() == --size;
            }else{
                assert map.put(j,"j") == null;
                assert map.containsKey(j);
                assert map.size() == ++size;
            }
        }
    }
}

interface Consumer<K,V> {
    void accept(K k, V v);
}

interface OurMap<K,V> {
    boolean containsKey(K k);
    V get(K k);
    V put(K k, V v);
    V putIfAbsent(K k, V v);
    V remove(K k);
    int size();
    void forEach(Consumer<K,V> consumer);
    void reallocateBuckets();
}

// The bucketCount must be a multiple of the number lockCount of
// stripes, so that h % lockCount == (h % bucketCount) % lockCount and
// so that h % lockCount is invariant under doubling the number of
// buckets in method reallocateBuckets.  Otherwise there is a risk of
// locking a stripe, only to have the relevant entry moved to a
// different stripe by an intervening call to reallocateBuckets.

class StripedWriteMap<K,V> implements OurMap<K,V> {
    // Synchronization policy: writing to
    //   buckets[hash] is guarded by locks[hash % lockCount]
    //   sizes[stripe] is guarded by locks[stripe]
    // Visibility of writes to reads is ensured by writes writing to
    // the stripe's size component (even if size does not change) and
    // reads reading from the stripe's size component.
    private volatile ItemNode<K,V>[] buckets;
    private final int lockCount;
    private final Object[] locks;
    private final AtomicIntegerArray sizes;

    public StripedWriteMap(int bucketCount, int lockCount) {
        if (bucketCount % lockCount != 0)
            throw new RuntimeException("bucket count must be a multiple of stripe count");
        this.lockCount = lockCount;
        this.buckets = makeBuckets(bucketCount);
        this.locks = new Object[lockCount];
        this.sizes = new AtomicIntegerArray(lockCount);
        for (int stripe=0; stripe<lockCount; stripe++)
            this.locks[stripe] = new Object();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
        private static <K,V> ItemNode<K,V>[] makeBuckets(int size) {
            // Java's @$#@?!! type system requires "unsafe" cast here:
            return (ItemNode<K,V>[])new ItemNode[size];
        }

    // Protect against poor hash functions and make non-negative
    private static <K> int getHash(K k) {
        final int kh = k.hashCode();
        return (kh ^ (kh >>> 16)) & 0x7FFFFFFF;
    }

    // Return true if key k is in map, else false
    public boolean containsKey(K k) {
        final ItemNode<K,V>[] bs = buckets;
        final int h = getHash(k), stripe = h % lockCount, hash = h % bs.length;
        // The sizes access is necessary for visibility of bs elements
        return sizes.get(stripe) != 0 && ItemNode.search(bs[hash], k, null);
    }

    // Return value v associated with key k, or null
    public V get(K k) {
        final ItemNode<K,V>[] bs = buckets;
        final int h = getHash(k), stripe = h % lockCount, hash = h % bs.length;
        if(sizes.get(stripe) == 0) return null;
        final Holder<V> holder = new Holder<>();
        ItemNode.search(bs[hash],k, holder);
        return holder.value;
    }

    public int size() {
        int sum = 0;
        for(int i = 0; i < sizes.length(); i++) sum += sizes.get(i);
        return sum;
    }

    // Put v at key k, or update if already present.  The logic here has
    // become more contorted because we must not hold the stripe lock
    // when calling reallocateBuckets, otherwise there will be deadlock
    // when two threads working on different stripes try to reallocate
    // at the same time.
    public V put(K k, V v) {
        final int h = getHash(k), stripe = h % lockCount;
        final Holder<V> old = new Holder<V>();
        ItemNode<K,V>[] bs;
        int afterSize;
        synchronized (locks[stripe]) {
            bs = buckets;
            final int hash = h % bs.length;
            final ItemNode<K,V> node = bs[hash],
                  newNode = ItemNode.delete(node, k, old);
            bs[hash] = new ItemNode<K,V>(k, v, newNode);
            // Write for visibility; increment if k was not already in map
            afterSize = sizes.addAndGet(stripe, newNode == node ? 1 : 0);
        }
        if (afterSize * lockCount > bs.length)
            reallocateBuckets(bs);
        return old.get();
    }

    // Put v at key k only if absent.
    public V putIfAbsent(K k, V v) {
        final int h = getHash(k), stripe = h % lockCount;
        final Holder<V> old = new Holder<>();
        int afterSize;
        synchronized (locks[stripe]){
            final int hash = h % buckets.length;
            final ItemNode<K,V> node = buckets[hash];
            if(ItemNode.search(node, k, old)) return old.get();
            buckets[hash] = new ItemNode<>(k,v,buckets[hash]);
            afterSize = sizes.incrementAndGet(stripe);
        }
        if(afterSize * lockCount > buckets.length)
            reallocateBuckets();
        return old.get();
    }

    // Remove and return the value at key k if any, else return null
    public V remove(K k) {
        final int h = getHash(k), stripe = h % lockCount;
        final Holder<V> holder = new Holder<>();
        synchronized (locks[stripe]){
            final int hash = h % buckets.length;
            buckets[hash] = ItemNode.delete(buckets[hash], k, holder);
            sizes.addAndGet(stripe, holder.value != null ? -1 : 0);
            return holder.value;
        }
    }

    // Iterate over the hashmap's entries one stripe at a time.
    public void forEach(Consumer<K,V> consumer) {
        ItemNode<K,V>[] bs = buckets;

        if(size() == 0) return; // for visibility

        for (int hash=0; hash<bs.length; hash++) {
            ItemNode<K,V> node = bs[hash];
            while (node != null) {
                consumer.accept(node.k, node.v);
                node = node.next;
            }
        }
    }

    // Now that reallocation happens internally, do not do it externally
    public void reallocateBuckets() { }

    // First lock all stripes.  Then double bucket table size, rehash,
    // and redistribute entries.  Since the number of stripes does not
    // change, and since buckets.length is a multiple of lockCount, a
    // key that belongs to stripe s because (getHash(k) % N) %
    // lockCount == s will continue to belong to stripe s.  Hence the
    // sizes array need not be recomputed.

    // In any case, do not reallocate if the buckets field was updated
    // since the need for reallocation was discovered; this means that
    // another thread has already reallocated.  This happens very often
    // with 16 threads and a largish buckets table, size > 10,000.

    public void reallocateBuckets(final ItemNode<K,V>[] oldBuckets) {
        lockAllAndThen(() -> {
            final ItemNode<K,V>[] bs = buckets;
            if (oldBuckets == bs) {
                // System.out.printf("Reallocating from %d buckets%n", bs.length);
                final ItemNode<K,V>[] newBuckets = makeBuckets(2 * bs.length);
                for (int hash=0; hash<bs.length; hash++) {
                    ItemNode<K,V> node = bs[hash];
                    while (node != null) {
                        final int newHash = getHash(node.k) % newBuckets.length;
                        newBuckets[newHash]
            = new ItemNode<>(node.k, node.v, newBuckets[newHash]);
        node = node.next;
                    }
                }
                buckets = newBuckets; // Visibility: buckets field is volatile
            }
        });
    }

    // Lock all stripes, perform action, then unlock all stripes
    private void lockAllAndThen(Runnable action) {
        lockAllAndThen(0, action);
    }

    private void lockAllAndThen(int nextStripe, Runnable action) {
        if (nextStripe >= lockCount)
            action.run();
        else
            synchronized (locks[nextStripe]) {
                lockAllAndThen(nextStripe + 1, action);
            }
    }

    static class ItemNode<K,V> {
        private final K k;
        private final V v;
        private final ItemNode<K,V> next;

        public ItemNode(K k, V v, ItemNode<K,V> next) {
            if(v == null) throw new RuntimeException("Value cannot be null");
            this.k = k;
            this.v = v;
            this.next = next;
        }

        // These work on immutable data only, no synchronization needed.

        public static <K,V> boolean search(ItemNode<K,V> node, K k, Holder<V> old) {
            while (node != null)
                if (k.equals(node.k)) {
                    if (old != null)
                        old.set(node.v);
                    return true;
                } else
                    node = node.next;
            return false;
        }

        public static <K,V> ItemNode<K,V> delete(ItemNode<K,V> node, K k, Holder<V> old) {
            if (node == null)
                return null;
            else if (k.equals(node.k)) {
                old.set(node.v);
                return node.next;
            } else {
                final ItemNode<K,V> newNode = delete(node.next, k, old);
                if (newNode == node.next)
                    return node;
                else
                    return new ItemNode<>(node.k, node.v, newNode);
            }
        }
    }

    // Object to hold a "by reference" parameter.  For use only on a
    // single thread, so no need for "volatile" or synchronization.

    static class Holder<V> {
        private V value;
        public V get() {
            return value;
        }
        public void set(V value) {
            this.value = value;
        }
    }
}

// ----------------------------------------------------------------------
// A wrapper around the Java class library's sophisticated
// ConcurrentHashMap<K,V>, making it implement OurMap<K,V>

class WrapConcurrentHashMap<K,V> implements OurMap<K,V> {
    final ConcurrentHashMap<K,V> underlying = new ConcurrentHashMap<K,V>();

    public boolean containsKey(K k) {
        return underlying.containsKey(k);
    }

    public V get(K k) {
        return underlying.get(k);
    }

    public V put(K k, V v) {
        return underlying.put(k, v);
    }

    public V putIfAbsent(K k, V v) {
        return underlying.putIfAbsent(k, v);
    }

    public V remove(K k) {
        return underlying.remove(k);
    }

    public int size() {
        return underlying.size();
    }

    public void forEach(Consumer<K,V> consumer) {
        underlying.forEach((k,v) -> consumer.accept(k,v));
    }

    public void reallocateBuckets() { }
}
