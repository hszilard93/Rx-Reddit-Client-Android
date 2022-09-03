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
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.database.SubredditRoomDatabase
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditRssPagingSource
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

private const val LOG_TAG = "HomeViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel() {

//    private val _text = MutableLiveData<String>().apply {
//        value = "This is home Fragment"
//    }
//    val text: LiveData<String> = _text

    private val subredditDatabase: SubredditDatabase by inject(SubredditDatabase::class.java)
    private val disposables = CompositeDisposable()
    private val _subreddit = MutableLiveData(RedditRssPagingSource.DEFAULT_SUBREDDIT)
    val subredditChangedSubject: PublishSubject<Unit> = PublishSubject.create()
    val subredditNameLiveData: LiveData<String> = _subreddit
    val cachedFlowable: Flowable<PagingData<Post>>

    init {
        val pager = Pager(PagingConfig(
            pageSize = RedditRssPagingSource.PAGE_SIZE,
            prefetchDistance = 5,
            initialLoadSize = RedditRssPagingSource.PAGE_SIZE
        )) { RedditRssPagingSource(_subreddit.value!!) }
        cachedFlowable = pager.flowable.cachedIn(this.viewModelScope)

        subredditDatabase.subredditDao().getSubreddits()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subs ->
                println("SUBS IN DB:")
                subs.forEach {
                    println("SUB: ${it.name}")
                }
            }
            .addTo(disposables)

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