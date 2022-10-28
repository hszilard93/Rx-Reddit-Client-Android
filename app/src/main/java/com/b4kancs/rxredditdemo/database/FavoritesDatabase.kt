package com.b4kancs.rxredditdemo.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PostFavoritesDbEntry::class], version = 1, exportSchema = false)
abstract class FavoritesDatabase : RoomDatabase() {
    abstract fun favoritesDao(): PostFavoritesDao
}