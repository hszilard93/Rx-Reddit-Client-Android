package com.b4kancs.rxredditdemo.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [FavoritesDbEntryPost::class], version = 1, exportSchema = false)
@TypeConverters(DateConverter::class)
abstract class FavoritesDatabase : RoomDatabase() {
    abstract fun favoritesDao(): PostFavoritesDao
}