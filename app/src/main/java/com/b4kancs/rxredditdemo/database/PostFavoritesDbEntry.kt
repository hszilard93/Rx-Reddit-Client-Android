package com.b4kancs.rxredditdemo.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.networking.RedditJsonService
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.java.KoinJavaComponent.inject
import java.util.*

@Entity(tableName = "favoritePosts")
data class PostFavoritesDbEntry(
    @PrimaryKey val name: String,
    val url: String,
    val subreddit: String,
    val addedDate: Date
) {
    companion object {

        fun fromPost(post: Post) = PostFavoritesDbEntry(post.name, post.url, post.subreddit, Date())

        fun toPost(dbEntry: PostFavoritesDbEntry): Post? {
            val service: RedditJsonService by inject(RedditJsonService::class.java)
            val jsonModel =
                service.getPostJson(
                    dbEntry.subreddit,
                    dbEntry.name.substring(3) // Instead of 't3_someth' (the post's name), we need to pass 'someth"
                )
                    .subscribeOn(Schedulers.io())
                    .blockingGet().let { response ->
                        if (response.isSuccessful) response.body()
                        else null
                    } ?: return null
            // TODO: Better error handling
            return Post.from(jsonModel.first().data.children.first().data)
        }
    }
}