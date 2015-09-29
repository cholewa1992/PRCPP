import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Created by Jacob on 29/09/15.
 */
public class TestTaskTime<T extends ExecutorService> {

    public static void main(String[] args){
        Mark.systemInfo();
        new TestTaskTime("WSP", () -> Executors.newWorkStealingPool());
        new TestTaskTime("CTP", () -> Executors.newCachedThreadPool());
    }

    private static final int count = 1000;
    private String name;
    private Supplier<ExecutorService> es_factory;

    public TestTaskTime(String name, Supplier<ExecutorService> es_factory){
        this.name = name;
        this.es_factory = es_factory;
        incr_notask();
        incr_task_create();
        incr_task_create_submit_cancel();
        incr_task_create_submit_get();
    }


    public static long incr_serial(){
        AtomicLong al = new AtomicLong();

        for(int i = 0; i < count; i ++){
            al.incrementAndGet();
        }

        return al.get();
    }

    public void incr_notask(){

        ExecutorService executor = es_factory.get();

        Mark.mark7(name + ": No task", (i) -> {
            incr_serial();
            return i;
        });
    }

    public void incr_task_create(){

        ExecutorService executor = es_factory.get();

        Mark.mark7(name + ": Task c", (i) -> {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    incr_serial();
                }
            };
            return i;
        });

        executor.shutdown();
    }

    public void incr_task_create_submit_cancel(){

        ExecutorService executor = es_factory.get();

        Mark.mark7(name + ": Task csc", (i) -> {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    incr_serial();
                }
            };

            Future<?> f = executor.submit(r);
            f.cancel(true);
            return i;
        });

        executor.shutdown();
    }

    public void incr_task_create_submit_get(){

        ExecutorService executor = es_factory.get();

        Mark.mark7(name + ": Task csg", (i) -> {
            long result;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    incr_serial();
                }
            };

            Future<?> f = executor.submit(r);
            try { f.get(); }
            catch (InterruptedException exn) { System.out.println(exn); }
            catch (ExecutionException exn) { throw new RuntimeException(exn); }
            return i;
        });

        executor.shutdown();
    }
}
