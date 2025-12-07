# Fork/Join Pool The Ripper

[English](#english) · [Українська](#українська)

## English
To understand why we need the `Fork/Join` pool and how it can help us, let's try to write our own `MergeSort` implementation that performs work across several threads.

```java
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
```

In this example, for each division of the array, we manually create two threads. The problem is that we create a large number of threads very quickly. My machine managed to create 5,000 threads before it crashed due to a lack of memory.
![](src/test/resources/images/to_many_thread_error.png)

Alternatively, we could independently divide the array into 2-4 parts and manually run them in separate threads:

Example with 2 threads:
![Split the work into two threads](src/test/resources/images/divide_on_2_threads.png)

Example with 4 threads:

![Split the work into four threads](src/test/resources/images/divide_on_4_threads.png)

This works—the job indeed took two to four times less time.

However, creating threads manually violates the SOLID Single Responsibility principle. It is better to delegate thread management to an `ExecutorService`:

![Run work on a thread pool](src/test/resources/images/run_threads_on_pool.png)

Now, we split the work into parts and execute each part in a separate thread. Since each part runs on a separate CPU core, we can improve performance.

For this specific algorithm, this approach might be optimal.

But how can we further improve performance?

Broadly speaking, there are three main stages:
1.  **Split** the task into parts (this happens in the main thread).
2.  **Execute** each part of the task in parallel on worker threads.
3.  **Merge** the results in the main thread after waiting for all parts to finish.

![Three stages of the job](src/test/resources/images/job_parts.png)

In stage #1, only one thread is working. In stage #2, all worker threads (except main) run. In stage #3, only the main thread works again.

In stages #1 and #3, the work runs sequentially on a single thread, so we don't utilize parallelism there (recall [Amdahl's law](https://en.wikipedia.org/wiki/Amdahl%27s_law)).

Furthermore, if the task is unbalanced, faces unpredictable delays (e.g., I/O), or if the machine shares resources with other processes, some worker threads in stage #2 might finish earlier and sit idle—just like the main thread—waiting until slower tasks complete. This leads to suboptimal hardware resource utilization.

Let's try to optimize these stages by parallelizing them and pushing more work into worker threads.

If we try to split the array and perform sorting inside `MergeSort` using an `ExecutorService` with a limited thread pool, it might look like this:

```java
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
```

Running this:

![Out of threads](src/test/resources/images/no_more_threads.png)

We see that all available threads are exhausted.

The problem is that the `get()` call blocks the current thread, causing all threads in our pool to eventually become blocked waiting for others, leading to a deadlock or resource exhaustion.

Organizing correct thread management becomes difficult here.

This is where the **Fork/Join pool** helps—it extends the capabilities of a regular `ExecutorService`.

`ForkJoinPool` is a subclass of `ExecutorService`. This pool accepts a special kind of task: `ForkJoinTask`.

```java
invoke(ForkJoinTask<T> task)
```

The main concrete implementations are `RecursiveTask` and `RecursiveAction`. Each has an abstract `compute()` method that you must implement. `RecursiveTask#compute()` returns a value, while `RecursiveAction#compute()` returns `void`.

```java
@Override
protected void compute() {
    if (arr.length < 2) return;
    var n = arr.length / 2;
    var left = Arrays.copyOfRange(arr, 0, n);
    var right = Arrays.copyOfRange(arr, n, arr.length);
    var leftAction = new MergeSortAction(left);
    var rightAction = new MergeSortAction(right);

    leftAction.fork();
    rightAction.fork(); // fork() here is redundant for the right part; it's better to call compute() directly
    
    leftAction.join();
    rightAction.join();
    
    merge(arr, left, right);
}
```

Here, the `fork()` call is similar to `executor.submit(Runnable)`—it tells the `ForkJoin` pool that we want to split the work and run a separate task on another worker thread.

The `join()` method is used when we need the task's result. The key difference is that `join()` **does not block the native thread**: the worker thread can temporarily switch to other pending tasks while waiting. This means our threads will not be permanently blocked, preventing thread exhaustion.

The fact that `join()` keeps the thread active is the main advantage of the Fork/Join pool—it efficiently schedules and balances tasks across worker threads.

![ForkJoinPool ActionTask](src/test/resources/images/run_action_on_FJ.png)


---

## Українська

Щоб зрозуміти, навіщо нам потрібен `Fork/Join` пул і як він може нам допомогти, спробуємо написати власну реалізацію `MergeSort`, яка виконує роботу в кількох потоках.

```java
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
```

У цьому прикладі для кожного розбиття масиву ми вручну створюємо два потоки. Проблема полягає в тому, що ми дуже швидко створимо величезну кількість потоків. Моя машина змогла створити 5 000 потоків, перш ніж програма впала через нестачу пам'яті.

![](src/test/resources/images/to_many_thread_error.png)

Гаразд, ми могли б самостійно розділити масив на 2–4 частини і запустити їх вручну в окремих потоках:

Приклад для 2 потоків:
![Розподіл роботи на два потоки](src/test/resources/images/divide_on_2_threads.png)

Приклад для 4 потоків:

![Розподіл роботи на чотири потоки](src/test/resources/images/divide_on_4_threads.png)

Це спрацювало — виконання роботи справді зайняло в два–чотири рази менше часу.

Проте створення потоків вручну порушує принцип єдиної відповідальності (Single Responsibility Principle) з SOLID. Краще делегувати управління потоками `ExecutorService`:

![Запуск роботи на пулі потоків](src/test/resources/images/run_threads_on_pool.png)

Отже, ми розбили роботу на частини і виконали кожну з них в окремому потоці. Оскільки кожна частина виконувалася на окремому ядрі процесора, нам вдалося підвищити продуктивність.

Для цього конкретного алгоритму такий підхід може бути оптимальним.

Але як ми можемо ще більше покращити продуктивність?

У загальному вигляді можна виділити три основні етапи:
1.  **Розділення** (Split) задачі на частини (відбувається в головному потоці).
2.  **Виконання** (Execute) кожної частини задачі паралельно у воркер-потоках.
3.  **Об'єднання** (Merge) результатів у головному потоці після очікування завершення всіх частин.

![Три етапи виконання задачі](src/test/resources/images/job_parts.png)

На етапі №1 працює лише один потік. На етапі №2 працюють усі воркер-потоки (окрім main). На етапі №3 знову працює лише головний потік.

На етапах №1 та №3 робота виконується послідовно в одному потоці, тому тут ми не отримуємо переваг від паралелізму (згадайте [закон Амдала](https://uk.wikipedia.org/wiki/%D0%97%D0%B0%D0%BA%D0%BE%D0%BD_%D0%90%D0%BC%D0%B4%D0%B0%D0%BB%D0%B0)).

Крім того, якщо задача незбалансована, виникають непередбачувані затримки (наприклад, I/O), або машина ділить ресурси з іншими процесами, деякі воркер-потоки на етапі №2 можуть завершити роботу раніше і простоювати (idle) в очікуванні — як і головний потік — поки повільніші задачі не завершаться. Це веде до неефективного використання апаратних ресурсів.

Спробуймо оптимізувати ці етапи, розпаралеливши їх і переклавши більше роботи на воркер-потоки.

Якщо спробувати розбивати масив і виконувати сортування всередині `MergeSort`, використовуючи `ExecutorService` з обмеженим пулом потоків, код може виглядати так:

```java
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
        leftFuture.get(); // блокує потік
        System.out.println("Waiting for the result of right sorting");
        rightFuture.get(); // блокує потік

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
```

Запуск:

![Закінчились потоки](src/test/resources/images/no_more_threads.png)

Ми бачимо, що всі доступні потоки вичерпані.

Проблема полягає в тому, що виклик `get()` блокує поточний потік, і з часом усі потоки з нашого пулу опиняються в стані очікування (блокування).

Організувати правильне управління потоками в такій ситуації стає складно.

Саме тут нам на допомогу приходить **Fork/Join пул**, який розширює можливості звичайного `ExecutorService`.

`ForkJoinPool` є підкласом `ExecutorService`. Цей пул приймає до виконання особливий тип задач — `ForkJoinTask`.

```java
invoke(ForkJoinTask<T> task)
```

Основними реалізаціями є `RecursiveTask` та `RecursiveAction`. У кожного з них є абстрактний метод `compute()`, який потрібно реалізувати. `RecursiveTask#compute()` повертає значення, а `RecursiveAction#compute()` — `void`.

```java
@Override
protected void compute() {
    if (arr.length < 2) return;
    var n = arr.length / 2;
    var left = Arrays.copyOfRange(arr, 0, n);
    var right = Arrays.copyOfRange(arr, n, arr.length);
    var leftAction = new MergeSortAction(left);
    var rightAction = new MergeSortAction(right);

    leftAction.fork();
    rightAction.fork(); // .fork() для правої частини тут зайвий, краще викликати compute() безпосередньо
    
    leftAction.join();
    rightAction.join();
    
    merge(arr, left, right);
}
```

Виклик `fork()` — це майже те саме, що і `executor.submit(Runnable)`. Ми повідомляємо `ForkJoin` пулу, що хочемо розпаралелити виконання і запустити окрему задачу, яка виконається в іншому воркер-потоці.

Метод `join()` використовується, коли нам потрібен результат задачі. Насправді **`join()` не блокує нативний потік**: воркер-потік може тимчасово переключитися на виконання інших задач. Це означає, що наші потоки не будуть заблоковані і не вичерпаються.

Те, що `join()` залишає потік активним — це головна перевага `Fork/Join` пулу. Він намагається ефективно розподіляти та балансувати задачі між воркер-потоками.

![ForkJoinPool ActionTask](src/test/resources/images/run_action_on_FJ.png)
