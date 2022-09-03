package com.b4kancs.rxredditdemo.networking

import io.reactivex.rxjava3.core.Single
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface RedditRssService {

    @GET("/{subreddit}/.json")
    fun getSubredditJson(
        @Path("subreddit") subreddit: String,
        @Query("limit") limit: Int,
        @Query("after") after: String?
    ): Single<Response<RedditRssListingModel>>

    @GET
    fun getGalleryJson(@Url url: String): Single<Response<List<RedditGalleryListing>>>
}