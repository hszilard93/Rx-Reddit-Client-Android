package com.b4kancs.rxredditdemo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.pagination.RedditDbPagingSource
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel : ViewModel() {

    val cachedPagingObservable: Observable<PagingData<Post>>

    init {
        logcat { "init"}
        val pager = Pager(
            PagingConfig(
                pageSize = RedditDbPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = RedditJsonPagingSource.PAGE_SIZE
            )
        ) { RedditDbPagingSource() }
        cachedPagingObservable = pager.observable.cachedIn(this.viewModelScope)
    }

}