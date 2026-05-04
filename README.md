# JavaCoroutines

WanAndroid API concurrency demos in Java and Kotlin, both using Retrofit.

## Java Virtual Threads for Concurrent API Calls

This section demonstrates how to achieve concurrent API calls using Java 21's Virtual Threads.
The `Main.java` file showcases the use of `Executors.newVirtualThreadPerTaskExecutor()` to run multiple simulated API requests concurrently.

To run the Java demo:
```bash
# Compile and run the Main class
./gradlew run
```
The `Main` class defines several `Callable` tasks, each simulating an API call with a delay using the `fetchApi` method. These tasks are submitted to a virtual thread executor, and their results are collected using `invokeAll()`. The total execution time will be close to the longest individual API call, demonstrating efficient concurrency.

## Kotlin Coroutines for Concurrent API Calls

The Kotlin demo (`WanAndroidKotlinDemo.kt`) utilizes Kotlin Coroutines for asynchronous and concurrent API requests.
It uses a Retrofit interface with `suspend` functions and executes banner and home article list requests concurrently with `coroutineScope` and `async`.

To run the Kotlin demo:
```bash
# Compile and run the WanAndroidKotlinDemoKt class
./gradlew runKotlinDemo
```
Alternatively, you can run `WanAndroidKotlinDemoKt` directly from IntelliJ IDEA.
