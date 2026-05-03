import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

interface WanAndroidKotlinApi {
    @GET("banner/json")
    suspend fun banner(): Response<ResponseBody>

    @GET("article/list/0/json")
    suspend fun homeArticles(): Response<ResponseBody>
}
