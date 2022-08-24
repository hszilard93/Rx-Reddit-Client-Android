import com.b4kancs.rxredditdemo.networking.RedditRssListingModel
import io.reactivex.rxjava3.core.Single
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface RedditRssService {

    companion object {
        fun create(): RedditRssService {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(RedditRssFeed.FEED_URL)
                .client(client)
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(RedditRssService::class.java)
        }
    }

    @GET("/r/{subreddit}/.json")
    fun getSubredditJson(@Path("subreddit") subreddit: String): Single<Response<RedditRssListingModel>>
//    fun getSubredditJson(@Path("subreddit")subreddit: String): Single<Response<String>>
}