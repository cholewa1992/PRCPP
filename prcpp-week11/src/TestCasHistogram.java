// For week 10
// sestoft@itu.dk * 2014-11-05, 2015-10-14

// Compile and run like this:
//   javac -cp ~/lib/multiverse-core-0.7.0.jar TestStmHistogram.java
//   java -cp ~/lib/multiverse-core-0.7.0.jar:. TestStmHistogram

import org.multiverse.api.references.*;
import static org.multiverse.api.StmUtils.*;
import org.multiverse.api.LockMode;
import org.multiverse.api.Txn;
import org.multiverse.api.callables.TxnVoidCallable;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CyclicBarrier;

class TestCasHistogram {
    public static void main(String[] args) {
        countPrimeFactors(new CasHistogram(30));
        countPrimeFactors(new StmHistogram(30));
    }

    private static void countPrimeFactors(final Histogram histogram) {
        final int range = 4_000_000;
        final int threadCount = 10, perThread = range / threadCount;
        final CyclicBarrier startBarrier = new CyclicBarrier(threadCount + 1), 
              stopBarrier = startBarrier;
        final Thread[] threads = new Thread[threadCount];
        for (int t=0; t<threadCount; t++) {
            final int from = perThread * t, 
                  to = (t+1 == threadCount) ? range : perThread * (t+1); 
            threads[t] = 
                new Thread(() -> { 
                    try { startBarrier.await(); } catch (Exception exn) { }
                    for (int p=from; p<to; p++) 
                        histogram.increment(countFactors(p));
                    try { stopBarrier.await(); } catch (Exception exn) { }
                });
            threads[t].start();
        }
        try { startBarrier.await(); } catch (Exception exn) { }
        Mark.Timer t = new Mark.Timer();
        try { stopBarrier.await(); } catch (Exception exn) { }
        double time = t.check() * 1e3;
        System.out.printf("%6.1f ms%n", time);
        dump(histogram);
    }

    public static void dump(Histogram histogram) {
        int totalCount = 0;
        for (int bin=0; bin<histogram.getSpan(); bin++) {
            System.out.printf("%4d: %9d%n", bin, histogram.getCount(bin));
            totalCount += histogram.getCount(bin);
        }
        System.out.printf("      %9d%n", totalCount);
    }

    public static int countFactors(int p) {
        if (p < 2) 
            return 0;
        int factorCount = 1, k = 2;
        while (p >= k * k) {
            if (p % k == 0) {
                factorCount++;
                p /= k;
            } else 
                k++;
        }
        return factorCount;
    }
}

interface Histogram {
    void increment(int bin);
    int getCount(int bin);
    int getSpan();
    int[] getBins();
    int getAndClear(int bin);
    void transferBins(Histogram hist);
}

class CasHistogram implements Histogram {

    final AtomicInteger[] bins;

    public CasHistogram(int span) {
        bins = new AtomicInteger[span];
        for(int i = 0; i < span; i++){
            bins[i] = new AtomicInteger();
        }
    }

    public void increment(int bin) {
        int old;
        do {
            old = bins[bin].get();
        } while (!bins[bin].compareAndSet(old, old + 1));
    }


    public int getCount(int bin) {
        return bins[bin].get();
    }

    public int getSpan() {
        return bins.length;
    }


    public int[] getBins() {
        int[] bins = new int[getSpan()];
        for(int i = 0; i < bins.length; i++){
            bins[i] = this.bins[i].get();
        }
        return bins;
    }

    public int getAndClear(int bin) {
        int old;
        do {
            old = bins[bin].get();
        } while (!bins[bin].compareAndSet(old, 0));
        return old;
    }

    public void transferBins(Histogram hist) {
        if(hist.getSpan() != bins.length) throw new RuntimeException("The histograms must have the same length");
        int old, nextVal;
        for(int i = 0; i < bins.length; i++){
            nextVal = hist.getAndClear(i);
            do {
                old = bins[i].get(); 
            } while (!bins[i].compareAndSet(old, old + nextVal));
        }
    }
}

class StmHistogram implements Histogram {
    private final TxnInteger[] counts;

    public StmHistogram(int span) {

        counts = new TxnInteger[span];

        for(int i = 0; i < span; i++)
            counts[i] = newTxnInteger(0);

    }

    public void increment(int bin) {
        atomic(() -> {
            counts[bin].set(counts[bin].get() + 1); 
        });
    }


    public int getCount(int bin) {
        return atomic(() -> {
            return counts[bin].get();
        });
    }

    public int getSpan() {
        return counts.length; //This is immutable so no need for anything fancy 
    }

    public int[] getBins() {
        return atomic(() -> {
            int[] bins = new int[counts.length];
            for(int i = 0; i < counts.length; i++){
                bins[i] = counts[i].get();
            }
            return bins;
        });
    }

    public int getAndClear(int bin) {
        return atomic(() -> {
            int v = counts[bin].get();
            counts[bin].set(0);
            return v;
        });
    }

    public void transferBins(Histogram hist) {
        if(counts.length != hist.getSpan()) 
            throw new RuntimeException("The histograms must have the same length"); 
        for(int i = 0; i < counts.length; i++){
            final int j = i;
            atomic(() -> {
                counts[j].set( hist.getAndClear(j) + getCount(j) );
            });
        }
    }
}

