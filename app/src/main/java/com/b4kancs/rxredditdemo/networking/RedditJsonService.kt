package com.b4kancs.rxredditdemo.networking

import io.reactivex.rxjava3.core.Single
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface RedditJsonService {

    @GET("/{subreddit}/.json")
    fun getSubredditJson(
        @Path("subreddit") subreddit: String,
        @Query("limit") limit: Int,
        @Query("after") after: String?
    ): Single<Response<RedditJsonListingModel>>



    @GET
    fun getGalleryJson(@Url url: String): Single<Response<List<RedditGalleryListingModel>>>

    @GET("subreddits/search.json")
    fun searchSubredditsByKeyword(@Query("q") keyword: String): Single<Response<RedditSubredditsListingModel>>
}