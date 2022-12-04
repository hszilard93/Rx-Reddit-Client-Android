package com.b4kancs.rxredditdemo.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.b4kancs.rxredditdemo.model.Subreddit
import io.reactivex.rxjava3.schedulers.Schedulers
import logcat.LogPriority
import logcat.logcat

object SubredditRoomDatabase {
    private var database: SubredditDatabase? = null

    fun fetchDatabase(context: Context): SubredditDatabase {
        logcat { "fetchDatabase" }

        val localDatabaseCopy = database
        localDatabaseCopy?.let {
            logcat { "Returning existing database instance." }
            return it
        }

        var hasJustBeenCreated = false
        // A new db instance needs to be created.
        val localDatabase = Room
            .databaseBuilder(context.applicationContext, SubredditDatabase::class.java, "subreddit_db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    logcat(LogPriority.INFO) { "onCreate. New SubredditDatabase has been created." }
                    hasJustBeenCreated = true
                    super.onCreate(db)
                }
            })
            .build()

        // Prompts the builder to build the db already... ..
        localDatabase.subredditDao().getSubreddits()
            .subscribeOn(Schedulers.io())
            .blockingSubscribe()

        // Only now can we assume that onCreate has run and the flag has been set, if necessary.
        if (hasJustBeenCreated) {
            val defaultSubreddits = Subreddit.parseDefaultSubredditsFromXml()
            localDatabase.subredditDao().insertSubreddits(defaultSubreddits)
                .subscribeOn(Schedulers.io())
                .blockingSubscribe()
        }

        database = localDatabase
        logcat(LogPriority.INFO) { "Returning new database instance." }
        return localDatabase
    }
}