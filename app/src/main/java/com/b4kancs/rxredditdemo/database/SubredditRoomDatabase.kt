package com.b4kancs.rxredditdemo.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.b4kancs.rxredditdemo.model.Subreddit
import io.reactivex.rxjava3.schedulers.Schedulers

object SubredditRoomDatabase {
    private var database: SubredditDatabase? = null

    fun fetchDatabase(context: Context): SubredditDatabase {
        val localDatabaseCopy = database
        return if (localDatabaseCopy != null) {
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
            database = localDatabase
            localDatabase
        }
    }
}