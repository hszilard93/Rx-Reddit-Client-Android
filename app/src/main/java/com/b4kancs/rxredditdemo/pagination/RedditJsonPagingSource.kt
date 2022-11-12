package com.b4kancs.rxredditdemo.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditPostListingModel
import com.b4kancs.rxredditdemo.networking.RedditJsonService
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class RedditJsonPagingSource(val subreddit: String) : RxPagingSource<String, Post>() {

    companion object RedditJsonClient {
        const val FEED_URL = "https://www.reddit.com"
        const val PAGE_SIZE = 50

        private val service: RedditJsonService by inject(RedditJsonService::class.java)

        fun getPictureIdsFromGalleryPostAtUrl(url: String): Maybe<List<String>> {
            return service
                .getGalleryJson("$url/.json")
                .subscribeOn(Schedulers.io())
                .map { response ->
                    if (!response.isSuccessful) {
                        logcat(LogPriority.ERROR) { "Error getting gallery items for $url. Error: ${response.code()}" }
                        return@map emptyList<RedditPostListingModel.RedditPostDataChildDataGalleryDataItem>()
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

        fun getSubredditsByKeyword(keyword: String): Single<List<Subreddit>> {
            return service.searchSubredditsByKeyword(keyword)
                .map { response -> response.body()!! }
                .map { subsModel ->
                    subsModel.data.children
                }
                .map { listOfSubData ->
                    val subreddits = ArrayList<Subreddit>()
                    listOfSubData.forEach {
                        subreddits.add(Subreddit.fromSubredditJsonModel(it.data))
                    }
                    subreddits
                }
                .flatMap { Single.just(it.toList()) }
                .onErrorReturn {
                    logcat(LogPriority.ERROR) { it.message.toString() }
                    emptyList()
                }
        }
    }

    // Load the posts of a given subreddit into a PagingSource.LoadResult
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