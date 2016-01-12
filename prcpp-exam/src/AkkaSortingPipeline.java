import java.io.*;
import akka.actor.*;

public class AkkaSortingPipeline{
    public static void main(String[] args){
        final ActorSystem system = ActorSystem.create("AkkaSortingPipeline");

        final ActorRef drain = system.actorOf(Props.create(EchoActor.class), "Ekko");

        int P = 4;
        int N = 100;
        ActorRef[] sorters = new ActorRef[P];

        for(int i = 0; i < P; i++){
            sorters[i] = system.actorOf(Props.create(SorterActor.class),"Sorter"+(i+1));
        }

        //Setting up chain
        ActorRef prev = drain;
        for(int i = 0; i < P; i++){
            sorters[i].tell(new InitMessage(prev, N/P), ActorRef.noSender()); 
            prev = sorters[i];
        }

        final double[] arr = DoubleArray.randomPermutation(N); 

        for(int i = 0; i < arr.length; i++){
            prev.tell(new DoubleMessage(arr[i]),ActorRef.noSender());
        }

        for(int i = 0; i < arr.length; i++){
            prev.tell(new DoubleMessage(Double.POSITIVE_INFINITY),ActorRef.noSender());
        }

        try {
            System.out.println("Press return to terminate...");
            System.in.read();
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            system.shutdown();
        }
    }
}

class SorterActor extends UntypedActor{
    private double[] heap;
    private int heapSize = 0;
    private ActorRef out;
    public void onReceive(Object o) throws Exception{
        if(o instanceof InitMessage){
            InitMessage msg = (InitMessage) o; 
            heap = new double[msg.capacity];
            heapSize = 0;
            out = msg.to;
        }else if(o instanceof DoubleMessage){
            if(heap == null) return;
            DoubleMessage msg = (DoubleMessage) o;

            if(heapSize < heap.length){
                heap[heapSize++] = msg.value;
                DoubleArray.minheapSiftup(heap, heapSize-1, heapSize-1);
            } else if (msg.value <= heap[0]){
                out.tell(msg, ActorRef.noSender());
            } else {
                double least = heap[0];
                heap[0] = msg.value;
                DoubleArray.minheapSiftdown(heap,0,heapSize-1);
                out.tell(new DoubleMessage(least), ActorRef.noSender());
            } 
        }
    }

}

class EchoActor extends UntypedActor{
    public void onReceive(Object o) throws Exception{
        if(o instanceof DoubleMessage){
            DoubleMessage msg = (DoubleMessage) o;
            System.out.print(msg.value + ", ");
        }
    }
}

@SuppressWarnings("serial")
class DoubleMessage implements Serializable{
    public final double value;
    public DoubleMessage(double value){
        this.value = value;
    }
}

@SuppressWarnings("serial")
class InitMessage implements Serializable{
    public final ActorRef to;
    public final int capacity;
    public InitMessage(ActorRef to, int capacity){
        this.to = to;
        this.capacity = capacity;
    }
}


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

