package com.b4kancs.rxredditdemo.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel(), PostPagingDataObservableProvider {

//    private val _text = MutableLiveData<String>().apply {
//        value = "This is home Fragment"
//    }
//    val text: LiveData<String> = _text

    var isAppJustStarted = true
    private val subredditLiveData = MutableLiveData(RedditJsonPagingSource.defaultSubreddit)
    private val _subredditNameLiveData = MutableLiveData(subredditLiveData.value?.name!!)
    val subredditNameLiveData: LiveData<String> = _subredditNameLiveData
    val cachedPagingObservable: Observable<PagingData<Post>>
    private val disposables = CompositeDisposable()
    private var subredditAddress = subredditLiveData.value?.address

    init {
        logcat { "init" }
        val pager = Pager(
            PagingConfig(
                pageSize = RedditJsonPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = RedditJsonPagingSource.PAGE_SIZE
            )
        ) { RedditJsonPagingSource(subredditAddress!!) }
        cachedPagingObservable = pager.observable.cachedIn(this.viewModelScope)
    }

    fun changeSubreddit(newSub: Subreddit) {
        logcat { "changeSubreddit: newSub = ${newSub.name}" }
        subredditLiveData.postValue(newSub)
        _subredditNameLiveData.postValue(newSub.name)
        subredditAddress = newSub.address
    }

    override fun cachedPagingObservable(): Observable<PagingData<Post>> = cachedPagingObservable
}