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

    @Delete
    fun deleteSubreddit(subreddit: Subreddit): Completable
}