package com.b4kancs.rxredditdemo.data.utils

import com.b4kancs.rxredditdemo.data.networking.RedditJsonClient
import com.b4kancs.rxredditdemo.data.networking.RedditJsonListingModel
import com.b4kancs.rxredditdemo.model.Post
import logcat.logcat

object JsonDataModelToPostTransformer {

    fun fromJsonPostDataModel(dataModel: RedditJsonListingModel.RedditPostDataModel): Post {
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
                        }
                        else {
                            // It's a crosspost gallery post (°〇°)ﾉ
                            logcat { "Parsing crosspost gallery post  $url" }

                            crosspostParents.first().subreddit
                        }
                    val galleryPostUrl = "https://www.reddit.com/r/$sub/comments/$galleryId"
                    logcat { "Attempting to get links to gallery items on post $name $title; gallery url $url; request url $galleryPostUrl." }
                    val idTypePairs = RedditJsonClient.getPictureIdTypePairsFromGalleryPostAtUrl(galleryPostUrl)
                        .blockingGet()
                    idTypePairs?.map { (imageId, imageType) ->
                        val type = imageType.split("/").last()  // This data is in the format of "image/png", for example.
                        "https://i.redd.it/$imageId.$type"
                    }
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
                this.numOfComments,
                toBlur = this.nsfw
            )
        }
    }
}