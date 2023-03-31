package com.b4kancs.rxredditdemo.repository.pagination

import android.annotation.SuppressLint
import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.data.database.loadFromNetwork
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.utils.JsonPostsFeedHelper
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

class FavoritesPagingSource(
    private val favoritePostsRepository: FavoritePostsRepository,
    private val jsonService: RedditJsonService
) : RxPagingSource<Int, Post>() {

    companion object {
        const val PAGE_SIZE = 5

        // A Pager always requires a new instance of PagingSource, so we'll keep this nice cache 'o posts here in the companion object.
        private val cachedPostsMap: ConcurrentHashMap<String, Post> by lazy { ConcurrentHashMap<String, Post>() }
    }

    private val disposables = CompositeDisposable()

    @SuppressLint("CheckResult")
    override fun loadSingle(params: LoadParams<Int>): Single<LoadResult<Int, Post>> {
        val offset = params.key ?: 0
        logcat(LogPriority.INFO) { "loadSingle: offset = $offset" }

        return Single.create { resultEmitter ->
            favoritePostsRepository.getFavoritesFromDbLimitedAndOffset(PAGE_SIZE, offset)
                .map { dbEntries ->
                    // The DB entries contain only minimal data. We need to download the full post from the network.
                    logcat { "DbEntries = ${dbEntries.map { it.name }}" }
                    dbEntries
                        .parallelStream() // Parallelizing the processing.
                        .map(::processDbEntryIntoPostMaybe)
                        .toList()
                }
                .flatMap(::processMaybesIntoPosts)
                .subscribeBy(
                    onSuccess = { posts ->
                        resultEmitter.onSuccess(
                            LoadResult.Page(
                                data = posts
                                    .filter {
                                        if (it.links == null) {
                                            logcat { "Post ${it.name} filtered out in PagingSource as no longer valid." }
                                            false
                                        }
                                        else true
                                    },
                                prevKey = null,
                                nextKey = if (posts.isNotEmpty()) offset + posts.size else null
                            )
                        )
                    },
                    onError = { e ->
                        resultEmitter.onSuccess(
                            LoadResult.Error(e)
                        )
                    }
                ).addTo(disposables)
        }
    }

    // Attempts to download the post's data from the network based on the DB entry if not already in the cache.
    // Returns a Maybe that either succeeds with a post, completes in case the post is no longer available, or errors out (network error).
    private fun processDbEntryIntoPostMaybe(dbEntry: PostFavoritesDbEntry): Maybe<Post> {
        logcat { "dbEntryToMaybe: dbEntry = $dbEntry" }
        return Maybe.create { emitter ->
            if (!cachedPostsMap.containsKey(dbEntry.name)) {
                logcat { "Loading post data ${dbEntry.name} from network." }
                dbEntry.loadFromNetwork(jsonService)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.computation())
                    .retry(5)
                    .subscribeBy(
                        onSuccess = { response ->
                            if (response.isSuccessful) {
                                val jsonModel = response.body()!!
                                val post = JsonPostsFeedHelper
                                    .fromJsonPostDataModelToPost(jsonModel.first().data.children.first().data)
                                cachedPostsMap[dbEntry.name] = post
                                emitter.onSuccess(post)
                            }
                            else {
                                // The post might have been deleted etc. We will later disregard it.
                                logcat { "Favorite post ${dbEntry.name} cannot be downloaded. Might not exist anymore." }
                                emitter.onComplete()
                            }
                        },
                        onError = { e ->
                            logcat(LogPriority.ERROR) { "A network error occurred. Message = ${e.message}" }
                            emitter.onError(e)
                        }
                    ).addTo(disposables)
            }
            else {
                logcat { "Loading post data ${dbEntry.name} from cache." }
                emitter.onSuccess(cachedPostsMap[dbEntry.name]!!)
            }
        }
    }


    private fun processMaybesIntoPosts(maybes: List<Maybe<Post>>): Single<List<Post>> {
        return Single.create { emitter ->
            val maybesRunning = Collections.newSetFromMap(ConcurrentHashMap<Maybe<Post>, Boolean>()).also {
                it.addAll(maybes)
            }
            val maybeFinishedSubject = PublishSubject.create<Maybe<Post>>()
            val resultList = Collections.synchronizedList(java.util.ArrayList<Post>())

            maybeFinishedSubject
                .subscribeBy(
                    onNext = { finishedMaybe ->
                        maybesRunning.remove(finishedMaybe)
                        if (maybesRunning.isEmpty())
                            emitter.onSuccess(resultList)
                    }
                )
                .addTo(disposables)

            for (maybe in maybes) {
                maybe
                    .subscribeBy(
                        onSuccess = { post ->
                            if (post.links != null) { // The post's content could have been deleted.
                                resultList.add(post)
                            }
                            maybeFinishedSubject.onNext(maybe)
                        },
                        onComplete = {
                            logcat(LogPriority.WARN) { "Ignoring post." }
                            maybeFinishedSubject.onNext(maybe)
                        },
                        onError = { e ->
                            // If we get a single network error, we will shut down the download and display an error message.
                            // Might want to handle this more elegantly in the future.
                            emitter.tryOnError(e)
                        })
                    .addTo(disposables)
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Post>): Int? {
        return null
    }
}