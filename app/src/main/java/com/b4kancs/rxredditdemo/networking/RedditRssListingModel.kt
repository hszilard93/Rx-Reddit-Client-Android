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
    val author: String,
    val title: String,
    val subreddit: String,
    @SerializedName("url_overridden_by_dest") val link: String,
    val permalink: String,
    val domain: String,
    @SerializedName("created") val createdAt: Int,
    @SerializedName("over_18") val nsfw: Boolean,
    @SerializedName("num_comments") val numOfComments: Int
)