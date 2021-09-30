package com.anderb.forkjoinripper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
public class MergeSortExecutor {

    private final int[] arr;
    private final ExecutorService executor;

    @SneakyThrows
    protected void merge() {
        if (arr.length < 2) return;
        var n = arr.length / 2;
        var left = Arrays.copyOfRange(arr, 0, n);
        var right = Arrays.copyOfRange(arr, n, arr.length);
        var leftFuture = executor.submit(() -> new MergeSortExecutor(left, executor).merge());
        var rightFuture = executor.submit(() -> new MergeSortExecutor(right, executor).merge());

        System.out.println("Waiting for the result of left sorting");
        leftFuture.get(); // blocks thread
        System.out.println("Waiting for the result of right sorting");
        rightFuture.get(); // blocks thread

        merge(arr, left, right);
    }

    private void merge(int[] arr, int[] left, int[] right) {
        int i = 0, j = 0;
        while (i < left.length && j < right.length) {
            if (left[i] > right[j]) {
                arr[i + j] = right[j++];
            } else {
                arr[i + j] = left[i++];
            }
        }
        System.arraycopy(left, i, arr, i + j, left.length - i);
        System.arraycopy(right, j, arr, i + j, right.length - j);
    }
}