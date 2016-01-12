// Pipelined sorting using P>=1 stages, each maintaining an internal
// collection of size S>=1.  Stage 1 contains the largest items, stage
// 2 the second largest, ..., stage P the smallest ones.  In each
// stage, the internal collection of items is organized as a minheap.
// When a stage receives an item x and its collection is not full, it
// inserts it in the heap.  If the collection is full and x is less
// than or equal to the collections's least item, it forwards the item
// to the next stage; otherwise forwards the collection's least item
// and inserts x into the collection instead.

// When there are itemCount items and stageCount stages, each stage
// must be able to hold at least ceil(itemCount/stageCount) items,
// which equals (itemCount-1)/stageCount+1.

// sestoft@itu.dk * 2016-01-10

import java.util.stream.DoubleStream;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.IntToDoubleFunction;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

//Multiverse import 
import org.multiverse.api.references.*;
import static org.multiverse.api.StmUtils.*;

// Multiverse locking:
import org.multiverse.api.LockMode;
import org.multiverse.api.Txn;
import org.multiverse.api.callables.TxnVoidCallable;

public class SortingPipeline { 
    public static void main(String[] args) {
        final int count = 100_000, P = 4;
        final double[] arr = DoubleArray.randomPermutation(count);

        //Combining the array stream with an infinit stream of infinity
        DoubleStream input = DoubleStream.concat(DoubleStream.of(arr), DoubleStream.iterate(0, x -> Double.POSITIVE_INFINITY));//.parallel();

        //Chaning the StreamSorters
        for(int i = 0; i < P; i++){
            input = input.flatMap(new StreamSorter(count/P)::pipe);
        }

        //Chaning the output writer
        input.limit(500).forEach(x -> System.out.print(x + ", "));
    }

    /*public static void main(String[] args) {
        SystemInfo();
        Mark7("Sorting pipe", j -> {
            final int count = 60, P = 4;
            final double[] arr = DoubleArray.randomPermutation(count);

            final BlockingDoubleQueue[] queues = new BlockingDoubleQueue[P+1];

            for(int i = 0; i < P+1; i++){
                //queues[i] = new BlockingNDoubleQueue();
                //queues[i] = new UnboundedDoubleQueue();
                //queues[i] = new NoLockNDoubleQueue();
                //queues[i] = new MSUnboundedDoubleQueue();
                queues[i] = new StmBlockingNDoubleQueue();
            }

            sortPipeline(arr, P, queues);
            return arr[0];
        });
    }*/

    private static void sortPipeline(double[] arr, int P, BlockingDoubleQueue[] queues) {
        int n = arr.length / P;

        //Initializing the sorting stages
        Thread[] threads = new Thread[P+2];
        for(int i = 1; i <= P; i++){
            threads[i-1] = new Thread(new SortingStage(queues[i-1], queues[i], n, arr.length+(P-i)*n)); 
        }

        //Initializing the drain
        threads[P] = new Thread(new SortedChecker(arr.length, queues[P]));

        //Initializing the source. The source is purposefully last in the array so that it will be started lastly.
        threads[P+1] = new Thread(new DoubleGenerator(arr, arr.length, queues[0]));

        //Starting the stages
        for(int i = 0; i < threads.length; i++){
            threads[i].start();
        }

        //Joining the stages
        for(int i = 0; i < threads.length; i++){
            try{ threads[i].join(); }
            catch(InterruptedException e){ throw new RuntimeException(e); }
        }
    }

    static class SortingStage implements Runnable {
        private final BlockingDoubleQueue in;
        private final BlockingDoubleQueue out;
        private final double[] heap; 
        private int itemCount;
        private int heapSize = 0;

        public SortingStage(BlockingDoubleQueue in, BlockingDoubleQueue out, int capacity, int itemCount){
            this.in = in;
            this.out = out;
            this.itemCount = itemCount;
            heap = new double[capacity];
        }


        public void run() { 
            while(itemCount > 0){
                double x = in.take();
                if(heapSize < heap.length){
                    heap[heapSize++] = x;
                    DoubleArray.minheapSiftup(heap, heapSize-1, heapSize-1);
                } else if (x <= heap[0]){
                    out.put(x);
                    itemCount--;
                } else {
                    double least = heap[0];
                    heap[0] = x;
                    DoubleArray.minheapSiftdown(heap,0,heapSize-1);
                    out.put(least);
                    itemCount--;
                }
            }
        }
    }

    static class DoubleGenerator implements Runnable {
        private final BlockingDoubleQueue output;
        private final double[] arr;  // The numbers to feed to output
        private final int infinites;

        public DoubleGenerator(double[] arr, int infinites, BlockingDoubleQueue output) {
            this.arr = arr;
            this.output = output;
            this.infinites = infinites;
        }

        public void run() { 
            for (int i=0; i<arr.length; i++)  // The numbers to sort
                output.put(arr[i]);
            for (int i=0; i<infinites; i++)   // Infinite numbers for wash-out
                output.put(Double.POSITIVE_INFINITY);
        }
    }

    static class SortedChecker implements Runnable {
        // If DEBUG is true, print the first 100 numbers received
        private final static boolean DEBUG = false;
        private final BlockingDoubleQueue input;
        private final int itemCount; // the number of items to check

        public SortedChecker(int itemCount, BlockingDoubleQueue input) {
            this.itemCount = itemCount;
            this.input = input;
        }

        public void run() { 
            int consumed = 0;
            double last = Double.NEGATIVE_INFINITY;
            while (consumed++ < itemCount) {
                double p = input.take();
                if (DEBUG && consumed <= 100) 
                    System.out.print(p + " ");
                if (p <= last)
                    System.out.printf("Elements out of order: %g before %g%n", last, p);
                last = p;
            }
            if (DEBUG)
                System.out.println();
        }
    }

    // --- Benchmarking infrastructure ---

    // NB: Modified to show milliseconds instead of nanoseconds

    public static double Mark7(String msg, IntToDoubleFunction f) {
        int n = 10, count = 1, totalCount = 0;
        double dummy = 0.0, runningTime = 0.0, st = 0.0, sst = 0.0;
        do { 
            count *= 2;
            st = sst = 0.0;
            for (int j=0; j<n; j++) {
                Timer t = new Timer();
                for (int i=0; i<count; i++) 
                    dummy += f.applyAsDouble(i);
                runningTime = t.check();
                double time = runningTime * 1e3 / count;
                st += time; 
                sst += time * time;
                totalCount += count;
            }
        } while (runningTime < 0.25 && count < Integer.MAX_VALUE/2);
        double mean = st/n, sdev = Math.sqrt((sst - mean*mean*n)/(n-1));
        System.out.printf("%-25s %15.1f ms %10.2f %10d%n", msg, mean, sdev, count);
        return dummy / totalCount;
    }

    public static void SystemInfo() {
        System.out.printf("# OS:   %s; %s; %s%n", 
                System.getProperty("os.name"), 
                System.getProperty("os.version"), 
                System.getProperty("os.arch"));
        System.out.printf("# JVM:  %s; %s%n", 
                System.getProperty("java.vendor"), 
                System.getProperty("java.version"));
        // The processor identifier works only on MS Windows:
        System.out.printf("# CPU:  %s; %d \"cores\"%n", 
                System.getenv("PROCESSOR_IDENTIFIER"),
                Runtime.getRuntime().availableProcessors());
        java.util.Date now = new java.util.Date();
        System.out.printf("# Date: %s%n", 
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(now));
    }

    // Crude wall clock timing utility, measuring time in seconds

    static class Timer {
        private long start, spent = 0;
        public Timer() { play(); }
        public double check() { return (System.nanoTime()-start+spent)/1e9; }
        public void pause() { spent += System.nanoTime()-start; }
        public void play() { start = System.nanoTime(); }
    }
}

// ----------------------------------------------------------------------

// Queue interface

interface BlockingDoubleQueue {
    double take();
    void put(double item);
}

class WrappedArrayDoubleQueue implements BlockingDoubleQueue{

    private final ArrayBlockingQueue<Double> queue;

    public WrappedArrayDoubleQueue(){
        this.queue = new ArrayBlockingQueue<Double>(50);
    }

    public WrappedArrayDoubleQueue(int capacity){
        this.queue = new ArrayBlockingQueue<Double>(capacity);
    }

    public void put(double item){
        try{ queue.put(item); }
        catch(InterruptedException e){ throw new RuntimeException(e); }
    }

    public double take(){
        try{ return queue.take(); }
        catch(InterruptedException e){ throw new RuntimeException(e); }
    }
}


class BlockingNDoubleQueue implements BlockingDoubleQueue{

    private final double[] arr = new double[50];
    private int head = 0, tail = 0, count = 0;

    public synchronized void put(double item){
        while(count == arr.length){
            try{ this.wait(); }
            catch(InterruptedException exn) { }
        }

        arr[tail] = item;
        tail = ++tail == arr.length ? 0 : tail;
        count++;
        this.notify();
    }

    public synchronized double take(){
        while(count == 0){
            try{ this.wait(); }
            catch(InterruptedException exn) { }
        }

        double item = arr[head];
        head = ++head == arr.length ? 0 : head;
        count --;
        this.notify();
        return item;
    }
}

class UnboundedDoubleQueue implements BlockingDoubleQueue{

    public Node head;
    public Node tail;

    public UnboundedDoubleQueue(){
        Node n = new Node(0,null);
        tail = head = n;
    }

    public synchronized void put(double item){
        tail.next = new Node(item,null); //Setting next
        tail = tail.next; //Moving tail

        this.notify(); //Notifying a thread waiting for elements
    }

    public synchronized double take(){
        while(head.next == null){
            try{ this.wait(); }
            catch(InterruptedException exn) { }
        }

        Node first = head;
        head = first.next;
        return head.value;
    }

    class Node{
        public Node next;
        public final double value;

        public Node(double value, Node next){
            this.next = next;
            this.value = value;
        }
    }
}

class NoLockNDoubleQueue implements BlockingDoubleQueue{

    private final double[] arr = new double[50];
    private volatile int head = 0, tail = 0;

    public void put(double item){
        while(tail - head == arr.length){} //Spin
        arr[tail % arr.length] = item;
        tail++;
    }

    public double take() { 
        while(tail - head == 0){} //Spin
        double item = arr[head % arr.length];
        head++;
        return item; 
    }
} 

class MSUnboundedDoubleQueue implements BlockingDoubleQueue{

    private final AtomicReference<Node> head, tail;


    public MSUnboundedDoubleQueue(){
        Node sentinal = new Node(0,null);
        head = new AtomicReference<Node>(sentinal);
        tail = new AtomicReference<Node>(sentinal);
    }

    public void put(double item){
        Node node = new Node(item,null);
        while(true){
            Node last = tail.get(), 
                 next = last.next.get();
            if(last == tail.get()){
                if(next == null){
                    if(last.next.compareAndSet(next,node)){
                        tail.compareAndSet(last,node);
                        return;
                    }
                } else {
                    tail.compareAndSet(last,next);
                }
            }
        }
    }
    public double take(){ 
        while(true){
            Node first = head.get(),
                 last = tail.get(),
                 next = first.next.get();
            if(first == head.get()){
                if(first == last){
                    if(next == null){
                        continue;
                    } else {
                        tail.compareAndSet(last,next);
                    }
                } else {
                    double result = next.value;
                    if(head.compareAndSet(first,next)){
                        return result;
                    }
                }
            }
        }
    }

    class Node{
        public final AtomicReference<Node> next;
        public final double value;

        public Node(double value, Node next){
            this.next = new AtomicReference<>(next);
            this.value = value;
        }
    }
}


class StmBlockingNDoubleQueue implements BlockingDoubleQueue{
    private final TxnDouble[] arr;
    private final TxnInteger head, tail;

    public StmBlockingNDoubleQueue(){
        arr = new TxnDouble[40];
        for(int i = 0; i < arr.length; i++){
            arr[i] = newTxnDouble(0);
        }
        head = newTxnInteger(0);
        tail = newTxnInteger(0);
    } 

    public void put(double item){
        atomic(() -> {
            if(tail.get() - head.get() == arr.length){
                retry();
            } else {
                arr[tail.get() % arr.length].set(item);
                tail.increment(); 
            } 
        });
    }
    public double take(){ 
        return atomic(() -> {
            if(tail.get() - head.get() == 0) {
                retry();
            } else {
                double item = arr[head.get() % arr.length].get();
                head.increment();
                return item;
            }
            //Needed to compile. Will never be called
            throw new RuntimeException(); 
        });
    }
}

// ----------------------------------------------------------------------


class StreamSorter{

    private double[] heap;
    private int heapSize = 0;

    public StreamSorter(int capacity){
        heap = new double[capacity];
    }

    public DoubleStream pipe(double x){
        if(heapSize < heap.length){
            heap[heapSize++] = x;
            DoubleArray.minheapSiftup(heap, heapSize-1, heapSize-1);
            return DoubleStream.empty();
        } else if (x <= heap[0]){
            return DoubleStream.of(x);
        } else {
            double least = heap[0];
            heap[0] = x;
            DoubleArray.minheapSiftdown(heap,0,heapSize-1);
            return DoubleStream.of(least);
        }
    }
}

// ----------------------------------------------------------------------

class DoubleArray {
    public static double[] randomPermutation(int n) {
        double[] arr = fillDoubleArray(n);
        shuffle(arr);
        return arr;
    }

    private static double[] fillDoubleArray(int n) {
        double[] arr = new double[n];
        for (int i = 0; i < n; i++)
            arr[i] = i + 0.1;
        return arr;
    }

    private static final java.util.Random rnd = new java.util.Random();

    private static void shuffle(double[] arr) {
        for (int i = arr.length-1; i > 0; i--)
            swap(arr, i, rnd.nextInt(i+1));
    }

    // Swap arr[s] and arr[t]
    private static void swap(double[] arr, int s, int t) {
        double tmp = arr[s]; arr[s] = arr[t]; arr[t] = tmp;
    }

    // Minheap operations for parallel sort pipelines.  
    // Minheap invariant: 
    // If heap[0..k-1] is a minheap, then heap[(i-1)/2] <= heap[i] for
    // all indexes i=1..k-1.  Thus heap[0] is the smallest element.

    // Although stored in an array, the heap can be considered a tree
    // where each element heap[i] is a node and heap[(i-1)/2] is its
    // parent. Then heap[0] is the tree's root and a node heap[i] has
    // children heap[2*i+1] and heap[2*i+2] if these are in the heap.

    // In heap[0..k], move node heap[i] downwards by swapping it with
    // its smallest child until the heap invariant is reestablished.

    public static void minheapSiftdown(double[] heap, int i, int k) {
        int child = 2 * i + 1;                          
        if (child <= k) {
            if (child+1 <= k && heap[child] > heap[child+1])
                child++;                                  
            if (heap[i] > heap[child]) {
                swap(heap, i, child); 
                minheapSiftdown(heap, child, k); 
            }
        }
    }

    // In heap[0..k], move node heap[i] upwards by swapping with its
    // parent until the heap invariant is reestablished.
    public static void minheapSiftup(double[] heap, int i, int k) {
        if (0 < i) {
            int parent = (i - 1) / 2;
            if (heap[i] < heap[parent]) {
                swap(heap, i, parent); 
                minheapSiftup(heap, parent, k); 
            }
        }
    }
}
