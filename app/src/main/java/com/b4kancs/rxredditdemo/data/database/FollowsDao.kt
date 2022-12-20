package com.b4kancs.rxredditdemo.data.database

import androidx.room.*
import com.b4kancs.rxredditdemo.model.UserFeed
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
interface FollowsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFollowedUser(userFeed: UserFeed): Completable

    @Query("SELECT * FROM follows ORDER BY name")
    fun getFollowedUsers(): Single<List<UserFeed>>

    @Delete
    fun deleteFollowedUser(userFeed: UserFeed): Completable
}