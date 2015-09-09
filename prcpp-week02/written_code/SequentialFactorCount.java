public class SequentialFactorCount{

    public static void main(String[] args){
        int sum = 0;
        for(int i = 0; i < 5_000_000; i++){
            sum += countFactors(i);
        }
        System.out.println(sum);
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