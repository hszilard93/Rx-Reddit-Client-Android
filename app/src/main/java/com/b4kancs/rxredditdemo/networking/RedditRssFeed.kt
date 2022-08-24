import com.b4kancs.rxredditdemo.networking.RedditRssService
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

object RedditRssFeed {
    const val FEED_URL = "https://www.reddit.com"
    private val service = RedditRssService.create()

    fun getPostsOnSub(subreddit: String): Single<List<Post>> {
        return service.getSubredditJson(subreddit)
            .map { response -> response.body()!!.data.children }
            .map { posts -> posts.map { Post.from(it.data) } }
            .subscribeOn(Schedulers.io())
    }
}