
import java.util.concurrent.atomic.*;
public class ConcurrentFactorCount{

    public static void main(String[] args){

        int range = 5_000_000;
        int nthreads = 10;
        //final MyAtomicInteger sum = new MyAtomicInteger();
        final AtomicInteger sum = new AtomicInteger();
        Thread[] threads = new Thread[nthreads];

        for(int i = 0; i < nthreads; i++){

            final int thread_number = i;

            threads[thread_number] = new Thread(() -> {

                //I'm here trying to evenly distribute the load between theads.
                for(int j = 0; j < range; j++){ 
                    if(j % nthreads == thread_number) 
                        sum.addAndGet(countFactors(j));
                }

            });

            threads[thread_number].start();
        }

        for(int i = 0; i < nthreads; i++){
            try{threads[i].join();}
            catch(Exception e){ throw new RuntimeException();}
        }
    
        System.out.println(sum.get());
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