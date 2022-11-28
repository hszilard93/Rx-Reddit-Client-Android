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
        return if (localDatabaseCopy != null) {
            logcat { "Returning existing database instance." }
            localDatabaseCopy
        } else {
            val localDatabase = Room.databaseBuilder(context.applicationContext, SubredditDatabase::class.java, "subreddit_db")
                .addCallback(object : RoomDatabase.Callback() {

                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val defaultSubreddits = Subreddit.parseDefaultSubredditsFromXml()
                        val subredditDatabase = database ?: return
                        subredditDatabase.subredditDao().insertSubreddits(defaultSubreddits)
                            .subscribeOn(Schedulers.io())
                            .subscribe()
                    }
                })
                .build()
            // This is here because the db doesn't get initialized until the first transaction happens
            localDatabase.subredditDao().getSubreddits()
            database = localDatabase
            logcat(LogPriority.INFO) { "Returning new database instance." }
            localDatabase
        }
    }
}