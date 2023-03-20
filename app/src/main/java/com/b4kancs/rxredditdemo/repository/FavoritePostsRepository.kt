package com.b4kancs.rxredditdemo.repository;

import com.b4kancs.rxredditdemo.data.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import logcat.LogPriority
import logcat.logcat

class FavoritePostsRepository(
    private val favoritesDatabase: FavoritesDatabase
) {

    val favoritePostEntriesBehaviorSubject = BehaviorSubject.create<List<PostFavoritesDbEntry>>()

    fun getAllFavoritePostsFromDb(): Single<List<PostFavoritesDbEntry>> {
        logcat { "getAllFavoritePostsFromDb" }

        // The favoritePostEntriesBehaviorSubject always maintains an up to date version of the Favorites;
        // Only perform db query if the Favorites have not yet been loaded.
        return if (favoritePostEntriesBehaviorSubject.hasValue()) {
            Single.just(favoritePostEntriesBehaviorSubject.value!!)
        }
        else {
            favoritesDatabase.favoritesDao().getFavorites()
                .subscribeOn(Schedulers.io())
                .retry(1)
                .doOnSuccess { favoriteDbEntries ->
                    favoritePostEntriesBehaviorSubject.onNext(favoriteDbEntries)
                }
                .doOnError { e ->
                    logcat(LogPriority.ERROR) { "Could not get favorite posts from DB! Message: ${e.message}" }
                }
        }
    }

    fun getFavoritesFromDbLimitedAndOffset(limit: Int, offset: Int): Single<List<PostFavoritesDbEntry>> {
        logcat { "getFavoritesFromDbLimitedOffset: limit = $limit, offset = $offset" }
        return favoritesDatabase.favoritesDao().getFavoritesPaged(limit, offset)
            .subscribeOn(Schedulers.io())
    }

    fun addFavoritePostToDb(post: Post): Completable {
        logcat(LogPriority.INFO) { "addFavoritePostToDb: post = ${post.name}" }
        val newDbEntryPost = PostFavoritesDbEntry.fromPost(post)
        return favoritesDatabase.favoritesDao().insertPost(newDbEntryPost)
            .subscribeOn(Schedulers.io())
            .retry(1)
            .doOnSuccess {
                val oldList = favoritePostEntriesBehaviorSubject.value!!
                val newList = oldList.plus(newDbEntryPost)
                favoritePostEntriesBehaviorSubject.onNext(newList)
            }
            .flatMapCompletable { insertedThisMany ->
                if (insertedThisMany == 1L) Completable.complete() else Completable.error(Exception())
            }
    }

    fun removeFavoritePostFromDb(post: Post): Completable {
        logcat(LogPriority.INFO) { "removePostFromFavorites: post = ${post.name}" }
        return favoritesDatabase.favoritesDao().deletePost(PostFavoritesDbEntry.fromPost(post))
            .retry(1)
            .subscribeOn(Schedulers.io())
            .doOnComplete {
                val oldList = favoritePostEntriesBehaviorSubject.value!!
                val newList = oldList.minus(oldList.first { it.name == post.name })
                favoritePostEntriesBehaviorSubject.onNext(newList)
            }
    }

    fun deleteAllFavoritePostsFromDb(): Completable {
        logcat(LogPriority.INFO) { "deleteAllFavoritePostsFromDb" }
        return favoritesDatabase.favoritesDao().deleteAll()
            .subscribeOn(Schedulers.io())
            .retry(1)
            .doOnError { e -> logcat(LogPriority.ERROR) { "Could not delete posts from DB! Message: ${e.message}" } }
            .doOnComplete { favoritePostEntriesBehaviorSubject.onNext(emptyList()) }
    }
}
