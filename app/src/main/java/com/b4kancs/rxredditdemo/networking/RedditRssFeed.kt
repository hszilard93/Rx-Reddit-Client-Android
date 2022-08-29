package com.b4kancs.rxredditdemo.networking

import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.java.KoinJavaComponent.inject

object RedditRssFeed {
    const val FEED_URL = "https://www.reddit.com"
    const val PAGE_SIZE = 25
    private val service: RedditRssService by inject(RedditRssService::class.java)

    fun getPostsOnSub(subreddit: String, after: String? = null): Single<List<Post>> {
        return service.getSubredditJson(subreddit, 25, after)
            .map { response -> response.body()!!.data.children }
            .map { posts ->
                posts
                    .map { Post.from(it.data) }
                    .filter { it.links != null }        // The links of all posts that are not picture or gallery posts is null
            }
            .subscribeOn(Schedulers.io())
    }

    fun getPictureIdsFromGalleryPostAtUrl(url: String): Single<List<String>> {
        return service
            .getGalleryJson("$url/.json")
            .map { response ->
                println(response.body())
                response.body()!!
                    .first().data.children.first().data.galleryData.items
            }
            .map { items ->
                val ids = ArrayList<String>()
                items.forEach { ids.add(it.mediaId!!) }
                ids
            }
    }
}