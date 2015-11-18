import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CyclicBarrier;

public class TestSimpleRWTryLock {


    public static void main(String[] args){
        SequentialTest(new SimpleRWTryLock());
        ConcurrentTest(new SimpleRWTryLock());
    }

    static class LongCounter{
        volatile int count = 0;
        public void increment(){ count++; };
        public int get(){ return count; };
    }

    public static void ConcurrentTest(final SimpleRWTryLock lock){
        final int n = 400_000, threadCount = 16, ratio = 8;
        LongCounter lc = new LongCounter();

        final CyclicBarrier startBarrier = new CyclicBarrier(threadCount + 1), 
              stopBarrier = startBarrier;

        final Thread[] threads = new Thread[threadCount];
        for (int t=0; t<threadCount; t++) {
            threads[t] = 
                new Thread(() -> { 
                    try { startBarrier.await(); } catch (Exception exn) { }
                    for (int i = 0; i < n; i++){

                        
                        if(i % ratio == 0){
                            while(!lock.writerTryLock());
                            lc.increment(); 
                            lock.writerUnlock();
                        }else{
                            while(!lock.readerTryLock());
                            lc.get();
                            lock.readerUnlock();
                        }

                    }
                    try { stopBarrier.await(); } catch (Exception exn) { }
                });
            threads[t].start();
        }
        try { startBarrier.await(); } catch (Exception exn) { }
        try { stopBarrier.await(); } catch (Exception exn) { }

        System.out.println(lc.get());
        assert lc.get() == (n * threadCount) / ratio;
    }

    public static void SequentialTest(SimpleRWTryLock lock){
        //Tests that an exception is thrown if we do not have the lock
        boolean failed = false;
        try {
            lock.readerUnlock();
        }catch(Exception e){
            failed = true;
        }
        assert failed;

        //Tests that we can acquire a read lock
        assert lock.readerTryLock();
        //Checks that we can take a lock again
        assert lock.readerTryLock();

        //We now release the locks
        lock.readerUnlock();

        //Tests that an exception is thrown if we do not have a read lock
        failed = false;
        try {
            lock.readerUnlock();
        }catch(Exception e){
            failed = true;
        }
        assert failed;

        //Tests that an exception is thrown if we do not have the write lock
        failed = false;
        try {
            lock.writerUnlock();
        }catch(Exception e){
            failed = true;
        }
        assert failed;

        //We take the write lock
        assert lock.writerTryLock();

        //Tests that the write lock can't be acquired if it is already taken 
        assert !lock.writerTryLock();

        //We now release the write lock
        lock.writerUnlock();

        //Tests that an exception is thrown if we do not have the write lock
        failed = false;
        try {
            lock.writerUnlock();
        }catch(Exception e){
            failed = true;
        }
        assert failed;
    }
}

class SimpleRWTryLock{
    private final AtomicReference<Holders> holders = new AtomicReference<Holders>();

    public boolean readerTryLock(){
        final Thread current = Thread.currentThread();
        Holders old;
        do{
            old = holders.get();
            if (old instanceof Writer) return false; //This means that it is a writer
        }while(!holders.compareAndSet(old, new ReaderList(current, (ReaderList) old)));
        return true;
    }

    public void readerUnlock(){
        final Thread current = Thread.currentThread();
        Holders old;
        do{
            old = holders.get();
            if(old instanceof Writer) throw new RuntimeException("The acquired lock is a write lock");
            if(old == null || !((ReaderList) old).contains(current)) throw new RuntimeException("Not lock holder");
        }while(!holders.compareAndSet(old, ((ReaderList) old).remove(current)));
    }
    public boolean writerTryLock(){
        final Thread current = Thread.currentThread();
        return holders.compareAndSet(null, new Writer(current));
    }
    public void writerUnlock(){
        final Thread current = Thread.currentThread();
        Holders val = holders.get();

        if(val == null) throw new RuntimeException("Not lock holder");
        if(val instanceof Writer && !((Writer) val).thread.equals(current))
            throw new RuntimeException("Not lock holder");

        if (!holders.compareAndSet(val, null))
            throw new RuntimeException("Something went horribly wrong");
    }

    private static abstract class Holders{}
    private static class ReaderList extends Holders{
        private final ReaderList next;
        private final Thread thread;
        public ReaderList(Thread thread, ReaderList next){
            this.thread = thread;
            this.next = next;
        }

        public boolean contains(Thread t){
            return thread.equals(t) || (next != null && next.contains(t));
        }

        public ReaderList remove(Thread t){
            ReaderList next = this.next != null ? this.next.remove(t) : null;
            return thread.equals(t) ? next : new ReaderList(thread, next);
        }
    }

    private static class Writer extends Holders{
        public final Thread thread;
        public Writer(Thread thread){
            this.thread = thread;
        }
    }

}
