package com.b4kancs.rxredditdemo.ui.postviewer

import android.util.Log
import androidx.lifecycle.ViewModel

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {
    companion object {
        private const val LOG_TAG = "PostViewerViewModel"
    }

    val pagingDataObservable = pagingDataObservableProvider.cachedPagingObservable()

    fun hello() = Log.d(LOG_TAG, "Hello!")
}