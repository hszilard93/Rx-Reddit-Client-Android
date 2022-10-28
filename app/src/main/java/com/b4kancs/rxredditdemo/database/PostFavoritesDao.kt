package com.b4kancs.rxredditdemo.database

import androidx.room.*
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
interface PostFavoritesDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPost(post: PostFavoritesDbEntry): Single<Long>

    @Query("SELECT * FROM favoritePosts ORDER BY name")
    fun getFavorites(): Single<List<PostFavoritesDbEntry>>

    @Delete
    fun deletePost(post: PostFavoritesDbEntry): Completable
}