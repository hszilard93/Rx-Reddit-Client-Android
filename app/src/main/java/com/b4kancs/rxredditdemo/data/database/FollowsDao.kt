package com.b4kancs.rxredditdemo.data.database

import androidx.room.*
import com.b4kancs.rxredditdemo.model.UserFeed
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
interface FollowsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFollowedUser(userFeed: UserFeed): Completable

    @Query("SELECT * FROM follows ORDER BY name")
    fun getFollowedUsers(): Single<List<UserFeed>>

    @Query("SELECT * FROM follows WHERE status = 'SUBSCRIBED' ORDER BY name")
    fun getSubscribedUsers(): Single<List<UserFeed>>

    @Query("SELECT * FROM follows WHERE LOWER(name) LIKE LOWER('%' || :name || '%')")
    fun getFollowedUsersByNameLike(name: String): Single<List<UserFeed>>

    @Delete
    fun deleteFollowedUser(userFeed: UserFeed): Completable

    @Query("UPDATE follows SET lastPost = :postName WHERE name = :userName")
    fun updateLatestPost(userName: String, postName: String): Completable
}