package com.b4kancs.rxredditdemo.model

import com.b4kancs.rxredditdemo.networking.RedditRssListingModel.RedditPostDataModel
import com.b4kancs.rxredditdemo.networking.RedditRssPagingSource
import org.koin.java.KoinJavaComponent.inject

data class Post(
    val name: String,
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
            dataModel.also { postModel ->
                val glideSupportedFileTypesPattern = """^.*\.(gif|jpg|jpeg|raw|png|webp)${'$'}""".toRegex()
                val galleryPattern = """^https://www.reddit.com/gallery/(.+)$""".toRegex()

                val links: List<String>? =
                    if (glideSupportedFileTypesPattern.matches(postModel.url)) {
                        listOf(postModel.url)
                    }
                    else if(galleryPattern.matches(postModel.url)) {
                        val galleryId = galleryPattern.find(postModel.url)!!.groupValues[1]
                        val galleryPostUrl = "https://www.reddit.com/r/pics/comments/$galleryId"
                        val ids = RedditRssPagingSource.getPictureIdsFromGalleryPostAtUrl(galleryPostUrl).blockingGet()
                        ids.map { imageId -> "https://i.redd.it/$imageId.jpg" }
                    }
                    else {
                        null
                    }

                return Post(
                    postModel.name,
                    postModel.author,
                    postModel.title,
                    postModel.subreddit,
                    postModel.url,
                    links,
                    postModel.permalink,
                    postModel.domain,
                    postModel.score,
                    postModel.createdAt,
                    postModel.nsfw,
                    postModel.numOfComments
                )
            }
        }
    }
}