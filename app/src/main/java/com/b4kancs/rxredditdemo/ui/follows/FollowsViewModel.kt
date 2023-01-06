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
import com.b4kancs.rxredditdemo.utils.fromCompletable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.observables.ConnectableObservable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class FollowsViewModel : ViewModel(), PostPagingDataObservableProvider {

    private val followsRepository: FollowsRepository by inject(FollowsRepository::class.java)

    val postsCachedPagingObservable: Observable<PagingData<Post>>
    val feedChangedBehaviorSubject: BehaviorSubject<UserFeed> = BehaviorSubject.create()
    val selectedUserFeedReplayObservable: ConnectableObservable<UserFeed> = feedChangedBehaviorSubject.replay(1)
    val followsSearchResultsChangedSubject: PublishSubject<List<UserFeed>> = PublishSubject.create()


    val currentUserFeed: UserFeed
        get() = feedChangedBehaviorSubject
            .blockingMostRecent(FollowsRepository.defaultUserFeed).first()
    private val disposables = CompositeDisposable()

    init {
        logcat { "init" }

        selectedUserFeedReplayObservable.connect()
        feedChangedBehaviorSubject.doOnNext { logcat(LogPriority.INFO) { "selectedUserFeedChangedSubject.onNext" } }

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


    fun getAllUserFeeds(): Single<List<UserFeed>> =
        followsRepository.getAllFollowsFromDb()

    fun getUserFeedByName(name: String): Maybe<UserFeed> =
        followsRepository.getUserFeedFromDbByName(name)

    fun saveUserFeed(userFeed: UserFeed): Completable =
        followsRepository.addUserFeedToDb(userFeed)
            .doOnComplete {
                val newFeed = UserFeed(userFeed.name, UserFeed.Status.FOLLOWED)
                feedChangedBehaviorSubject.onNext(newFeed)
            }

    fun deleteUserFeed(userFeed: UserFeed): Completable =
        followsRepository.deleteUserFeedFromDb(userFeed)
            .doOnComplete {
                val newFeed = UserFeed(userFeed.name, UserFeed.Status.NOT_IN_DB)
                feedChangedBehaviorSubject.onNext(newFeed)
            }

    fun subscribeToFeed(userFeed: UserFeed): Completable {
        val newFeed = UserFeed(userFeed.name, UserFeed.Status.SUBSCRIBED)
        return saveUserFeed(newFeed)
            .doOnComplete { feedChangedBehaviorSubject.onNext(newFeed) }
    }

    fun unsubscribeFromFeed(userFeed: UserFeed): Completable {
        val newFeed = UserFeed(userFeed.name, UserFeed.Status.FOLLOWED)
        return saveUserFeed(newFeed)
            .doOnComplete { feedChangedBehaviorSubject.onNext(newFeed) }
    }

    fun getAreThereFollowedUsersBehaviourSubject() =
        followsRepository.areThereFollowedUsersBehaviourSubject

    fun setUserFeedTo(userName: String): Completable {
        logcat { "setUserFeedTo: userName = $userName" }
        return Completable.create { emitter ->
            followsRepository.getUserFeedFromDbByName(userName)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { userFeed ->
                        emitter.fromCompletable(setUserFeedTo(userFeed))
                    },
                    onComplete = {
                        emitter.fromCompletable(
                            setUserFeedTo(UserFeed(userName, UserFeed.Status.NOT_IN_DB))
                        )
                    },
                    onError = { e ->
                        logcat(LogPriority.ERROR) { "Could not get user feed! Message: ${e.message}" }
                        emitter.onError(e)
                    }
                ).addTo(disposables)
        }
    }

    fun setUserFeedTo(userFeed: UserFeed): Completable {
        feedChangedBehaviorSubject.onNext(userFeed)
        return Completable.complete()
    }

    fun getUserFeedSearchResultsFromDb(query: String): Observable<List<UserFeed>> {
        logcat { "getUserFeedSearchResultsFromDb: query = $query" }
        if (query.isEmpty()) return Observable.just(emptyList())

        return followsRepository.getUserFeedFromDbByNameLike(query)
            .toObservable()
    }

    fun getFollowsChangedSubject(): PublishSubject<Unit> =
        followsRepository.followsChangedSubject

    fun getDefaultUserFeed() = FollowsRepository.defaultUserFeed

    override fun cachedPagingObservable(): Observable<PagingData<Post>> =
        postsCachedPagingObservable
}