package com.b4kancs.rxredditdemo.networking

import com.google.gson.annotations.SerializedName

// Main listing
data class RedditRssListingModel(
    val kind: String,
    val data: ListingDataModel
) {
    // "/data/"
    data class ListingDataModel(
        val children: List<RedditPostModel>
    )

    // "/data/children/"
    data class RedditPostModel(
        val kind: String,
        val data: RedditPostDataModel
    )

    // "/data/children/data/"
    data class RedditPostDataModel(
        val name: String,
        val author: String,
        val title: String,
        val subreddit: String,
        val url: String,
        val permalink: String,
        val domain: String,
        val score: Int,
        @SerializedName("created") val createdAt: Int,
        @SerializedName("over_18") val nsfw: Boolean,
        @SerializedName("num_comments") val numOfComments: Int,
        @SerializedName("crosspost_parent_list") val crosspostParents: List<RedditPostModelParent>?
    )

    // "/data/children/data/0/"
    data class RedditPostModelParent(
        val subreddit: String,
        val url: String
    )
}

// A post's listing. Contains the pictures from the gallery.
data class RedditGalleryListing(
    val data: RedditGalleryPostData
) {

    // "/0/data/"
    data class RedditGalleryPostData(
        val children: List<RedditGalleryPostDataChild>
    )

    // "/0/data/children/0/"
    data class RedditGalleryPostDataChild(
        val data: RedditGalleryPostDataChildData
    )

    // "/0/data/children/0/data/"
    data class RedditGalleryPostDataChildData(
        val galleryData: RedditGalleryPostDataChildDataGalleryData
    )

    // "/0/data/children/0/data/gallery_data"
    data class RedditGalleryPostDataChildDataGalleryData(
        val items: List<RedditPostDataChildDataGalleryDataItem>
    )

    // "/0/data/children/0/gallery_data/items"
    data class RedditPostDataChildDataGalleryDataItem(
        val mediaId: String
    )
}

