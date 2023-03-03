package com.b4kancs.rxredditdemo.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

@Entity(tableName = "follows")
data class UserFeed(
    @PrimaryKey val name: String,
    val status: Status
) {
    enum class Status { NOT_IN_DB, FOLLOWED, SUBSCRIBED, AGGREGATE, SUBSCRIPTIONS }
}