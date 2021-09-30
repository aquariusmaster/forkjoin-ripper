package com.anderb.forkjoinripper;

import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class SortingRunner {
    public static void main(String[] args) {
        System.out.println("CPU number: " + Runtime.getRuntime().availableProcessors());
        Supplier<int[]> arraySupplier = () -> ThreadLocalRandom.current().ints(20_000_000).toArray();

        System.out.println("Regular merge sort by MergeSort:");
        var test1 = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(arr -> {
                    long start = System.nanoTime();
                    MergeSort.mergeSort(arr);
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + "ms"))
                .summaryStatistics();
        System.out.println(test1);

        System.gc();

        System.out.println("Concurrent merge sort by MergeSortAction");
        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
        var test2 = Stream.generate(arraySupplier)
                .limit(10)
                .map(MergeSortAction::new)
                .mapToLong(task -> {
                    long start = System.nanoTime();
                    forkJoinPool.invoke(task);
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(test2);

        System.gc();

        System.out.println("Concurrent merge sort by MergeSortTask with threshold 20000:");
        var test3 = Stream.generate(arraySupplier)
                .limit(10)
                .map(MergeSortTask::new)
                .mapToLong(task -> {
                    long start = System.nanoTime();
                    task.invoke();
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(test3);

        System.gc();

        System.out.println("Concurrent merge sort by creating threads:");
        var test4 = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(array -> {
                    long start = System.nanoTime();
//                    MergeSortThread.mergeSort(array); //causes a problem
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(test4);

        System.gc();

        System.out.println("Concurrent merge sort with controlled number of thread by using ThreadPool:");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        var test5 = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(array -> {
                    long start = System.nanoTime();
//                    new MergeSortExecutor(array, pool).merge(); //causes a problem
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(test5);
        pool.shutdown();

        System.gc();

        System.out.println("Concurrent merge sort with dividing on several parts and run it in 4 threads:");
        var test6 = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(array -> {
                    long start = System.nanoTime();
                    int n = array.length / 4;
                    int[] p1 = Arrays.copyOfRange(array, 0, n);
                    int[] p2 = Arrays.copyOfRange(array, n, n * 2);
                    int[] p3 = Arrays.copyOfRange(array, n * 2, n * 3);
                    int[] p4 = Arrays.copyOfRange(array, n * 3, array.length);
                    Thread t1 = new Thread(() -> MergeSort.mergeSort(p1));
                    Thread t2 = new Thread(() -> MergeSort.mergeSort(p2));
                    Thread t3 = new Thread(() -> MergeSort.mergeSort(p3));
                    Thread t4 = new Thread(() -> MergeSort.mergeSort(p4));
                    t1.start();
                    t2.start();
                    t3.start();
                    t4.start();
                    join(t1);
                    join(t2);
                    int[] m1 = new int[n * 2];
                    MergeSort.merge(m1, p1, p2);
                    join(t3);
                    join(t4);
                    int[] m2 = new int[n * 2];
                    MergeSort.merge(m2, p3, p4);
                    MergeSort.merge(array, m1, m2);
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(test6);

        System.gc();

        System.out.println("Concurrent merge sort with dividing on several parts by using a thread pool:");
        ExecutorService pool2 = Executors.newFixedThreadPool(4);
        var test7 = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(array -> {
                    long start = System.nanoTime();
                    int n = array.length / 2;
                    int[] left = Arrays.copyOfRange(array, 0, n);
                    int[] right = Arrays.copyOfRange(array, n, array.length);
                    Future<?> leftFuture = pool2.submit(() -> MergeSort.mergeSort(left));
                    Future<?> rightFuture = pool2.submit(() -> MergeSort.mergeSort(right));
                    get(leftFuture);
                    get(rightFuture);
                    MergeSort.merge(array, left, right);
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(test7);
        pool2.shutdown();
    }

    @SneakyThrows
    private static void join(Thread thread) {
        thread.join();
    }

    @SneakyThrows
    private static void get(Future<?> thread) {
        thread.get();
    }
}
