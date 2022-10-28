package com.b4kancs.rxredditdemo.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.b4kancs.rxredditdemo.model.Post

@Entity(tableName = "favoritePosts")
data class PostFavoritesDbEntry(
    @PrimaryKey val name: String,
    val url: String
) {
    companion object {
        fun fromPost(post: Post) = PostFavoritesDbEntry(post.name, post.url)
    }
}