package com.anderb.forkjoinripper;

import lombok.SneakyThrows;

import java.util.Arrays;

public class MergeSortThread {

    @SneakyThrows
    public static void mergeSort(int[] arr) {
        System.out.println("Thread: " + Thread.currentThread().getName());
        if (arr.length < 2) return;
        int n = arr.length / 2;
        int[] left = Arrays.copyOfRange(arr, 0, n);
        int[] right = Arrays.copyOfRange(arr, n, arr.length);

        Thread leftThread = new Thread(() -> mergeSort(left));
        leftThread.start();
        Thread rightThread = new Thread(() -> mergeSort(right));
        rightThread.start();
        leftThread.join();
        rightThread.join();
        merge(arr, left, right);
    }

    private static void merge(int[] arr, int[] left, int[] right) {
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
