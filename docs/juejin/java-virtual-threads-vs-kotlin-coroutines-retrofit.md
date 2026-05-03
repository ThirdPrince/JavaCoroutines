# Java 虚拟线程 vs Kotlin 协程：用 Retrofit 请求 WanAndroid，看两种并发写法到底差在哪

前面聊 Kotlin 协程时，我们一直在说一句话：

**协程不是为了让你“能并发”，而是为了让并发代码更好写、更好管。**

但如果你是 Java 开发者，尤其已经开始了解 Project Loom 和虚拟线程，可能会有一个很自然的问题：

既然 Java 也能用虚拟线程把阻塞式代码写得很轻量，那 Kotlin 协程还有什么优势？

这篇文章不空聊概念。我们用同一个场景做对比：

同时请求 WanAndroid 的两个接口：

- `banner/json`
- `article/list/0/json`

网络框架统一使用 Retrofit，不引入 RxJava，也不引入复杂响应式链路。

## 先看一个问题

假设首页打开时需要同时请求两个接口：

1. 顶部 banner
2. 首页文章列表

这两个接口没有先后依赖关系，最自然的做法就是并发请求，等两个结果都回来后再展示。

如果串行请求，大概是这样：

```text
请求 banner -> 等 banner 返回 -> 请求文章列表 -> 等文章列表返回
```

如果并发请求，应该是这样：

```text
请求 banner
请求文章列表
等待两个结果
```

这正是 Java 虚拟线程和 Kotlin 协程都能解决的问题。

区别不在于“谁能做到”，而在于：

- 代码怎么表达
- 生命周期怎么管理
- 异常怎么传播
- 在 Android 项目里怎么落地

## Retrofit 接口定义

先看 Java 版 Retrofit API：

```java
public interface WanAndroidJavaApi {
    @GET("banner/json")
    Call<ResponseBody> banner();

    @GET("article/list/0/json")
    Call<ResponseBody> homeArticles();
}
```

Java 版返回的是 Retrofit 的 `Call<ResponseBody>`。

再看 Kotlin 版：

```kotlin
interface WanAndroidKotlinApi {
    @GET("banner/json")
    suspend fun banner(): Response<ResponseBody>

    @GET("article/list/0/json")
    suspend fun homeArticles(): Response<ResponseBody>
}
```

Kotlin 版直接把接口声明成 `suspend fun`。

这一步已经能看出两种风格的差异：

- Java：请求对象是一个 `Call`，什么时候执行由调用方决定
- Kotlin：请求本身就是一个可挂起函数，调用点天然处在协程语义里

## Java 版：Retrofit + 虚拟线程

Java 版的核心代码是这样：

```java
private static void runConcurrent(WanAndroidJavaApi api) throws InterruptedException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        Callable<ApiResult> bannerTask = () -> execute("banner", api.banner());
        Callable<ApiResult> articlesTask = () -> execute("homeArticles", api.homeArticles());

        List<Future<ApiResult>> futures = executor.invokeAll(List.of(bannerTask, articlesTask));
        for (Future<ApiResult> future : futures) {
            printResult(future);
        }
    }
}
```

这里的思路非常直接：

- 创建一个虚拟线程 executor
- 每个接口请求包装成一个 `Callable`
- `invokeAll` 并发提交任务
- 通过 `Future.get()` 获取结果

请求执行部分仍然是阻塞式的：

```java
private static ApiResult execute(String name, Call<ResponseBody> call) {
    long start = System.currentTimeMillis();
    try {
        Response<ResponseBody> response = call.execute();
        ResponseBody body = response.body() != null ? response.body() : response.errorBody();
        String text = body != null ? body.string() : "";
        return new ApiResult(
                name,
                response.raw().request().url().toString(),
                response.code(),
                System.currentTimeMillis() - start,
                text,
                null
        );
    } catch (IOException e) {
        return new ApiResult(name, call.request().url().toString(), -1, 0, "", e);
    }
}
```

注意这里用了 `call.execute()`。

如果这是传统平台线程，大量阻塞请求会占用大量真实线程，线程成本会比较高。

但虚拟线程的价值就在这里：**它允许你继续写阻塞式代码，同时把线程成本降下来。**

所以 Java 版的优势很明显：

- 对 Java 开发者非常直观
- Retrofit `Call.execute()` 写法简单
- 不需要把代码改造成回调或响应式链
- 适合服务端 Java，尤其是 Spring Boot 这类场景

但它也有一些代价：

- 你仍然需要显式管理 executor
- 并发任务通过 `Callable`、`Future` 表达
- 异常要从 `ExecutionException` 里拆出来
- 在 Android 上不能依赖虚拟线程，因为 Android 当前并不是标准 Loom 运行环境

## Kotlin 版：Retrofit + 协程

Kotlin 版核心代码是这样：

```kotlin
suspend fun main() = coroutineScope {
    val api = createApi()
    val start = System.currentTimeMillis()

    val banner = async {
        execute("banner") { api.banner() }
    }
    val articles = async {
        execute("homeArticles") { api.homeArticles() }
    }

    listOf(banner.await(), articles.await()).forEach {
        println(it.toSummary())
    }

    println("Kotlin Retrofit + coroutines total cost: ${System.currentTimeMillis() - start} ms")
}
```

这段代码的阅读顺序也很直接：

- `coroutineScope` 创建一个结构化并发范围
- `async` 并发启动两个请求
- `await` 等待两个结果
- 最后统一输出

请求执行函数是这样：

```kotlin
private suspend fun execute(
    name: String,
    request: suspend () -> Response<ResponseBody>
): ApiResult {
    val start = System.currentTimeMillis()
    return try {
        val response = request()
        val body = response.body() ?: response.errorBody()
        ApiResult(
            name = name,
            url = response.raw().request.url.toString(),
            statusCode = response.code(),
            costMillis = System.currentTimeMillis() - start,
            body = body?.string().orEmpty(),
            error = null
        )
    } catch (e: Exception) {
        ApiResult(name, BASE_URL, -1, 0, "", e)
    }
}
```

这里最关键的是 Retrofit 接口本身：

```kotlin
suspend fun banner(): Response<ResponseBody>
```

调用 `api.banner()` 时，看起来像同步函数调用，但它其实是挂起函数。

请求等待期间，协程可以挂起，不需要一直占着当前线程。

这也是 Kotlin 协程在 Android 上特别自然的原因：

- ViewModel 里可以用 `viewModelScope`
- 页面销毁时协程可以自动取消
- Retrofit 原生支持 `suspend` 接口
- 不需要回调，不需要手动切一堆线程

## Java vs Kotlin：代码层面对比

Java 并发启动两个请求：

```java
Callable<ApiResult> bannerTask = () -> execute("banner", api.banner());
Callable<ApiResult> articlesTask = () -> execute("homeArticles", api.homeArticles());

List<Future<ApiResult>> futures = executor.invokeAll(List.of(bannerTask, articlesTask));
```

Kotlin 并发启动两个请求：

```kotlin
val banner = async {
    execute("banner") { api.banner() }
}
val articles = async {
    execute("homeArticles") { api.homeArticles() }
}
```

Java 的表达更偏“任务提交”：

- 构造 `Callable`
- 提交到 executor
- 拿到 `Future`
- 再取结果

Kotlin 的表达更偏“业务流程”：

- 启动 banner 请求
- 启动文章请求
- 等待两个结果

从可读性上看，Kotlin 更贴近业务描述。

但 Java 虚拟线程也不是差。它的优势是：**让传统阻塞式代码重新变得可扩展。**

## 结构化并发差异

Kotlin 协程的一个关键词是结构化并发。

```kotlin
coroutineScope {
    val banner = async { api.banner() }
    val articles = async { api.homeArticles() }

    banner.await()
    articles.await()
}
```

这意味着子任务属于这个 scope。

如果其中一个失败，默认会影响整个 scope，同级任务也会按规则取消。

Java 虚拟线程本身不等于结构化并发。

在 Java 里你可以用：

```java
private static void runWithLifecycle(WanAndroidJavaApi api) throws InterruptedException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        // submit tasks
    }
}
```

这样至少可以保证 executor 用完关闭。

但如果你想要更完整的结构化并发语义，Java 还需要使用 Structured Concurrency 相关 API。它和虚拟线程是相关的，但不是同一个东西。

简单说：

- 虚拟线程解决的是“线程便宜不便宜”
- 结构化并发解决的是“并发任务归谁管”
- Kotlin 协程把这两种体验更自然地合在了一起

## Android 项目里怎么选

如果是 Android 项目，我会更推荐 Kotlin 协程。

原因很现实：

1. Android 官方生态已经全面拥抱协程
2. Retrofit 对 `suspend` 支持非常成熟
3. ViewModel、Room、Paging、WorkManager 都能和协程自然配合
4. 页面生命周期和协程作用域可以绑定

典型 Android 写法可能是这样：

```kotlin
class HomeViewModel(
    private val api: WanAndroidKotlinApi
) : ViewModel() {

    fun loadHome() {
        viewModelScope.launch {
            val banner = async { api.banner() }
            val articles = async { api.homeArticles() }

            val bannerResult = banner.await()
            val articleResult = articles.await()

            // update ui state
        }
    }
}
```

这比在 Android 里手动维护线程池、Future、回调要舒服很多。

Java 虚拟线程更适合哪里？

更适合服务端 Java：

- Spring Boot
- 后台任务
- 网关服务
- 并发 IO 很多，但业务逻辑仍想保持阻塞式写法的系统

对于 Android 客户端，虚拟线程不是当前主流解法。

## 常见坑

### 1. 以为虚拟线程等于协程

不等于。

虚拟线程仍然是线程，只是更轻量。

协程是语言和库层面的并发抽象，它更强调挂起、取消、作用域和结构化并发。

### 2. 以为 Retrofit suspend 一定切到 IO 线程

Retrofit 的 `suspend` 调用会把异步请求适配成挂起函数，但不要把它简单理解成“自动帮你切 `Dispatchers.IO`”。

在 Android 项目里，通常你可以直接在 `viewModelScope.launch` 中调用 Retrofit 的 `suspend` 接口，因为 Retrofit 内部会走 OkHttp 的异步请求机制。

如果你自己在 `suspend` 函数里做的是阻塞 IO，那才需要认真考虑 `withContext(Dispatchers.IO)`。

### 3. 在 Kotlin 里把所有请求都包一层 `GlobalScope`

不要这样。

Android 里优先使用：

- `viewModelScope`
- `lifecycleScope`
- 自己明确管理的业务 scope

这样页面销毁、任务取消、异常传播才有边界。

### 4. Java 版忘记关闭 executor

虚拟线程便宜，但不是不要管理。

示例里使用：

```java
private static void runAndCloseExecutor(WanAndroidJavaApi api) throws InterruptedException {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        // submit tasks
    }
}
```

就是为了让 executor 生命周期更清晰。

## 最后总结

这次用同一个 WanAndroid + Retrofit 场景对比后，可以得出一个比较务实的结论。

Java 虚拟线程的核心价值是：

- 保留阻塞式写法
- 降低线程成本
- 让 Java 服务端并发 IO 代码更简单

Kotlin 协程的核心价值是：

- 用同步风格写异步逻辑
- 更自然地表达结构化并发
- 更好地处理取消、异常和生命周期
- 在 Android 生态里有非常成熟的落地方式

如果你写的是 Android：

**Retrofit + Kotlin 协程，基本就是默认答案。**

如果你写的是服务端 Java：

**Retrofit/HTTP Client + 虚拟线程，是非常值得关注的新方向。**

所以这不是“谁彻底赢了谁”的问题，而是：

> 在 Android 客户端，协程更贴近生态；在 Java 服务端，虚拟线程让阻塞式代码重新变得轻量。

能理解这个边界，比单纯记住几个 API 更重要。
