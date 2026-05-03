import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface WanAndroidJavaApi {
    @GET("banner/json")
    Call<ResponseBody> banner();

    @GET("article/list/0/json")
    Call<ResponseBody> homeArticles();
}
