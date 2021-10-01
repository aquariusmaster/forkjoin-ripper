# Fork/Join Pool The Ripper

In order to understand why we need the Fork/Join pool and how it can help us we can try to write our own MergeSort implementation that performs work in several threads.


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

Here, for each division of the array, we will manually create two threads. The problem will be that we will create a large number of threads very quickly. My machine managed to create 5,000 threads before it crashed due to lack of memory. 
![](src/test/resources/images/to_many_thread_error.png)    
Ok, we could independently divide the array into 2-4 parts and separately manually run them in several threads:

TO BE CONTINUED