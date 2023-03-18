package com.b4kancs.rxredditdemo.repository

import com.b4kancs.rxredditdemo.model.Post
import logcat.logcat

class PostsPropertiesRepository {

    private val dontBlurThesePostsSet = mutableSetOf<String>()

    fun addPostToDontBlurThesePostsSet(post: Post) {
        logcat { "addPostToDontBlurThesePostsSet: post = ${post.name}" }
        dontBlurThesePostsSet.add(post.name)
    }

    fun removePostFromDontBlurThesePostsSet(post: Post) {
        logcat { "removePostFromDontBlurThesePostsSet: post = ${post.name}" }
        dontBlurThesePostsSet.remove(post.name)
    }

    fun isPostInDontBlurThesePostsSet(post: Post): Boolean {
        logcat { "isPostInDontBlurThesePostsSet: post = ${post.name}" }
        return post.name in dontBlurThesePostsSet
    }
}