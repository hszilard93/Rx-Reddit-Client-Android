package com.b4kancs.rxredditdemo.repository;

import com.b4kancs.rxredditdemo.data.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.data.database.FavoritesDbEntryPost
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class FavoritePostsRepository {

    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)
    val doesFavoritePostsHaveItemsBehaviorSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()

    fun getAllFavoritePostsFromDb(): Single<List<FavoritesDbEntryPost>> {
        logcat { "getAllFavoritePostsFromDb" }
        return favoritesDatabase.favoritesDao().getFavorites()
            .subscribeOn(Schedulers.io())
            .retry(1)
            .doOnSuccess { favoriteDbEntries ->
                doesFavoritePostsHaveItemsBehaviorSubject.let {
                    if (it.value != null && it.value == false && favoriteDbEntries.isNotEmpty()) {
                        it.onNext(true)
                    }
                    else if (it.value == null) {
                        it.onNext(favoriteDbEntries.isNotEmpty())
                    }
                }
            }
            .doOnError { e ->
                logcat(LogPriority.ERROR) { "Could not get favorite posts from DB! Message: ${e.message}" }
            }
    }

    fun getFavoritesFromDbLimitedAndOffset(limit: Int, offset: Int): Single<List<FavoritesDbEntryPost>> {
        logcat { "getFavoritesFromDbLimitedOffset: limit = $limit, offset = $offset" }
        return favoritesDatabase.favoritesDao().getFavoritesPaged(limit, offset)
            .subscribeOn(Schedulers.io())
    }

    fun addFavoritePostToDb(post: Post): Completable {
        logcat(LogPriority.INFO) { "addFavoritePostToDb: post = ${post.name}" }
        return favoritesDatabase.favoritesDao().insertPost(FavoritesDbEntryPost.fromPost(post))
            .subscribeOn(Schedulers.io())
            .retry(1)
            .flatMapCompletable { if (it > 0) Completable.complete() else Completable.error(Exception()) }
    }

    fun removeFavoritePostFromDb(post: Post): Completable {
        logcat(LogPriority.INFO) { "removePostFromFavorites: post = ${post.name}" }
        return favoritesDatabase.favoritesDao().deletePost(FavoritesDbEntryPost.fromPost(post))
            .retry(1)
            .subscribeOn(Schedulers.io())
    }

    fun deleteAllFavoritePostsFromDb(): Completable {
        logcat(LogPriority.INFO) { "deleteAllFavoritePostsFromDb" }
        return favoritesDatabase.favoritesDao().deleteAll()
            .subscribeOn(Schedulers.io())
            .retry(1)
            .doOnError { e -> logcat(LogPriority.ERROR) { "Could not delete posts from DB! Message: ${e.message}" } }
            .doOnComplete { doesFavoritePostsHaveItemsBehaviorSubject.onNext(false) }
    }
}
