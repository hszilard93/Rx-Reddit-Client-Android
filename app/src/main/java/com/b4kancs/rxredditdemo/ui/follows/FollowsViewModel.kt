package com.b4kancs.rxredditdemo.ui.follows

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.domain.notification.SubscriptionsNotificationManager
import com.b4kancs.rxredditdemo.domain.notification.SubscriptionsNotificationScheduler
import com.b4kancs.rxredditdemo.domain.pagination.FollowsPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import com.b4kancs.rxredditdemo.repository.PostsPropertiesRepository
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.LogPriority
import logcat.logcat

@OptIn(ExperimentalCoroutinesApi::class)
class FollowsViewModel(
    private val followsRepository: FollowsRepository,
    private val notificationManager: SubscriptionsNotificationManager,
    private val notificationScheduler: SubscriptionsNotificationScheduler,
    private val rxSharedPreferences: RxSharedPreferences,
    private val postsPropertiesRepository: PostsPropertiesRepository,
    private val favoritePostsRepository: FavoritePostsRepository,
    private val jsonService: RedditJsonService
) : BaseListingFragmentViewModel(rxSharedPreferences, postsPropertiesRepository, favoritePostsRepository) {

    override val postsCachedPagingObservable: Observable<PagingData<Post>>
    val currentFeedBehaviorSubject: BehaviorSubject<UserFeed> = BehaviorSubject.create()
    val followsSearchResultsChangedSubject: PublishSubject<List<UserFeed>> = PublishSubject.create()
    val shouldAskNotificationPermissionPublishSubject: PublishSubject<Unit> = PublishSubject.create()
    private var hasNotificationAccess: Boolean = false
        get() {
            logcat { "hasNotificationAccess get()" }
            // So that we don't always have to execute these calls once we have the permission.
            return if (!field) {
                field = notificationManager.checkHasNotificationPermission().blockingGet(true)
                field
            }
            else true
        }
    val currentUserFeed: UserFeed
        get() = currentFeedBehaviorSubject
            .blockingMostRecent(FollowsRepository.aggregateUserFeed).first()

    init {
        logcat { "init" }

        currentFeedBehaviorSubject
            .subscribe { feed ->
                logcat(LogPriority.INFO) { "feedChangedBehaviorSubject.onNext feed = ${feed.name}" }
                followsRepository.followsChangedSubject.onNext(Unit)
                rvPosition = 0
            }
            .addTo(disposables)

        val pager = Pager(
            PagingConfig(
                pageSize = FollowsPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = FollowsPagingSource.PAGE_SIZE
            )
        ) {
            logcat { "Follows pager pagingSourceFactory method called. currentUserFeed = $currentUserFeed" }
            FollowsPagingSource(currentUserFeed, jsonService, followsRepository)
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
            .doOnComplete {
                if (newFeed.name == currentUserFeed.name) currentFeedBehaviorSubject.onNext(newFeed)
            }
    }

    fun deleteUserFeed(userFeedToDelete: UserFeed): Completable {
        logcat { "deleteUserFeed: userFeed = ${userFeedToDelete.name}" }
        return followsRepository.deleteUserFeedFromDb(userFeedToDelete)
            .doOnComplete {
                if (userFeedToDelete.name == currentUserFeed.name) currentFeedBehaviorSubject.onNext(
                    UserFeed(userFeedToDelete.name, UserFeed.Status.NOT_IN_DB)
                )
            }
    }

    fun subscribeToFeed(userFeed: UserFeed): Completable {
        logcat { "subscribeToFeed: userFeed = ${userFeed.name}" }

        // If the user has not granted a notification permission, ask for one (if needed)
        val shouldAskForPermission = notificationManager.checkHasNotificationPermission().blockingGet(true).not()
        if (shouldAskForPermission) shouldAskNotificationPermissionPublishSubject.onNext(Unit)

        // Check if notifications have been scheduled already. If not, schedule notifications.
        notificationScheduler.checkForScheduledNotificationAndRescheduleIfMissingDelayElse()

        val newFeed = UserFeed(userFeed.name, UserFeed.Status.SUBSCRIBED)
        return followsRepository.saveUserFeedToDb(newFeed)
            .doOnComplete {
                if (newFeed.name == currentUserFeed.name) currentFeedBehaviorSubject.onNext(newFeed)
            }
    }

    fun unsubscribeFromFeed(userFeed: UserFeed): Completable {
        logcat { "unsubscribeFromFeed: userFeed = ${userFeed.name}" }
        val newFeed = UserFeed(userFeed.name, UserFeed.Status.FOLLOWED)
        return followsRepository.saveUserFeedToDb(newFeed)
            .doOnComplete {
                if (newFeed.name == currentUserFeed.name) currentFeedBehaviorSubject.onNext(newFeed)
            }
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
        currentFeedBehaviorSubject.onNext(userFeed)
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

    fun checkIsNotificationPermissionDenied(): Boolean = hasNotificationAccess
}