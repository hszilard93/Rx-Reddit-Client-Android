package com.b4kancs.rxredditdemo.model

data class Post(
    val name: String,
    val author: String,
    val title: String,
    val subreddit: String,
    val url: String,
    val links: List<String>?,
    val permalink: String,
    val domain: String,
    val crossPostFrom: String?,
    val score: Int,
    val createdAt: Int,
    val nsfw: Boolean,
    val numOfComments: Int
)