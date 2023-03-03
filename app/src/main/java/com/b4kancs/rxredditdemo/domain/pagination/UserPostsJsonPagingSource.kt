package com.b4kancs.rxredditdemo.domain.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonDataModelToPostTransformer.fromJsonPostDataModel
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import retrofit2.HttpException

class UserPostsJsonPagingSource(private val userFeed: UserFeed) : RxPagingSource<String, Post>() {

    companion object {
        const val PAGE_SIZE = 50
    }

    private val service: RedditJsonService by inject(RedditJsonService::class.java)

    init {
        logcat { "init username = $userFeed" }
    }

    override fun loadSingle(params: LoadParams<String>): Single<LoadResult<String, Post>> {
        logcat { "loadSingle" }

        // Load the aggregate feed OR the subscriptions feed.
        if (userFeed in setOf(FollowsRepository.aggregateUserFeed, FollowsRepository.subscriptionsUserFeed)) {
            // Inject a different CombinedFeedLoader single depending on whether we need an Aggregate or Subscriptions type loader.
            val combinedFeedLoaderType =
                if (userFeed == FollowsRepository.aggregateUserFeed) AggregateFeedLoader::class.java
                else SubscriptionsFeedLoader::class.java
            val aggregateFeedLoader: AbstractCombinedFeedLoader by inject(combinedFeedLoaderType)

            return aggregateFeedLoader.loadCombinedFeeds(params.loadSize, params.key)
        }

        // Load regular user feeds.
        return service.getUsersPostsJson(
            userFeed.name,
            params.loadSize,
            params.key
        )
            .subscribeOn(Schedulers.io())
            .map { response ->
                if (response.isSuccessful)
                    response.body()!!.data.children
                else
                    throw HttpException(response)
            }
            .map { postsModels ->
                postsModels
                    .map { fromJsonPostDataModel(it.data) }
                    .filter { it.links != null }        // The 'links' of all posts that are not picture or gallery posts is null
            }
            // Not specifying the Type here causes a 'type mismatch' error that made me run in circles for a while..
            .map<LoadResult<String, Post>> { posts ->
                LoadResult.Page(
                    data = posts,
                    prevKey = null,
                    nextKey = if (posts.isNotEmpty()) posts.last().name else null
                )
            }
            .onErrorReturn { e ->
                logcat(LogPriority.WARN) { "Exception caught: ${e.message}" }
                LoadResult.Error(e)
            }
    }

    override fun getRefreshKey(state: PagingState<String, Post>): String? {
        return null
    }
}