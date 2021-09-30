package com.anderb.forkjoinripper;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
public class MergeSortAction extends RecursiveAction {
    private final static int THRESHOLD = 1_000_000;

    private final int[] arr;

    @Override
    protected void compute() {
        if (arr.length < 2) return;
        var n = arr.length / 2;
        var left = Arrays.copyOfRange(arr, 0, n);
        var right = Arrays.copyOfRange(arr, n, arr.length);
        var leftAction = new MergeSortAction(left);
        var rightAction = new MergeSortAction(right);

        if (arr.length > THRESHOLD) {
            leftAction.fork();
            rightAction.compute();
            leftAction.join();
        } else {
            leftAction.compute();
            rightAction.compute();
        }
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