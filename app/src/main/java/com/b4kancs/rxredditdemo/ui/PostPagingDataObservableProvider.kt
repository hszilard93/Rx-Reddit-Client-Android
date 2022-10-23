package com.b4kancs.rxredditdemo.ui

import androidx.paging.PagingData
import com.b4kancs.rxredditdemo.model.Post
import io.reactivex.rxjava3.core.Observable

interface PostPagingDataObservableProvider {
    fun cachedPagingObservable(): Observable<PagingData<Post>>
}