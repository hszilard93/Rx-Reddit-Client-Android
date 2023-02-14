package com.b4kancs.rxredditdemo.ui.shared

import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import logcat.logcat
import org.koin.java.KoinJavaComponent

abstract class BaseListingFragmentViewModel : ViewModel(), PostPagingDataObservableProvider {

    enum class UiState { NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT, NO_CONTENT_AGGREGATE }
    /*
      Legal UI states for
        HomeViewModel:      NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT
        FavoritesViewModel: NORMAL, LOADING, ERROR_GENERIC, NO_CONTENT
        FollowsViewModel:   NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT, NO_CONTENT_AGGREGATE
     */

    val uiStateBehaviorSubject: BehaviorSubject<UiState> = BehaviorSubject.createDefault(UiState.LOADING)
    var _savedPosition: Int? = null
    val savedPosition get() = _savedPosition

    abstract val postsCachedPagingObservable: Observable<PagingData<Post>>

    private val favoritePostsRepository: FavoritePostsRepository by KoinJavaComponent.inject(FavoritePostsRepository::class.java)

    fun getFavoritePosts(): Single<List<PostFavoritesDbEntry>> {
        logcat { "getFavoritePosts" }
        return favoritePostsRepository.getAllFavoritePostsFromDb()
    }

    fun updateSavedPosition(newPos: Int) {
        logcat { "updateSavedPosition: newPos = $newPos" }
        _savedPosition = newPos
    }
}