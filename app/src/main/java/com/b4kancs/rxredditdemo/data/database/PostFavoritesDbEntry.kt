package com.b4kancs.rxredditdemo.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.data.networking.RedditPostListingModel
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.java.KoinJavaComponent.inject
import retrofit2.Response
import java.util.*

@Entity(tableName = "favoritePosts")
data class PostFavoritesDbEntry(
    @PrimaryKey val name: String,
    val url: String,
    val subreddit: String,
    val favoritedDate: Date
) {
    companion object {

        fun fromPost(post: Post) = PostFavoritesDbEntry(post.name, post.url, post.subreddit, Date())

        // The post either exists, has been deleted, or a network error could occur.
        fun loadPostFromNetwork(dbEntry: PostFavoritesDbEntry): Single<Response<List<RedditPostListingModel>>> {
            val service: RedditJsonService by inject(RedditJsonService::class.java)
            return service.getPostJson(
                dbEntry.subreddit,
                dbEntry.name.substring(3) // Instead of 't3_someth' (the post's name), we need to pass 'someth"
            )
        }
    }
}

fun PostFavoritesDbEntry.loadFromNetwork(): Single<Response<List<RedditPostListingModel>>> =
    PostFavoritesDbEntry.loadPostFromNetwork(this)