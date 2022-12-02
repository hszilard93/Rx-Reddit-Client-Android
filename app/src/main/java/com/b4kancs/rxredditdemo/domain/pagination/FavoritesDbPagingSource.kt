package com.b4kancs.rxredditdemo.domain.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.data.database.toPost
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import io.reactivex.rxjava3.core.Single
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import kotlin.streams.toList

class FavoritesDbPagingSource : RxPagingSource<Int, Post>() {

    companion object {
        const val PAGE_SIZE = 5

        // A Pager always requires a new instance of PagingSource, so we'll keep this useful cache here in the companion object.
        private val cachedPostsMap: HashMap<String, Post> by lazy { HashMap() }
    }

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)

    override fun loadSingle(params: LoadParams<Int>): Single<LoadResult<Int, Post>> {
        val offset = params.key ?: 0
        logcat(LogPriority.INFO) { "loadSingle: offset = $offset" }
        return try {
            favoritePostsRepository.getFavoritesFromDbLimitedAndOffset(PAGE_SIZE, offset)
                .map { dbEntries ->
                    // TODO: Error handling
                    logcat { "DbEntries = ${dbEntries.map { it.name }}" }
                    dbEntries
                        .parallelStream()       // Loading posts' data from the network in sequence takes too much time.
                        .map { dbEntry ->
                            if (dbEntry.name !in cachedPostsMap) {
                                logcat { "Loading post data ${dbEntry.name} from network." }
                                dbEntry.toPost()?.let { post ->
                                    cachedPostsMap[dbEntry.name] = post
                                }
                            } else logcat { "Loading post data ${dbEntry.name} from cache." }
                            cachedPostsMap[dbEntry.name]!!
                        }
                        .toList()
                }
                .map { posts ->
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
                }
        } catch (e: Exception) {
            Single.just(LoadResult.Error(e))
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Post>): Int? {
        return null
    }
}