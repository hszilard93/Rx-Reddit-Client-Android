package com.b4kancs.rxredditdemo.ui.postviewer

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.data.database.FavoritesDbEntryPost
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.koin.java.KoinJavaComponent.inject

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)

    val pagingDataObservable = pagingDataObservableProvider.cachedPagingObservable()

    fun getFavoritePosts(): Single<List<FavoritesDbEntryPost>> =
        favoritePostsRepository.getAllFavoritePostsFromDb()

    fun addPostToFavorites(post: Post): Completable =
        favoritePostsRepository.addFavoritePostToDb(post)

    fun removePostFromFavorites(post: Post): Completable =
        favoritePostsRepository.removeFavoritePostFromDb(post)
}