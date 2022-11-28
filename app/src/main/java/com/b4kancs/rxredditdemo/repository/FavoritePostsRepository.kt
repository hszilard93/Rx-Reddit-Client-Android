package com.b4kancs.rxredditdemo.repository;

import com.b4kancs.rxredditdemo.data.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.data.database.FavoritesDbEntryPost
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class FavoritePostsRepository {

    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)
    val isFavoritePostsNotEmptyBehaviorSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()

    fun getAllFavoritePostsFromDb(): Single<List<FavoritesDbEntryPost>> {
        logcat { "getAllFavoritePostsFromDb" }
        return favoritesDatabase.favoritesDao().getFavorites()
            .subscribeOn(Schedulers.io())
            .doOnSuccess { favoriteDbEntries ->
                isFavoritePostsNotEmptyBehaviorSubject.onNext(favoriteDbEntries.isNotEmpty())
            }
            .doOnError { e ->
                logcat(LogPriority.ERROR) { "Could not get favorite posts from DB! Message: ${e.message}" }
            }
    }

    fun deleteAllFavoritePostsFromDb(): Completable {
        logcat { "deleteAllFavoritePostsFromDb" }
        return favoritesDatabase.favoritesDao().deleteAll()
            .subscribeOn(Schedulers.io())
            .doOnError { e -> logcat(LogPriority.ERROR) { "Could not delete posts from DB! Message: ${e.message}" } }
            .doOnComplete { isFavoritePostsNotEmptyBehaviorSubject.onNext(false) }
    }
}
