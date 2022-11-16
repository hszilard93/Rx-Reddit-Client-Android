package com.b4kancs.rxredditdemo.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import kotlin.streams.toList

class FavoritesDbPagingSource : RxPagingSource<Int, Post>() {

    companion object {
        const val PAGE_SIZE = 5

        // A Pager always requires a new instance of PagingSource, so we'll keep this useful little cache here.
        private val cachedPostsMap: HashMap<String, Post> by lazy { HashMap() }
    }

    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)

    override fun loadSingle(params: LoadParams<Int>): Single<LoadResult<Int, Post>> {
        val offset = params.key ?: 0
        logcat(LogPriority.INFO) { "loadSingle: offset = $offset" }
        return try {
            favoritesDatabase.favoritesDao().getFavoritesPaged(PAGE_SIZE, offset)
                .subscribeOn(Schedulers.io())
                .map { dbEntries ->
                    // TODO: Error handling
                    dbEntries
                        .parallelStream()       // Loading posts from the network one by one takes time. This should speed things up.
                        .map { dbEntry ->
                            if (dbEntry.name !in cachedPostsMap) {
                                logcat { "Loading post ${dbEntry.name} from network." }
                                PostFavoritesDbEntry.toPost(dbEntry)?.let { post ->
                                    cachedPostsMap[dbEntry.name] = post
                                }
                            } else logcat { "Loading post ${dbEntry.name} from cache." }
                            cachedPostsMap[dbEntry.name]
                        }
                        .filter { it?.links != null }   // The post could have been deleted, for example.
                        .toList()
                        .filterNotNull()
                }
                .map { posts ->
                    LoadResult.Page(
                        data = posts,
                        prevKey = null,
                        nextKey = if (posts.isNotEmpty()) offset + posts.size else null
                    )
                }
        } catch (e: Exception) {
            Single.just(LoadResult.Error(e))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Post>): Int? {
        return null
    }
}