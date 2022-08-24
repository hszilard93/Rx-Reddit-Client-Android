package com.b4kancs.rxredditdemo.ui.home

import Post
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

private const val LOG_TAG = "HomeViewModel"

class HomeViewModel : ViewModel() {

//    private val _text = MutableLiveData<String>().apply {
//        value = "This is home Fragment"
//    }
//    val text: LiveData<String> = _text

    private val posts = ArrayList<Post>()
    val subredditNameLiveData = MutableLiveData<String>("subreddit")
    val postsLiveData = MutableLiveData<List<Post>>()

    init {
        RedditRssFeed.getPostsOnSub("EarthPorn")
            .subscribe { posts ->
                subredditNameLiveData.postValue(posts.first().subreddit)
                Log.d(LOG_TAG, "Current thread: " + Thread.currentThread().name)
                posts.forEach {
                    this.posts.add(it)
                    println("${it.title} ${it.author} ${it.link}")
                }
                postsLiveData.postValue(posts)
            }
    }
}