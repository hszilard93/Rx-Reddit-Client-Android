package com.b4kancs.rxredditdemo.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.networking.RedditJsonService
import io.reactivex.rxjava3.core.Single
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class RedditJsonPagingSource(val subredditAddress: String) : RxPagingSource<String, Post>() {

    companion object {
        const val FEED_URL = "https://www.reddit.com"
        const val PAGE_SIZE = 50
    }

    private val service: RedditJsonService by inject(RedditJsonService::class.java)

    init {
        logcat { "init subreddit = $subredditAddress" }
    }

    // Load the posts of a given subreddit into a PagingSource.LoadResult
    override fun loadSingle(params: LoadParams<String>): Single<LoadResult<String, Post>> {
        logcat { "loadSingle" }
        return service.getSubredditJson(
            subredditAddress,
            params.loadSize,
            params.key
        )
            .map { response -> response.body()!!.data.children }
            .map { posts ->
                posts
                    .map { Post.from(it.data) }
                    .filter { it.links != null }        // The 'links' of all posts that are not picture or gallery posts is null
            }
            .map { posts ->
                LoadResult.Page(
                    data = posts,
                    prevKey = null,
                    nextKey = if (posts.isNotEmpty()) posts.last().name else null
                )
            }
    }

    override fun getRefreshKey(state: PagingState<String, Post>): String? {
        return null
    }
}