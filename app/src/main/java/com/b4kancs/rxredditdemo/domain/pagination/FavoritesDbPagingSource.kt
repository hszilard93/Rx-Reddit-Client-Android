package com.b4kancs.rxredditdemo.domain.pagination

import android.annotation.SuppressLint
import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.data.database.loadFromNetwork
import com.b4kancs.rxredditdemo.data.utils.JsonDataModelToPostTransformer
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
import org.koin.java.KoinJavaComponent.inject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

class FavoritesDbPagingSource : RxPagingSource<Int, Post>() {

    companion object {
        const val PAGE_SIZE = 5

        // A Pager always requires a new instance of PagingSource, so we'll keep this useful cache here in the companion object.
        private val cachedPostsMap: ConcurrentHashMap<String, Post> by lazy { ConcurrentHashMap<String, Post>() }
    }

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    private val disposables = CompositeDisposable()

    @SuppressLint("CheckResult")
    override fun loadSingle(params: LoadParams<Int>): Single<LoadResult<Int, Post>> {
        val offset = params.key ?: 0
        logcat(LogPriority.INFO) { "loadSingle: offset = $offset" }

        return Single.create { resultEmitter ->
            favoritePostsRepository.getFavoritesFromDbLimitedAndOffset(PAGE_SIZE, offset)
                .map { dbEntries ->
                    logcat { "DbEntries = ${dbEntries.map { it.name }}" }
                    dbEntries
                        .parallelStream()       // Loading posts' data from the network in sequence takes too much time.
                        .map { dbEntry ->
                            Maybe.create<Post> { emitter ->
                                if (!cachedPostsMap.containsKey(dbEntry.name)) {
                                    logcat { "Loading post data ${dbEntry.name} from network." }
                                    dbEntry.loadFromNetwork()
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(Schedulers.computation())
                                        .retry(5)
                                        .subscribeBy(
                                            onSuccess = { response ->
                                                if (response.isSuccessful) {
                                                    val jsonModel = response.body()!!
                                                    val post = JsonDataModelToPostTransformer
                                                        .fromJsonPostDataModel(jsonModel.first().data.children.first().data)
                                                    cachedPostsMap[dbEntry.name] = post
                                                    emitter.onSuccess(post)
                                                } else {
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
                                } else {
                                    logcat { "Loading post data ${dbEntry.name} from cache." }
                                    emitter.onSuccess(cachedPostsMap[dbEntry.name]!!)
                                }
                            }
                        }
                        .toList()
                }
                .flatMap { maybes ->
                    Single.create<List<Post>> { emitter ->
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
                .subscribeBy(
                    onSuccess = { posts ->
                        resultEmitter.onSuccess(
                            LoadResult.Page(
                                data = posts
                                    .filter {
                                        if (it.links == null) {
                                            logcat { "Post ${it.name} filtered out in PagingSource as no longer valid." }
                                            false
                                        } else true
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

    override fun getRefreshKey(state: PagingState<Int, Post>): Int? {
        return null
    }
}