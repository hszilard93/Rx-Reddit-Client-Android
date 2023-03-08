package com.b4kancs.rxredditdemo.ui.follows

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.domain.notification.SubscriptionsNotificationManager
import com.b4kancs.rxredditdemo.domain.pagination.UserPostsJsonPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class FollowsViewModel : BaseListingFragmentViewModel() {

    private val followsRepository: FollowsRepository by inject(FollowsRepository::class.java)

    override val postsCachedPagingObservable: Observable<PagingData<Post>>
    val feedChangedBehaviorSubject: BehaviorSubject<UserFeed> = BehaviorSubject.create()
    val followsSearchResultsChangedSubject: PublishSubject<List<UserFeed>> = PublishSubject.create()
    val shouldAskNotificationPermissionPublishSubject: PublishSubject<Unit> = PublishSubject.create()

    val currentUserFeed: UserFeed
        get() = feedChangedBehaviorSubject
            .blockingMostRecent(FollowsRepository.aggregateUserFeed).first()
    private val disposables = CompositeDisposable()

    init {
        logcat { "init" }

        feedChangedBehaviorSubject
            .subscribe { feed ->
                logcat(LogPriority.INFO) { "feedChangedBehaviorSubject.onNext feed = ${feed.name}" }
                followsRepository.followsChangedSubject.onNext(Unit)
            }
            .addTo(disposables)

        val pager = Pager(
            PagingConfig(
                pageSize = UserPostsJsonPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = UserPostsJsonPagingSource.PAGE_SIZE
            )
        ) {
            logcat { "Follows pager pagingSourceFactory method called. currentUserFeed = $currentUserFeed" }
            UserPostsJsonPagingSource(currentUserFeed)
        }
        postsCachedPagingObservable = pager.observable
            .cachedIn(this.viewModelScope)
    }

    fun getAllUserFeeds(): Single<List<UserFeed>> {
        logcat { "getAllUserFeeds" }
        return followsRepository.getAllFollowsFromDb()
    }

    fun getAllSubscribedFeeds(): Single<List<UserFeed>> {
        logcat { "getAllSubscribedFeeds" }
        return followsRepository.getAllSubscribedFeeds()
    }

    fun getUserFeedByName(name: String): Maybe<UserFeed> {
        logcat { "getUserFeedByName: name = $name" }
        return followsRepository.getUserFeedFromDbByName(name)
    }

    fun addUserFeed(userFeed: UserFeed): Completable {
        logcat { "addUserFeed: userFeed = ${userFeed.name}" }
        val newFeed = UserFeed(userFeed.name, UserFeed.Status.FOLLOWED)
        return followsRepository.saveUserFeedToDb(newFeed)
    }

    fun deleteUserFeed(userFeed: UserFeed): Completable {
        logcat { "deleteUserFeed: userFeed = ${userFeed.name}" }
        return followsRepository.deleteUserFeedFromDb(userFeed)
    }

    fun subscribeToFeed(userFeed: UserFeed): Completable {
        logcat { "subscribeToFeed: userFeed = ${userFeed.name}" }

        val notificationManager: SubscriptionsNotificationManager by inject(SubscriptionsNotificationManager::class.java)
        val shouldAskForPermission = notificationManager.checkHasNotificationPermission().blockingGet(true).not()
        if (shouldAskForPermission) shouldAskNotificationPermissionPublishSubject.onNext(Unit)

        val newFeed = UserFeed(userFeed.name, UserFeed.Status.SUBSCRIBED)
        return followsRepository.saveUserFeedToDb(newFeed)
    }

    fun unsubscribeFromFeed(userFeed: UserFeed): Completable {
        logcat { "unsubscribeFromFeed: userFeed = ${userFeed.name}" }
        val newFeed = UserFeed(userFeed.name, UserFeed.Status.FOLLOWED)
        return followsRepository.saveUserFeedToDb(newFeed)
    }

    fun setUserFeedTo(userName: String): Completable {
        logcat { "setUserFeedTo: userName = $userName" }
        return Completable.create { emitter ->
            followsRepository.getUserFeedFromDbByName(userName)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { userFeed ->
                        setUserFeedTo(userFeed)
                            .subscribeBy(
                                onComplete = emitter::onComplete
                            ).addTo(disposables)
                    },
                    onComplete = {
                        setUserFeedTo(UserFeed(userName, UserFeed.Status.NOT_IN_DB))
                            .subscribeBy(
                                onComplete = emitter::onComplete,
                                onError = emitter::onError
                            ).addTo(disposables)
                    },
                    onError = { e ->
                        logcat(LogPriority.ERROR) { "Could not get user feed! Message: ${e.message}" }
                        emitter.onError(e)
                    }
                ).addTo(disposables)
        }
    }

    fun setUserFeedTo(userFeed: UserFeed): Completable {
        logcat { "setUserFeedTo: userFeed = $userFeed" }
        feedChangedBehaviorSubject.onNext(userFeed)
        return Completable.complete()
    }

    fun getUserFeedSearchResultsFromDb(query: String): Single<List<UserFeed>> {
        logcat { "getUserFeedSearchResultsFromDb: query = $query" }
        return if (query.isEmpty())
            Single.just(emptyList())
        else
            followsRepository.getUserFeedFromDbByNameLike(query)
    }

    fun getFollowsChangedSubject(): PublishSubject<Unit> =
        followsRepository.followsChangedSubject

    fun getAreThereFollowedUsersBehaviourSubject() =
        followsRepository.areThereFollowedUsersBehaviourSubject

    fun getAggregateUserFeed() = FollowsRepository.aggregateUserFeed

    fun getSubscriptionsUserFeed() = FollowsRepository.subscriptionsUserFeed

    override fun getCachedPagingObservable(): Observable<PagingData<Post>> =
        postsCachedPagingObservable

    override fun onCleared() {
        logcat { "onCleared" }
        disposables.clear()
        super.onCleared()
    }

    fun checkIsNotificationPermissionDenied(): Boolean {
        val notificationManager: SubscriptionsNotificationManager by inject(SubscriptionsNotificationManager::class.java)
        return notificationManager.checkHasNotificationPermission().blockingGet(true)
    }
}