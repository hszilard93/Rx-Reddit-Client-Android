package com.b4kancs.rxredditdemo.domain.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonDataModelToPostTransformer.fromJsonPostDataModel
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import io.reactivex.rxjava3.core.Single
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

// For now, the same as SubredditJsonPagingSource, just doing a different request. Still, I think it deserves its own class for now.
class UserPostsJsonPagingSource(private val userFeed: UserFeed?) : RxPagingSource<String, Post>() {

    companion object {
        const val PAGE_SIZE = 50
    }

    private val service: RedditJsonService by inject(RedditJsonService::class.java)

    init {
        logcat { "init username = $userFeed" }
    }

    // Load the posts of a given subreddit into a PagingSource.LoadResult
    override fun loadSingle(params: LoadParams<String>): Single<LoadResult<String, Post>> {
        logcat { "loadSingle" }

        // If we aren't following a user, return an empty result.
        if (userFeed == null) return Single.just(LoadResult.Page(emptyList(), null, null))

        if (userFeed == FollowsRepository.defaultUserFeed) {
            // TODO
            return Single.just(LoadResult.Page(emptyList(), null, null))
        }

        return service.getUsersPostsJson(
            userFeed.name,
            params.loadSize,
            params.key
        )
            .map { response -> response.body()!!.data.children }
            .map { posts ->
                posts
                    .map { fromJsonPostDataModel(it.data) }
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