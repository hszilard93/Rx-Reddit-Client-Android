package com.b4kancs.rxredditdemo.database

import androidx.room.*
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
interface PostFavoritesDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPost(post: FavoritesDbEntryPost): Single<Long>

    @Query("SELECT * FROM favoritePosts ORDER BY name")
    fun getFavorites(): Single<List<FavoritesDbEntryPost>>

    @Query("SELECT * FROM favoritePosts ORDER BY addedDate DESC LIMIT :limit OFFSET :offset")
    fun getFavoritesPaged(limit: Int, offset: Int): Single<List<FavoritesDbEntryPost>>

    @Delete
    fun deletePost(post: FavoritesDbEntryPost): Completable

    @Query("DELETE FROM favoritePosts")
    fun deleteAll(): Completable
}