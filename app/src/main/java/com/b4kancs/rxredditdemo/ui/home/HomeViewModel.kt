package com.b4kancs.rxredditdemo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.data.database.FavoritesDbEntryPost
import com.b4kancs.rxredditdemo.domain.pagination.SubredditJsonPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.SubredditRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.b4kancs.rxredditdemo.ui.shared.FavoritesProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(val mainViewModel: MainViewModel) : ViewModel(), PostPagingDataObservableProvider, FavoritesProvider {

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    private val subredditRepository: SubredditRepository by inject(SubredditRepository::class.java)
    private lateinit var subredditAddress: String
    val disposables = CompositeDisposable()

    val subredditPostsCachedPagingObservable: Observable<PagingData<Post>>
    var isAppJustStarted = true

    init {
        logcat { "init main viewModel = $mainViewModel" }
        mainViewModel.selectedSubredditReplayObservable
            .subscribe { subredditAddress = it.address }
            .addTo(disposables)

        val pager = Pager(
            PagingConfig(
                pageSize = SubredditJsonPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = SubredditJsonPagingSource.PAGE_SIZE
            )
        ) {
            SubredditJsonPagingSource(subredditAddress)
        }
        subredditPostsCachedPagingObservable = pager.observable
            .cachedIn(viewModelScope)
    }

    override fun getFavoritePosts(): Single<List<FavoritesDbEntryPost>> =
        favoritePostsRepository.getAllFavoritePostsFromDb()

    fun getDefaultSubreddit() = subredditRepository.defaultSubreddit

    override fun cachedPagingObservable(): Observable<PagingData<Post>> = subredditPostsCachedPagingObservable
}