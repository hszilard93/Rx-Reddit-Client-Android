package com.b4kancs.rxredditdemo.database

import androidx.room.*
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
interface PostFavoritesDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPost(post: PostFavoritesDbEntry): Single<Long>

    @Query("SELECT * FROM favoritePosts ORDER BY name")
    fun getFavorites(): Single<List<PostFavoritesDbEntry>>

    @Query("SELECT * FROM favoritePosts ORDER BY addedDate ASC LIMIT :limit OFFSET :offset")
    fun getFavoritesPaged(limit: Int, offset: Int): Single<List<PostFavoritesDbEntry>>

    @Delete
    fun deletePost(post: PostFavoritesDbEntry): Completable
}