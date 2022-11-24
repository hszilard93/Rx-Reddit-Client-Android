package com.b4kancs.rxredditdemo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.database.FavoritesDbEntryPost
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.b4kancs.rxredditdemo.ui.shared.FavoritesProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(val mainViewModel: MainViewModel) : ViewModel(), PostPagingDataObservableProvider, FavoritesProvider {

    val cachedPagingObservable: Observable<PagingData<Post>>
    var isAppJustStarted = true
    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)
    private lateinit var subredditAddress: String
    private val disposables = CompositeDisposable()

    init {
        logcat { "init main viewModel = $mainViewModel" }
        mainViewModel.selectedSubredditReplayObservable
            .subscribe { subredditAddress = it.address }
            .addTo(disposables)

        val pager = Pager(
            PagingConfig(
                pageSize = RedditJsonPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = RedditJsonPagingSource.PAGE_SIZE
            )
        ) {
            RedditJsonPagingSource(subredditAddress)
        }
        cachedPagingObservable = pager.observable
            .cachedIn(viewModelScope)
    }

    override fun getFavoritePosts(): Single<List<FavoritesDbEntryPost>> {
        logcat { "getFavoritePosts" }
        return favoritesDatabase.favoritesDao().getFavorites()
            .subscribeOn(Schedulers.io())
    }

    override fun cachedPagingObservable(): Observable<PagingData<Post>> = cachedPagingObservable
}