package com.b4kancs.rxredditdemo.ui.postviewer

import androidx.lifecycle.ViewModel

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {
    companion object {
        private const val LOG_TAG = "PostViewerViewModel"
    }

    val pagingDataObservable = pagingDataObservableProvider.cachedPagingObservable()
}