package com.b4kancs.rxredditdemo.data.networking

import com.google.gson.annotations.SerializedName

// Main listing
data class RedditJsonListingModel(
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

    // This is a post's data.
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
        @SerializedName("media_metadata") val mediaMetadata: HashMap<String, RedditPostListingModel.RedditPostDataChildDataMediaMetadataItem>,
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

// A post's listing. Contains all the post's data.
// For example: https://old.reddit.com/r/EarthPorn/comments/yiirgv/0/.json
data class RedditPostListingModel(
    val data: RedditPostData
) {

    // "/0/data/"
    data class RedditPostData(
        val children: List<RedditPostDataChild>
    )

    // "/0/data/children/0/"
    data class RedditPostDataChild(
        val data: RedditJsonListingModel.RedditPostDataModel
    )

    // "/0/data/children/0/data"
//    data class RedditPostDataChildDataMediaMetadata(
//        val items: HashMap<String, RedditPostDataChildDataMediaMetadataItem>
//    )

    data class RedditPostDataChildDataMediaMetadataItem(
        val id: String,
        @SerializedName("m") val type: String
    )

    // "/0/data/children/0/data/gallery_data"
//    data class RedditPostDataChildDataGalleryData(
//        val items: List<RedditPostDataChildDataGalleryDataItem>
//    )

    // "/0/data/children/0/gallery_data/items"
//    data class RedditPostDataChildDataGalleryDataItem(
//        val mediaId: String
//    )
}


// Represents a list of subreddits (the results of a search).
data class RedditSubredditsListingModel(
    val data: RedditSubredditData
) {
    // "/data"
    data class RedditSubredditData(
        val children: List<RedditSubredditDataChild>
    )

    // "/data/children/0/"
    data class RedditSubredditDataChild(
        val data: RedditSubredditDataChildData
    )

    // "/data/children/0/data"
    // Represents a single subreddits' data.
    data class RedditSubredditDataChildData(
        val url: String,
        @SerializedName("display_name") val name: String,
        @SerializedName("over18") val nsfw: Boolean
    )
}