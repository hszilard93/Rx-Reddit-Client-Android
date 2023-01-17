package com.b4kancs.rxredditdemo.domain.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonDataModelToPostTransformer.fromJsonPostDataModel
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import retrofit2.HttpException

class UserPostsJsonPagingSource(private val userFeed: UserFeed) : RxPagingSource<String, Post>() {

    companion object {
        const val PAGE_SIZE = 50
    }

    object NoContentException : Exception()

    private val service: RedditJsonService by inject(RedditJsonService::class.java)
    private val followsRepository: FollowsRepository by inject(FollowsRepository::class.java)

    init {
        logcat { "init username = $userFeed" }
    }

    override fun loadSingle(params: LoadParams<String>): Single<LoadResult<String, Post>> {
        logcat { "loadSingle" }

        // If we aren't following any users, return an empty result.
//        if (userFeed == null) return Single.just(LoadResult.Error(NoContentException))

        if (userFeed == FollowsRepository.defaultUserFeed) {
            val aggregateFeedLoader: AggregateFeedLoader by inject(AggregateFeedLoader::class.java)
            return aggregateFeedLoader.loadAggregateFeeds(params.loadSize, params.key)
        }

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