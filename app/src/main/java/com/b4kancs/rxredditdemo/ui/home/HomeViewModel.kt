package com.b4kancs.rxredditdemo.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.flowable
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.networking.RedditRssPagingSource
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "HomeViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel() {

//    private val _text = MutableLiveData<String>().apply {
//        value = "This is home Fragment"
//    }
//    val text: LiveData<String> = _text

    val subredditChangedSubject: PublishSubject<Unit> = PublishSubject.create()
    private val disposables = CompositeDisposable()
    private val _subreddit = MutableLiveData(RedditRssPagingSource.DEFAULT_SUBREDDIT)
    val subredditNameLiveData: LiveData<String> = _subreddit
    val cachedFlowable: Flowable<PagingData<Post>>

    init {
        val pager = Pager(PagingConfig(
            pageSize = RedditRssPagingSource.PAGE_SIZE,
            prefetchDistance = 5,
            initialLoadSize = RedditRssPagingSource.PAGE_SIZE
        )) { RedditRssPagingSource(_subreddit.value!!) }
        cachedFlowable = pager.flowable.cachedIn(this.viewModelScope)

//        Observable.timer(2, TimeUnit.SECONDS)
//            .subscribe {
//                changeSubreddit("funny")
//            }
    }

    fun changeSubreddit(newSubreddit: String) {
        _subreddit.postValue(newSubreddit)
        subredditChangedSubject.onNext(Unit)
    }
}