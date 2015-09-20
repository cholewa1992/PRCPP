// For week 2

// Code from Goetz et al 5.6, written by Brian Goetz and Tim Peierls.
// Modifications by sestoft@itu.dk * 2014-09-08

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;
import java.util.function.Function;


public class TestCache {
	public static void main(String[] args) throws InterruptedException {
		Mark.systemInfo();

		switch(args[0]){
			case "0" : 
				for(int i = 1; i <= 16; i++){
				final int theadCount = i;
				Mark.mark7(String.format("Memoizer0 %6d", theadCount), j -> {
					Factorizer f = new Factorizer(); 
					exerciseFactorizer(new Memoizer0<Long,long[]>(f),theadCount);
					return f.getCount();
				});
			}
			break;
			case "1" : 
				for(int i = 1; i <= 16; i++){
				final int theadCount = i;
				Mark.mark7(String.format("Memoizer1 %6d", theadCount), j -> {
					Factorizer f = new Factorizer(); 
					exerciseFactorizer(new Memoizer1<Long,long[]>(f),theadCount);
					return f.getCount();
				});
			}
			break;
			case "2" : 
				for(int i = 1; i <= 16; i++){
				final int theadCount = i;
				Mark.mark7(String.format("Memoizer2 %6d", theadCount), j -> {
					Factorizer f = new Factorizer(); 
					exerciseFactorizer(new Memoizer2<Long,long[]>(f),theadCount);
					return f.getCount();
				});
			}
			break;
			case "3" : 
				for(int i = 1; i <= 16; i++){
				final int theadCount = i;
				Mark.mark7(String.format("Memoizer3 %6d", theadCount), j -> {
					Factorizer f = new Factorizer(); 
					exerciseFactorizer(new Memoizer3<Long,long[]>(f),theadCount);
					return f.getCount();
				});
			}
			break;
			case "4" : 
				for(int i = 1; i <= 16; i++){
				final int theadCount = i;
				Mark.mark7(String.format("Memoizer4 %6d", theadCount), j -> {
					Factorizer f = new Factorizer(); 
					exerciseFactorizer(new Memoizer4<Long,long[]>(f),theadCount);
					return f.getCount();
				});
			}
			break;
			case "5" : 
				for(int i = 1; i <= 16; i++){
				final int theadCount = i;
				Mark.mark7(String.format("Memoizer5 %6d", theadCount), j -> {
					Factorizer f = new Factorizer(); 
					exerciseFactorizer(new Memoizer5<Long,long[]>(f),theadCount);
					return f.getCount();
				});
			}
			break;
		}
	}

	private static void print(long[] arr) {
		for (long x : arr) 
			System.out.print(" " + x);
		System.out.println();
	}

	private static void exerciseFactorizer(Computable<Long, long[]> f, int count) { 
		final int threadCount = count;
		final long start = 10_000_000_000L, range = 2000L;


		Thread[] threads = new Thread[threadCount];

		for(int i = 0; i < threadCount; i++){

			long from1 = start,
			from2 =  start + range + i * range / 4, 
			to1 = from1 + range,
			to2 = from2 + range;


			threads[i] = new Thread(() -> {
				for(long j = from1; j < to1; j++){
					try { f.compute(j); } 
					catch (InterruptedException e) { throw launderThrowable(e.getCause()); }
				}

				for(long j = from2; j < to2; j++){
					try { f.compute(j); }
					catch (InterruptedException e) { throw launderThrowable(e.getCause()); }
				}
			});
			threads[i].start();
		}

		for(int i = 0; i < threadCount; i++){
			try{ threads[i].join(); }
			catch (Exception e) { throw launderThrowable(e.getCause()); }
		}
	}

	public static RuntimeException launderThrowable(Throwable t) {
		if (t instanceof RuntimeException) return (RuntimeException) t;
		else if (t instanceof Error) throw (Error) t;
		else throw new IllegalStateException("Not unchecked", t);
	}
}


// Interface that represents a function from A to V
interface Computable <A, V> {
	V compute(A arg) throws InterruptedException;
}

// Prime factorization is a function from Long to long[]
class Factorizer implements Computable<Long, long[]> {
	// For statistics only, count number of calls to factorizer:
	private final AtomicLong count = new AtomicLong(0);
	public long getCount() { return count.longValue(); }

	public long[] compute(Long wrappedP) {
		count.getAndIncrement();
		long p = wrappedP;
		ArrayList<Long> factors = new ArrayList<Long>();
		long k = 2;
		while (p >= k * k) {
			if (p % k == 0) {
				factors.add(k);
				p /= k;
			} else 
			k++;
		}
		// Now k * k > p and no number in 2..k divides p
		factors.add(p);

		long[] result = new long[factors.size()];
		for (int i=0; i<result.length; i++) 
			result[i] = factors.get(i);
		return result;
	}
}

class Memoizer0 <A, V> implements Computable<A, V> {
	private final Map<A, V> cache = new ConcurrentHashMap<A, V>();
	private final Computable<A, V> c;

	public Memoizer0(Computable<A, V> c) { this.c = c; }

	public V compute(A arg) throws InterruptedException {
		 return cache.computeIfAbsent(arg, (v) -> {
		 	try {return c.compute(v);}
			catch (InterruptedException e) { throw launderThrowable(e.getCause()); }
		 });
	}

	public static RuntimeException launderThrowable(Throwable t) {
		if (t instanceof RuntimeException) return (RuntimeException) t;
		else if (t instanceof Error) throw (Error) t;
		else throw new IllegalStateException("Not unchecked", t);
	}
}

class Memoizer1 <A, V> implements Computable<A, V> {
	private final Map<A, V> cache = new HashMap<A, V>();
	private final Computable<A, V> c;

	public Memoizer1(Computable<A, V> c) { this.c = c; }

	public synchronized V compute(A arg) throws InterruptedException {
		V result = cache.get(arg);
		if (result == null) {
			result = c.compute(arg);
			cache.put(arg, result);
		}
		return result;
	}
}

class Memoizer2 <A, V> implements Computable<A, V> {
	private final Map<A, V> cache = new ConcurrentHashMap<A, V>();
	private final Computable<A, V> c;

	public Memoizer2(Computable<A, V> c) { this.c = c; }

	public V compute(A arg) throws InterruptedException {
		V result = cache.get(arg);
		if (result == null) {
			result = c.compute(arg);
			cache.put(arg, result);
		}
		return result;
	}
}

class Memoizer3<A, V> implements Computable<A, V> {
	private final Map<A, Future<V>> cache 
	= new ConcurrentHashMap<A, Future<V>>();
	private final Computable<A, V> c;

	public Memoizer3(Computable<A, V> c) { this.c = c; }

	public V compute(final A arg) throws InterruptedException {
		Future<V> f = cache.get(arg);
		if (f == null) {
			Callable<V> eval = new Callable<V>() {
				public V call() throws InterruptedException {
					return c.compute(arg);
				}};
				FutureTask<V> ft = new FutureTask<V>(eval);
				cache.put(arg, ft);
				f = ft;
				ft.run();
			}
			try { return f.get(); } 
			catch (ExecutionException e) { throw launderThrowable(e.getCause()); }  
		}

		public static RuntimeException launderThrowable(Throwable t) {
			if (t instanceof RuntimeException)
				return (RuntimeException) t;
			else if (t instanceof Error)
				throw (Error) t;
			else
				throw new IllegalStateException("Not unchecked", t);
		}
	}

class Memoizer4<A, V> implements Computable<A, V> {
	private final Map<A, Future<V>> cache 
	= new ConcurrentHashMap<A, Future<V>>();
	private final Computable<A, V> c;

	public Memoizer4(Computable<A, V> c) { this.c = c; }

	public V compute(final A arg) throws InterruptedException {
		Future<V> f = cache.get(arg);
		if (f == null) {
			Callable<V> eval = new Callable<V>() {
				public V call() throws InterruptedException {
					return c.compute(arg);
				}};
				FutureTask<V> ft = new FutureTask<V>(eval);
				f = cache.putIfAbsent(arg, ft);
				if (f == null) { 
					f = ft; 
					ft.run();
				}
			}
			try { return f.get(); } 
			catch (ExecutionException e) { throw launderThrowable(e.getCause()); }  
		}

		public static RuntimeException launderThrowable(Throwable t) {
			if (t instanceof RuntimeException)
				return (RuntimeException) t;
			else if (t instanceof Error)
				throw (Error) t;
			else
				throw new IllegalStateException("Not unchecked", t);
		}
	}

class Memoizer5<A, V> implements Computable<A, V> {
	private final Map<A, Future<V>> cache = new ConcurrentHashMap<A, Future<V>>();
	private final Computable<A, V> c;

	public Memoizer5(Computable<A, V> c) { this.c = c; }

	public V compute(final A arg) throws InterruptedException {
    // AtomicReference is used as a simple assignable holder; no atomicity needed
		final AtomicReference<FutureTask<V>> ftr = new AtomicReference<FutureTask<V>>();
		Future<V> f = cache.computeIfAbsent(arg, 
			new Function<A,Future<V>>() { 
				public Future<V> apply(final A arg) {
					Callable<V> eval = new Callable<V>() {
						public V call() throws InterruptedException {
							return c.compute(arg);
						}
					};

					ftr.set(new FutureTask<V>(eval));
					return ftr.get();
				}
			}
			);

	    // Important to run() the future outside the computeIfAbsent():
		if (ftr.get() != null) ftr.get().run();
		try { return f.get(); } 
		catch (ExecutionException e) { throw launderThrowable(e.getCause()); }

	}

	public static RuntimeException launderThrowable(Throwable t) {
		if (t instanceof RuntimeException) return (RuntimeException) t;
		else if (t instanceof Error) throw (Error) t;
		else throw new IllegalStateException("Not unchecked", t);
	}
}
