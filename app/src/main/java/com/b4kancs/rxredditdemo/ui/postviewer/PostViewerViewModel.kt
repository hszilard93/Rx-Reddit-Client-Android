package com.b4kancs.rxredditdemo.ui.postviewer

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {

    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)

    val pagingDataObservable = pagingDataObservableProvider.cachedPagingObservable()

    fun getFavoritePosts(): Single<List<PostFavoritesDbEntry>> {
        return favoritesDatabase.favoritesDao().getFavorites()
            .subscribeOn(Schedulers.io())
    }

    fun addPostToFavorites(post: Post): Completable {
        logcat(LogPriority.INFO) { "addPostToFavorites: post = ${post.name}" }
        return favoritesDatabase.favoritesDao().insertPost(PostFavoritesDbEntry.fromPost(post))
            .subscribeOn(Schedulers.io())
            .flatMapCompletable { if (it > 0) Completable.complete() else Completable.error(Exception()) }
    }

    fun removePostFromFavorites(post: Post): Completable {
        logcat(LogPriority.INFO) { "removePostFromFavorites: post = ${post.name}" }
        return favoritesDatabase.favoritesDao().deletePost(PostFavoritesDbEntry.fromPost(post))
            .subscribeOn(Schedulers.io())
    }
}