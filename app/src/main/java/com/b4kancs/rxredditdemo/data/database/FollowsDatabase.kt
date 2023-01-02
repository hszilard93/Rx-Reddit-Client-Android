package com.b4kancs.rxredditdemo.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.b4kancs.rxredditdemo.model.UserFeed

@Database(entities = [UserFeed::class],
    version = 2
)
abstract class FollowsDatabase : RoomDatabase() {
    abstract fun followsDao(): FollowsDao
}