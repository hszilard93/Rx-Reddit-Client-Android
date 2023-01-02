package com.b4kancs.rxredditdemo.data.database

import android.content.Context
import androidx.room.Room
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat

object FollowsRoomDatabase {
    private var database: FollowsDatabase? = null

    fun fetchDatabase(context: Context): FollowsDatabase {
        logcat { "fetchDatabase" }

        val localDatabaseCopy = database
        return if (localDatabaseCopy != null) {
            logcat { "Returning existing database instance." }
            localDatabaseCopy
        }
        else {
            val localDatabase = Room.databaseBuilder(context.applicationContext, FollowsDatabase::class.java, "follows_db")
                .fallbackToDestructiveMigration()
                .build()
            localDatabase.followsDao().getFollowedUsers().subscribeOn(Schedulers.io()).blockingSubscribe()
            database = localDatabase
            logcat(LogPriority.INFO) { "Returning new database instance." }
            localDatabase
        }
    }
}