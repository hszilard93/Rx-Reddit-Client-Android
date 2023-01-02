package com.b4kancs.rxredditdemo.ui.follows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.domain.pagination.UserPostsJsonPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class FollowsViewModel : ViewModel(), PostPagingDataObservableProvider {

    val postsCachedPagingObservable: Observable<PagingData<Post>>
    val feedChangedBehaviorSubject: BehaviorSubject<UserFeed> = BehaviorSubject.create()

    private val followsRepository: FollowsRepository by inject(FollowsRepository::class.java)
    private val currentUserFeed: UserFeed get() = feedChangedBehaviorSubject
        .blockingMostRecent(FollowsRepository.defaultUserFeed).first()
    private val disposables = CompositeDisposable()

    init {
        logcat { "init" }

        val pager = Pager(
            PagingConfig(
                pageSize = UserPostsJsonPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = UserPostsJsonPagingSource.PAGE_SIZE
            )
        ) { UserPostsJsonPagingSource(currentUserFeed) }
        postsCachedPagingObservable = pager.observable
            .cachedIn(this.viewModelScope)
    }

    fun addUserToFollowedUsers(userFeed: UserFeed): Completable =
        followsRepository.addUserFeedToDb(userFeed)

    fun removeUserFromFollowedUsers(userFeed: UserFeed): Completable =
        followsRepository.deleteUserFeedFromDb(userFeed)

    fun getAreThereFollowedUsersBehaviourSubject() =
        followsRepository.areThereFollowedUsersBehaviourSubject

    fun setUserFeedTo(userName: String): Completable {
        logcat { "setUserFeedTo: userName = $userName" }
        return Completable.create { emitter ->
            followsRepository.getUserFeedFromDbByName(userName)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { userFeed ->
                        feedChangedBehaviorSubject.onNext(userFeed)
                        emitter.onComplete()
                    },
                    onComplete = {
                        feedChangedBehaviorSubject.onNext(UserFeed(userName, UserFeed.Status.NOT_IN_DB))
                        emitter.onComplete()
                    },
                    onError = { e ->
                        logcat(LogPriority.ERROR) { "Could not get user feed! Message: ${e.message}" }
                        emitter.onError(e)
                    }
                ).addTo(disposables)
        }
    }

    override fun cachedPagingObservable(): Observable<PagingData<Post>> =
        postsCachedPagingObservable
}