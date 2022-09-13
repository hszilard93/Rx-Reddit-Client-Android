package com.b4kancs.rxredditdemo.model

import android.util.Log
import com.b4kancs.rxredditdemo.networking.RedditJsonListingModel.RedditPostDataModel
import com.b4kancs.rxredditdemo.networking.RedditJsonPagingSource

data class Post(
    val name: String,
    val author: String,
    val title: String,
    val subreddit: String,
    val url: String,
    val links: List<String>?,
    val permalink: String,
    val domain: String,
    val crossPostFrom: String?,
    val score: Int,
    val createdAt: Int,
    val nsfw: Boolean,
    val numOfComments: Int
) {
    companion object {

        private const val LOG_TAG = "Post"

        fun from(dataModel: RedditPostDataModel): Post {
            with(dataModel) {
                val glideSupportedFileTypesPattern = """^.*\.(gif|jpg|jpeg|raw|png|webp)${'$'}""".toRegex()
                val galleryPattern = """^https://www.reddit.com/gallery/(.+)$""".toRegex()

                val links: List<String>? =
                    // It's either a supported media type link
                    if (glideSupportedFileTypesPattern.matches(url)) {
                        listOf(url)
                    }
                    // Or a gallery post
                    else if (galleryPattern.matches(url)) {
                        val galleryId = galleryPattern.find(url)!!.groupValues[1]
                        val sub =
                            if (crosspostParents == null) {
                                subreddit
                            } else {
                                // It's a crosspost gallery post (°〇°)ﾉ
                                Log.d(LOG_TAG, "Parsing crosspost gallery post  $url")

                                crosspostParents.first().subreddit
                            }
                        val galleryPostUrl = "https://www.reddit.com/r/$sub/comments/$galleryId"
                        Log.d(
                            LOG_TAG,
                            "Attempting to get links to gallery items on post $name $title; gallery url $url; request url $galleryPostUrl."
                        )
                        val ids: List<String>? = RedditJsonPagingSource.getPictureIdsFromGalleryPostAtUrl(galleryPostUrl)
                            .blockingGet()
                        ids?.map { imageId -> "https://i.redd.it/$imageId.jpg" }
                    }
                    // Or not a supported link
                    else {
                        null
                    }

                return Post(
                    this.name,
                    this.author,
                    this.title,
                    this.subreddit,
                    this.url,
                    links,
                    this.permalink,
                    this.domain,
                    this.crosspostParents?.first()?.subreddit,
                    this.score,
                    this.createdAt,
                    this.nsfw,
                    this.numOfComments
                )
            }
        }
    }
}