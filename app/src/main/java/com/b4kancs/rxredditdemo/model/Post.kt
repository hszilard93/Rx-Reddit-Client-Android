import com.b4kancs.rxredditdemo.networking.RedditRssFeed
import com.b4kancs.rxredditdemo.networking.RedditRssListingModel.RedditPostDataModel

data class Post(
    val author: String,
    val title: String,
    val subreddit: String,
    val url: String,
    val links: List<String>?,
    val permalink: String,
    val domain: String,
    val score: Int,
    val createdAt: Int,
    val nsfw: Boolean,
    val numOfComments: Int
) {
    companion object {

        fun from(dataModel: RedditPostDataModel): Post {
            dataModel.also {
                val glideSupportedFileTypesPattern = """^.*\.(gif|jpg|jpeg|raw|png|webp)${'$'}""".toRegex()
                val galleryPattern = """^https://www.reddit.com/gallery/(.+)$""".toRegex()

                val links: List<String>? =
                    if (glideSupportedFileTypesPattern.matches(it.url)) {
                        listOf(it.url)
                    }
                    else if(galleryPattern.matches(it.url)) {
                        val id = galleryPattern.find(it.url)!!.groupValues[1]
                        val url = "https://www.reddit.com/r/pics/comments/$id"
                        val ids = RedditRssFeed.getPictureIdsFromGalleryPostAtUrl(url).blockingGet()
                        ids.map { id -> "https://i.redd.it/$id.jpg" }
                    }
                    else {
                        null
                    }

                return Post(
                    it.author,
                    it.title,
                    it.subreddit,
                    it.url,
                    links,
                    it.permalink,
                    it.domain,
                    it.score,
                    it.createdAt,
                    it.nsfw,
                    it.numOfComments
                )
            }
        }
    }
}