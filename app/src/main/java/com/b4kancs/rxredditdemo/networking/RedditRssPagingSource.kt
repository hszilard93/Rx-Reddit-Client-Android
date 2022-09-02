package com.b4kancs.rxredditdemo.networking

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.java.KoinJavaComponent.inject

class RedditRssPagingSource(val subreddit: String) : RxPagingSource<String, Post>() {

    companion object {
        private val service: RedditRssService by inject(RedditRssService::class.java)
        const val FEED_URL = "https://www.reddit.com"
        const val PAGE_SIZE = 15
        const val DEFAULT_SUBREDDIT = "pics"

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
                    items.forEach { ids.add(it.mediaId) }
                    ids
                }
        }
    }

    override fun loadSingle(params: LoadParams<String>): Single<LoadResult<String, Post>> {
        return service.getSubredditJson(
            subreddit,
            params.loadSize,
            params.key
        )
//            .subscribeOn(Schedulers.io())
            .map { response -> response.body()!!.data.children }
            .map { posts ->
                posts
                    .map { Post.from(it.data) }
                    .filter { it.links != null }        // The links of all posts that are not picture or gallery posts is null
            }
            .map { posts ->
                LoadResult.Page(
                    data = posts,
                    prevKey = null,
                    nextKey = posts.last().name,
                )
            }
    }

    override fun getRefreshKey(state: PagingState<String, Post>): String? {
        return null
    }
}
//
//object RedditRssFeed {
//    const val FEED_URL = "https://www.reddit.com"
//    private const val PAGE_SIZE = 5
//    private val service: RedditRssService by inject(RedditRssService::class.java)
//
//    fun getPostsOnSub(subreddit: String, after: String? = null): Single<List<Post>> {
//        return service.getSubredditJson(subreddit, PAGE_SIZE, after)
//            .map { response -> response.body()!!.data.children }
//            .map { posts ->
//                posts
//                    .map { Post.from(it.data) }
//                    .filter { it.links != null }        // The links of all posts that are not picture or gallery posts is null
//            }
//            .subscribeOn(Schedulers.io())
//    }
//
//}