package com.b4kancs.rxredditdemo.database

import androidx.room.*
import com.b4kancs.rxredditdemo.model.Subreddit
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
interface SubredditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSubreddit(subreddit: Subreddit): Single<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSubreddits(subreddits: List<Subreddit>): Completable

    @Query("SELECT * FROM subreddit ORDER BY name")
    fun getSubreddits(): Single<List<Subreddit>>

    @Query("SELECT * FROM subreddit WHERE address = :address")
    fun getSubredditByAddress(address: String): Single<Subreddit>

    @Query("SELECT * FROM subreddit WHERE name LIKE :name")
    fun getSubredditsByNameLike(name: String): Single<List<Subreddit>>

    @Delete
    fun deleteSubreddit(subreddit: Subreddit): Completable
}