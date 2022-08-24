import com.b4kancs.rxredditdemo.networking.RedditPostDataModel
import com.google.gson.annotations.SerializedName

data class Post(
    val author: String,
    val title: String,
    val subreddit: String,
    val link: String,
    val permalink: String,
    val domain: String,
    val numOfComments: Int
) {
    companion object {
        fun from(dataModel: RedditPostDataModel): Post {
            dataModel.apply {
                return Post(
                    author,
                    title,
                    subreddit,
                    link,
                    permalink,
                    domain,
                    numOfComments
                )
            }
        }
    }
}