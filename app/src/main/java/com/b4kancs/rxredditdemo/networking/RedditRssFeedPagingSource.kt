package com.b4kancs.rxredditdemo.networking

import android.util.Log
import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.Subreddit
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.java.KoinJavaComponent.inject

class RedditRssFeedPagingSource(val subreddit: String) : RxPagingSource<String, Post>() {

    companion object {
        const val LOG_TAG = "RedditRssPagingSource"
        const val FEED_URL = "https://www.reddit.com"
        const val PAGE_SIZE = 15
        const val defaultSubredditPreferenceKey = "default_subreddit"
        var defaultSubreddit = Subreddit( "SFWPornNetwork", "user/kjoneslol/m/sfwpornnetwork")

        private val service: RedditRssService by inject(RedditRssService::class.java)

        fun getPictureIdsFromGalleryPostAtUrl(url: String): Maybe<List<String>> {
            return service
                .getGalleryJson("$url/.json")
                .subscribeOn(Schedulers.io())
                .map { response ->
                    if (!response.isSuccessful) {
                        Log.e(LOG_TAG, "Error getting gallery items for $url. Error: ${response.code()}")
                        return@map emptyList()
                    }
                    response.body()!!
                        .first().data.children.first().data.galleryData.items
                }
                .flatMapMaybe { items ->
                    val ids = ArrayList<String>()
                    if (items.isEmpty()) {
                        Maybe.empty()
                    } else {
                        items.forEach { ids.add(it.mediaId) }
                        Maybe.just(ids)
                    }
                }
        }
    }

    override fun loadSingle(params: LoadParams<String>): Single<LoadResult<String, Post>> {
        return service.getSubredditJson(
            subreddit,
            params.loadSize,
            params.key
        )
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