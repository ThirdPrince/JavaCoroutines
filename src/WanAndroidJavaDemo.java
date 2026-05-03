import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class WanAndroidJavaDemo {
    private static final String BASE_URL = "https://www.wanandroid.com/";

    public static void main(String[] args) throws Exception {


        // 主线程循环打印日志，观察它和虚拟线程是并行发生的
        Thread monitor = Thread.ofPlatform().name("main-monitor").start(() -> {
            for (int i = 1; i <= 200; i++) {
                System.out.println("[monitor] i=" + i
                        + ", thread=" + Thread.currentThread().getName()
                        + ", isVirtual=" + Thread.currentThread().isVirtual());
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        });
        WanAndroidJavaApi api = createApi();
        long start = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<ApiResult> bannerTask = () -> execute("banner", api.banner());
            Callable<ApiResult> articlesTask = () -> execute("homeArticles", api.homeArticles());
            Thread.sleep(3000);
            List<Future<ApiResult>> futures = executor.invokeAll(List.of(bannerTask, articlesTask));
            for (Future<ApiResult> future : futures) {
                System.out.println("future ="+Thread.currentThread().getName());
                printResult(future);
            }
        }
        System.out.println("result ="+Thread.currentThread().getName());
        System.out.println("Java Retrofit + virtual threads total cost: "
                + (System.currentTimeMillis() - start) + " ms");
        monitor.join();
    }

    private static WanAndroidJavaApi createApi() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .build()
                .create(WanAndroidJavaApi.class);
    }

    private static void printResult(Future<ApiResult> future) throws InterruptedException {
        try {
            System.out.println(future.get().toSummary());
        } catch (ExecutionException e) {
            System.err.println("request failed: " + e.getCause());
        }
    }

    private static ApiResult execute(String name, Call<ResponseBody> call) {
        long start = System.currentTimeMillis();
        System.out.println("execute isVirtual ="+Thread.currentThread().isVirtual());
        try {
            Response<ResponseBody> response = call.execute();
            ResponseBody body = response.body() != null ? response.body() : response.errorBody();
            String text = body != null ? body.string() : "";
            return new ApiResult(name, response.raw().request().url().toString(),
                    response.code(), System.currentTimeMillis() - start, text, null);
        } catch (IOException e) {
            return new ApiResult(name, call.request().url().toString(), -1, 0, "", e);
        }
    }

    private record ApiResult(String name, String url, int statusCode, long costMillis, String body, Exception error) {
        String toSummary() {
            if (error != null) {
                return """
                        [%s]
                        url: %s
                        failed: %s
                        """.formatted(name, url, error);
            }

            return """
                    [%s]
                    url: %s
                    status: %d
                    cost: %d ms
                    body size: %d chars
                    note: %s
                    preview: %s
                    """.formatted(name, url, statusCode, costMillis, body.length(), note(statusCode), preview(body));
        }

        private static String note(int statusCode) {
            return statusCode >= 200 && statusCode < 300 ? "ok" : "remote returned error status";
        }

        private static String preview(String value) {
            String normalized = value.replaceAll("\\s+", " ").trim();
            if (normalized.length() <= 160) {
                return normalized;
            }
            return normalized.substring(0, 160) + "...";
        }
    }
}
