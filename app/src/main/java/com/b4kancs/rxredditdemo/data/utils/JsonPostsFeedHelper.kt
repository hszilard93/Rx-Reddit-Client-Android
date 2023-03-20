package com.b4kancs.rxredditdemo.data.utils

import com.b4kancs.rxredditdemo.data.networking.RedditJsonHelper
import com.b4kancs.rxredditdemo.data.networking.RedditJsonListingModel
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Single
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import retrofit2.HttpException
import retrofit2.Response

object JsonPostsFeedHelper {

    fun fromGetUsersPostsJsonCallToListOfPostsAsSingle(jsonRequest: Single<Response<RedditJsonListingModel>>): Single<List<Post>> {
        logcat { "fromGetUsersPostsJsonCallToListOfPostsAsSingle" }
        return jsonRequest
            .map { response ->
                if (response.isSuccessful)
                    response.body()!!.data.children
                else
                    throw HttpException(response)
            }
            .map { postsModels ->
                postsModels
                    .map { fromJsonPostDataModelToPost(it.data) }
                    .filter { it.links != null }
            }
    }

    fun fromJsonPostDataModelToPost(dataModel: RedditJsonListingModel.RedditPostDataModel): Post {  // TODO Maybe
        logcat { "fromJsonPostDataModel" }

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
                    val jsonHelper: RedditJsonHelper by inject(RedditJsonHelper::class.java)
                    val idTypePairs = jsonHelper.getPictureIdTypePairsFromGalleryPostAtUrl(galleryPostUrl)
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
                this.numOfComments
            )
        }
    }
}