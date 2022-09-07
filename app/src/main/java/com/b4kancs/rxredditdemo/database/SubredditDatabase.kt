package com.b4kancs.rxredditdemo.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.b4kancs.rxredditdemo.model.Subreddit

@Database(entities = [Subreddit::class], version = 1)
abstract class SubredditDatabase : RoomDatabase() {
    abstract fun subredditDao(): SubredditDao
}