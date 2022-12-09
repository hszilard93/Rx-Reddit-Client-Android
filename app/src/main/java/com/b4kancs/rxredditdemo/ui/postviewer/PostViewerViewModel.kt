package com.b4kancs.rxredditdemo.ui.postviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.data.database.FavoritesDbEntryPost
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)

    val pagingDataObservable = pagingDataObservableProvider.cachedPagingObservable()

    fun openRedditLinkOfPost(post: Post, context: Context): Completable {
        logcat { "openRedditLinkOfPost: post = ${post.permalink}" }
        return Completable.create { emitter ->
            try {
                val link = "https://reddit.com${post.permalink}"
                val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                context.startActivity(urlIntent)
                emitter.onComplete()
            }
            catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Could not open link! Message: ${e.message}" }
                emitter.onError(e)
            }
        }
    }

    fun getFavoritePosts(): Single<List<FavoritesDbEntryPost>> =
        favoritePostsRepository.getAllFavoritePostsFromDb()

    fun addPostToFavorites(post: Post): Completable =
        favoritePostsRepository.addFavoritePostToDb(post)

    fun removePostFromFavorites(post: Post): Completable =
        favoritePostsRepository.removeFavoritePostFromDb(post)
}