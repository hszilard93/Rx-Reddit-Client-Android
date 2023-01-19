package com.b4kancs.rxredditdemo.data.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PostFavoritesDbEntry::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration (from = 1, to = 2)]
)
@TypeConverters(DateConverter::class)
abstract class FavoritesDatabase : RoomDatabase() {
    abstract fun favoritesDao(): PostFavoritesDao
}