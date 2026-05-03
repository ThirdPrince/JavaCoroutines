# JavaCoroutines

WanAndroid API concurrency demos in Java and Kotlin, both using Retrofit.

## Java Retrofit + virtual threads

```bash
gradle run
```

`Main` runs `WanAndroidJavaDemo`, which executes the banner and home article list Retrofit `Call<ResponseBody>` requests on virtual threads.

## Kotlin Retrofit + coroutines

In IntelliJ IDEA, run `WanAndroidKotlinDemoKt`.

The Kotlin demo uses a Retrofit interface with `suspend` functions and runs the banner/home article list requests with `coroutineScope + async`.

```bash
gradle runKotlinDemo
```
