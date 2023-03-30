package com.b4kancs.rxredditdemo.domain.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonPostsFeedHelper.fromGetUsersPostsJsonCallToListOfPostsAsSingle
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class FollowsPagingSource(
    private val userFeed: UserFeed,
    private val jsonService: RedditJsonService,
    private val followsRepository: FollowsRepository
) : RxPagingSource<String, Post>() {

    companion object {
        const val PAGE_SIZE = 50
    }

    init {
        logcat { "init username = $userFeed" }
    }

    override fun loadSingle(params: LoadParams<String>): Single<LoadResult<String, Post>> {
        logcat { "loadSingle: params.loadSize = ${params.loadSize}, params.key = ${params.key}" }

        // Load the aggregate feed OR the subscriptions feed.
        if (userFeed in setOf(FollowsRepository.aggregateUserFeed, FollowsRepository.subscriptionsUserFeed)) {
            // Inject a different CombinedFeedLoader single depending on whether we need an Aggregate or Subscriptions type loader.
            val combinedFeedLoaderType =
                if (userFeed == FollowsRepository.aggregateUserFeed) AggregateFeedLoader::class.java
                else SubscriptionsFeedLoader::class.java
            // This type of dynamic field injection could be replaced by constructor injection (by an instance of both types) later if it
            // poses an issue.
            val aggregateFeedLoader: AbstractCombinedFeedLoader by inject(combinedFeedLoaderType)

            return aggregateFeedLoader.loadCombinedFeed(params.loadSize, params.key)
        }

        // Load regular user feeds.
        val request = jsonService.getUsersPostsJson(
            userFeed.name,
            params.loadSize,
            params.key
        )
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())

        return fromGetUsersPostsJsonCallToListOfPostsAsSingle(request)
            // Not specifying the Type here causes a 'type mismatch' error that made me run in circles for a while..
            .map<LoadResult<String, Post>> { posts ->
                if (params.key == null && posts.isNotEmpty()) {
                    // If this is the first page, we update the user's last post in the DB.
                    followsRepository.updateUsersLatestPost(userFeed.name, posts.first().name).blockingSubscribe()
                }

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