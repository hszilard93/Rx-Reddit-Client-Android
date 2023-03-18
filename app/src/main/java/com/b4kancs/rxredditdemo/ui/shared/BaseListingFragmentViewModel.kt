package com.b4kancs.rxredditdemo.ui.shared

import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.PostsPropertiesRepository
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import logcat.logcat
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject

abstract class BaseListingFragmentViewModel : ViewModel(), PostPagingDataObservableProvider {

    enum class UiState { NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT, NO_CONTENT_AGGREGATE }
    /*
      Legal UI states for
        HomeViewModel:      NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT
        FavoritesViewModel: NORMAL, LOADING, ERROR_GENERIC, NO_CONTENT
        FollowsViewModel:   NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT, NO_CONTENT_AGGREGATE
     */

    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val postsPropertiesRepository: PostsPropertiesRepository by inject(PostsPropertiesRepository::class.java)
    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    val uiStateBehaviorSubject: BehaviorSubject<UiState> = BehaviorSubject.createDefault(UiState.LOADING)
    val disposables = CompositeDisposable()
    private var _savedPosition: Int? = null
    val savedPosition get() = _savedPosition
    var shouldBlurNsfwPosts = true

    abstract val postsCachedPagingObservable: Observable<PagingData<Post>>

    init {
        rxSharedPreferences.getBoolean("pref_switch_unblur_nsfw").asObservable().toV3Observable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { shouldBlurNsfwPosts = it.not() }
            .addTo(disposables)
    }

    fun getFavoritePosts(): Single<List<PostFavoritesDbEntry>> {
        logcat { "getFavoritePosts" }
        return favoritePostsRepository.getAllFavoritePostsFromDb()
    }

    fun updateSavedPosition(newPos: Int) {
        logcat { "updateSavedPosition: newPos = $newPos" }
        _savedPosition = newPos
    }

    fun shouldBlurThisPost(post: Post): Boolean {
        logcat { "shouldBlurThisPost: post = ${post.name}" }
        if (!shouldBlurNsfwPosts) return false

        return post.nsfw && !postsPropertiesRepository.isPostInDontBlurThesePostsSet(post)
    }

    fun dontBlurThisPostAnymore(post: Post) {
        logcat { "dontBlurThisPostAnymore: post = ${post.name}" }
        postsPropertiesRepository.addPostToDontBlurThesePostsSet(post)
    }
}