import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://www.wanandroid.com/"

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

private fun createApi(): WanAndroidKotlinApi {
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    return Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .build()
        .create(WanAndroidKotlinApi::class.java)
}

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
            url = body.toString(),
            statusCode = response.code(),
            costMillis = System.currentTimeMillis() - start,
            body = body?.string().orEmpty(),
            error = null
        )
    } catch (e: Exception) {
        ApiResult(name, BASE_URL, -1, 0, "", e)
    }
}

private data class ApiResult(
    val name: String,
    val url: String,
    val statusCode: Int,
    val costMillis: Long,
    val body: String,
    val error: Exception?
) {
    fun toSummary(): String {
        if (error != null) {
            return """
                [$name]
                url: $url
                failed: $error
            """.trimIndent()
        }

        return """
            [$name]
            url: $url
            status: $statusCode
            cost: $costMillis ms
            body size: ${body.length} chars
            note: ${statusCode.note()}
            preview: ${body.preview()}
        """.trimIndent()
    }

    private fun String.preview(): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= 160) normalized else normalized.take(160) + "..."
    }

    private fun Int.note(): String =
        if (this in 200..299) "ok" else "remote returned error status"
}
