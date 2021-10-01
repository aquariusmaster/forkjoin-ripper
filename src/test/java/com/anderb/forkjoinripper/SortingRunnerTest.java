package com.anderb.forkjoinripper;

import lombok.SneakyThrows;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SortingRunnerTest {

    private final Supplier<int[]> arraySupplier = () -> ThreadLocalRandom.current().ints(20_000_000).toArray();

    @BeforeAll
    static void beforeAll() {
        System.out.println("CPU number: " + Runtime.getRuntime().availableProcessors());
    }

    @BeforeEach
    void setUp() {
        System.gc();
    }

    @Test
    @Order(1)
    @DisplayName("Run non-parallel Merge sort")
    void test1() {
        System.out.println("Regular merge sort by MergeSort:");
        var statistics = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(arr -> {
                    long start = System.nanoTime();
                    MergeSort.mergeSort(arr);
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + "ms"))
                .summaryStatistics();
        System.out.println(statistics);
    }

    @Test
    @Order(2)
    @DisplayName("Run Merge Sort in several threads")
    void test2() {
        System.out.println("Concurrent merge sort with dividing on several parts and run it in 4 threads:");
        var statistics = Stream.generate(arraySupplier)
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
        System.out.println(statistics);
    }

    @Test
    @Order(3)
    @DisplayName("Run Merge Sort in several threads in Thread pool")
    void test3() {
        System.out.println("Concurrent merge sort with dividing on several parts by using a thread pool:");
        ExecutorService pool2 = Executors.newFixedThreadPool(4);
        var statistics = Stream.generate(arraySupplier)
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
        System.out.println(statistics);
        pool2.shutdown();
    }

    @Test
    @Order(4)
    @DisplayName("Create thread on each divide")
    void test4() {
        System.out.println("Concurrent merge sort by creating threads:");
        var statistics = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(array -> {
                    long start = System.nanoTime();
//                    MergeSortThread.mergeSort(array); //causes a problem
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(statistics);
    }

    @Test
    @Order(5)
    @DisplayName("Divide and run on a fixed size thread pool ")
    void test5() {
        System.out.println("Concurrent merge sort with controlled number of thread by using ThreadPool:");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        var statistics = Stream.generate(arraySupplier)
                .limit(10)
                .mapToLong(array -> {
                    long start = System.nanoTime();
//                    new MergeSortExecutor(array, pool).merge(); //causes a problem
                    return System.nanoTime() - start;
                })
                .map(elapsedNanos -> MILLISECONDS.convert(elapsedNanos, NANOSECONDS))
                .peek(elapsedTime -> System.out.println(elapsedTime + " ms"))
                .summaryStatistics();
        System.out.println(statistics);
        pool.shutdown();
    }

    @Test
    @Order(6)
    @DisplayName("Run concurrent merge sort by MergeSortAction")
    void test6() {
        System.out.println("Concurrent merge sort by MergeSortAction");
        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
        var statistics = Stream.generate(arraySupplier)
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
        System.out.println(statistics);

    }

    @Test
    @Order(7)
    @DisplayName("Concurrent merge sort by MergeSortTask")
    void test7() {
        System.out.println("Concurrent merge sort by MergeSortTask");
        var statistics = Stream.generate(arraySupplier)
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
        System.out.println(statistics);
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