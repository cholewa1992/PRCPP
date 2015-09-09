public class MyAtomicInteger{
    
    int i;

    public synchronized int addAndGet(int amount){
        return i += amount;
    }

    public synchronized int get(){
        return i;
    }
}
