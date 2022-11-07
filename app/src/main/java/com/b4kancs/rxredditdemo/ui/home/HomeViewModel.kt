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
import com.b4kancs.rxredditdemo.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel(), PostPagingDataObservableProvider {

//    private val _text = MutableLiveData<String>().apply {
//        value = "This is home Fragment"
//    }
//    val text: LiveData<String> = _text

    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)
    private val subredditLiveData = MutableLiveData(RedditJsonPagingSource.defaultSubreddit)
    private val _subredditNameLiveData = MutableLiveData(subredditLiveData.value?.name!!)
    val subredditNameLiveData: LiveData<String> = _subredditNameLiveData
    val cachedPagingObservable: Observable<PagingData<Post>>
    private val disposables = CompositeDisposable()
    var isAppJustStarted = true
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
        cachedPagingObservable = pager.observable
            .cachedIn(this.viewModelScope)
    }

    fun changeSubreddit(newSub: Subreddit) {
        logcat { "changeSubreddit: newSub = ${newSub.name}" }
        subredditLiveData.postValue(newSub)
        _subredditNameLiveData.postValue(newSub.name)
        subredditAddress = newSub.address
    }

    fun getFavoritePosts(): Single<List<PostFavoritesDbEntry>> {
        return favoritesDatabase.favoritesDao().getFavorites()
            .subscribeOn(Schedulers.io())
    }

    override fun cachedPagingObservable(): Observable<PagingData<Post>> = cachedPagingObservable
}