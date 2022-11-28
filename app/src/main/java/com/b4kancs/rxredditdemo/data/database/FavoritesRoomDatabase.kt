package com.b4kancs.rxredditdemo.data.database

import android.content.Context
import androidx.room.Room
import logcat.LogPriority
import logcat.logcat

object FavoritesRoomDatabase {
    private var database: FavoritesDatabase? = null

    fun fetchDatabase(context: Context): FavoritesDatabase {
        logcat { "fetchDatabase" }
        val localDatabaseCopy = database
        return if (localDatabaseCopy != null) {
            logcat { "Returning existing database instance." }
            localDatabaseCopy
        } else {
            val localDatabase = Room.databaseBuilder(context.applicationContext, FavoritesDatabase::class.java, "favorites_db")
                .build()
            localDatabase.favoritesDao().getFavorites()
            database = localDatabase
            logcat(LogPriority.INFO) { "Returning new database instance." }
            localDatabase
        }
    }
}