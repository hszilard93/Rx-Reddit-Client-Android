package com.b4kancs.rxredditdemo.networking

import com.google.gson.annotations.SerializedName

data class RedditRssListingModel(
    val kind: String,
    val data: ListingDataModel
)

data class ListingDataModel(
    val children: List<RedditPostModel>
)

data class RedditPostModel(
    val kind: String,
    val data: RedditPostDataModel
)

data class RedditPostDataModel(
    @SerializedName("author") val author: String,
    @SerializedName("title") val title: String,
    @SerializedName("subreddit") val subreddit: String,
    @SerializedName("url_overridden_by_dest") val link: String,
    @SerializedName("permalink") val permalink: String,
    @SerializedName("domain") val domain: String,
    @SerializedName("num_comments") val numOfComments: Int
)