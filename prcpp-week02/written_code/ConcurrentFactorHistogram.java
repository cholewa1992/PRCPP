
import java.util.concurrent.atomic.*;
public class ConcurrentFactorHistogram{

    public static void main(String[] args){

        int range = 5_000_000;
        int nthreads = 10;

        final Histogram his = new Histogram5(25);

        Thread[] threads = new Thread[nthreads];

        for(int i = 0; i < nthreads; i++){

            final int thread_number = i;

            threads[thread_number] = new Thread(() -> {
                
                //I'm here trying to evenly distribute the load between theads.
                for(int j = 0; j < range; j++){ 
                    if(j % nthreads == thread_number)
                        his.increment(countFactors(j));
                }

            });

            threads[thread_number].start();
        }

        for(int i = 0; i < nthreads; i++){
            try{threads[i].join();}
            catch(Exception e){ throw new RuntimeException();}
        }
        
        int total = 0;
        for(int i = 0; i < his.getSpan(); i++){
            total += his.getCount(i);
            System.out.println(i + ": \t" + his.getCount(i));
        }
        System.out.println("Total: " + total);
    }

    public static int countFactors(int p) { 
        if (p < 2)
            return 0;
        int factorCount = 1, k = 2;
        while (p >= k * k) {
            if (p % k == 0) {
                factorCount++;
                p /= k;
            } else k++;
        }
        return factorCount;
    }
}

interface Histogram {
    public void increment(int bin);
    public int getCount(int bin);
    public int getSpan();
    public int[] getBins();
}

class Histogram2 implements Histogram {
    private final int[] counts;
    public Histogram2(int span) {
        this.counts = new int[span];
    }
    public synchronized void increment(int bin) {
        counts[bin] = counts[bin] + 1;
    }
    public synchronized int getCount(int bin) {
        return counts[bin];
    }
    public int getSpan() {
        return counts.length;
    }
    public int[] getBins(){
        return counts.clone();
    }
}

class Histogram3 implements Histogram{

    private final AtomicInteger[] counts;
    public Histogram3(int span) {
        this.counts = new AtomicInteger[span];

        for(int i = 0; i < span; i++){
            this.counts[i] = new AtomicInteger();
        }

    }
    public void increment(int bin) {
        counts[bin].getAndIncrement();
    }
    public int getCount(int bin) {
        return counts[bin].get();
    }
    public int getSpan() {
        return counts.length;
    }

    public int[] getBins(){
        int[] bins = new int[counts.length];
        for(int i = 0; i < counts.length; i++){
            bins[i] = counts[i].get();
        }
        return bins;
    }
}

class Histogram4 implements Histogram{

    private final AtomicIntegerArray counts;
    public Histogram4(int span) {
        this.counts = new AtomicIntegerArray(span);
    }
    public void increment(int bin) {
        counts.getAndIncrement(bin);
    }
    public int getCount(int bin) {
        return counts.get(bin);
    }
    public int getSpan() {
        return counts.length();
    }

    public int[] getBins(){
        int[] bins = new int[counts.length()];
        for(int i = 0; i < counts.length(); i++){
            bins[i] = counts.get(i);
        }
        return bins;
    }
}

class Histogram5 implements Histogram{
    private final LongAdder[] counts;
    public Histogram5(int span) {
        this.counts = new LongAdder[span];

        for(int i = 0; i < span; i++){
            this.counts[i] = new LongAdder();
        }

    }
    public void increment(int bin) {
        counts[bin].increment();
    }
    public int getCount(int bin) {
        return counts[bin].intValue();
    }
    public int getSpan() {
        return counts.length;
    }

    public int[] getBins(){
        int[] bins = new int[counts.length];
        for(int i = 0; i < counts.length; i++){
            bins[i] = counts[i].intValue();
        }
        return bins;
    }
}