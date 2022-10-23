package com.b4kancs.rxredditdemo.ui.postviewer

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider

class PostViewerViewModel(pagingDataObservableProvider: PostPagingDataObservableProvider) : ViewModel() {

    val pagingDataObservable = pagingDataObservableProvider.cachedPagingObservable()
}