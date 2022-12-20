package com.b4kancs.rxredditdemo.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follows")
data class UserFeed(
    @PrimaryKey val name: String
)
