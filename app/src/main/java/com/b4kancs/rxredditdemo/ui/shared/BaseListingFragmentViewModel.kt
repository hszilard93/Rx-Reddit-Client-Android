package com.b4kancs.rxredditdemo.ui.shared

import androidx.lifecycle.ViewModel
import androidx.paging.PagingData
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.PostsPropertiesRepository
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.logcat

abstract class BaseListingFragmentViewModel(
    private val rxSharedPreferences: RxSharedPreferences,
    private val postsPropertiesRepository: PostsPropertiesRepository,
    private val favoritePostsRepository: FavoritePostsRepository

) : ViewModel(), PostPagingDataObservableProvider {

    enum class UiState { NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT, NO_CONTENT_AGGREGATE }
    /*
      Legal UI states for
        HomeViewModel:      NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT
        FavoritesViewModel: NORMAL, LOADING, ERROR_GENERIC, NO_CONTENT
        FollowsViewModel:   NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT, NO_CONTENT_AGGREGATE
     */

    val uiStateBehaviorSubject: BehaviorSubject<UiState> = BehaviorSubject.createDefault(UiState.LOADING)
    private val refreshTriggerSubject: PublishSubject<Unit> = PublishSubject.create()
    val refreshTriggerObservable: Observable<Unit> = refreshTriggerSubject.hide()   // I should use this `backing subject` pattern more.
    val disposables = CompositeDisposable()
    var shouldBlurNsfwPosts = true
    var rvStoredPosition: Int = 0
        // We keep track of the position of the RecyclerView here for restoring it after a configuration change or to manipulate it
        // in other ways. It gets used after uiStateBehaviorSubject signals NORMAL state, for example.
        get() {
            logcat { "rvPosition.get" }
            return field
        }
        protected set(value) {
            logcat { "rvPosition.set: value = $value" }
            field = value
        }

    abstract val postsCachedPagingObservable: Observable<PagingData<Post>>

    init {
        uiStateBehaviorSubject
            .doOnNext { logcat { "uiStateBehaviorSubject.onNext: it = $it" } }
            .subscribe()
            .addTo(disposables)

        refreshTriggerSubject
            .doOnNext { logcat { "refreshTriggerSubject.onNext" } }
            .subscribe()
            .addTo(disposables)

        rxSharedPreferences.getBoolean("pref_switch_unblur_nsfw").asObservable().toV3Observable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { shouldBlurNsfwPosts = it.not() }
            .addTo(disposables)
    }

    fun getFavoritePosts(): Single<List<PostFavoritesDbEntry>> {
        logcat { "getFavoritePosts" }
        return favoritePostsRepository.getAllFavoritePostsFromDb()
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

    fun triggerRefreshFeed() {
        logcat { "refreshFeed" }
        rvStoredPosition = 0
        refreshTriggerSubject.onNext(Unit)
    }

    fun saveRvPosition(position: Int) {
        logcat { "saveRvPosition: position = $position" }
        rvStoredPosition = position
    }
}