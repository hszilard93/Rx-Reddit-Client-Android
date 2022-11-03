package com.b4kancs.rxredditdemo.pagination

import androidx.paging.PagingState
import androidx.paging.rxjava3.RxPagingSource
import com.b4kancs.rxredditdemo.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.java.KoinJavaComponent.inject

class RedditDbPagingSource : RxPagingSource<Int, Post> (){

    companion object {
        const val PAGE_SIZE = 5
    }

    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)

    override fun loadSingle(params: LoadParams<Int>): Single<LoadResult<Int, Post>> {
        val page = params.key ?: 0
        return favoritesDatabase.favoritesDao().getFavoritesPaged(PAGE_SIZE, page)
            .subscribeOn(Schedulers.io())
            .map { dbEntries ->
                // TODO: Error handling
                dbEntries
                    .mapNotNull { PostFavoritesDbEntry.toPost(it) }
                    .filter { it.links != null }    // The post could have been deleted, for example.
            }
            .map { posts ->
                LoadResult.Page(
                    data = posts,
                    prevKey = null,
                    nextKey = if (posts.size >= PAGE_SIZE) page + 1 else null
                )
            }
    }

    override fun getRefreshKey(state: PagingState<Int, Post>): Int? {
        return null
    }
}