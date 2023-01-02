package com.b4kancs.rxredditdemo.repository

import com.b4kancs.rxredditdemo.data.database.FollowsDatabase
import com.b4kancs.rxredditdemo.model.UserFeed
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class FollowsRepository {

    companion object {
        val defaultUserFeed = UserFeed("All your follows", UserFeed.Status.AGGREGATE)
    }

    val followsChangedSubject: PublishSubject<Unit> = PublishSubject.create()
    val areThereFollowedUsersBehaviourSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()

    private val followsDatabase: FollowsDatabase by inject(FollowsDatabase::class.java)
    private val disposables = CompositeDisposable()

    fun getAllFollowsFromDb(): Single<List<UserFeed>> {
        logcat { "getAllFollowsFromDb" }
        return followsDatabase.followsDao().getFollowedUsers()
            .subscribeOn(Schedulers.io())
            .retry(1)
            .doOnSuccess { follows ->
                areThereFollowedUsersBehaviourSubject.let {
                    if (follows.isNotEmpty() != it.value) it.onNext(follows.isNotEmpty())
                }
            }
            .doOnError { e ->
                logcat(LogPriority.ERROR) { "Could not get followed users from DB! Message: ${e.message}" }
            }
    }

    fun addUserFeedToDb(userFeed: UserFeed): Completable {
        logcat(LogPriority.INFO) { "addFollowedUserToDb: post = ${userFeed.name}" }
        return followsDatabase.followsDao().insertFollowedUser(userFeed)
            .subscribeOn(Schedulers.io())
            .doOnComplete { followsChangedSubject.onNext(Unit) }
            .retry(1)
    }

    fun deleteUserFeedFromDb(userFeed: UserFeed): Completable {
        logcat(LogPriority.INFO) { "removeFollowedUserFromDb: user = ${userFeed.name}" }
        return followsDatabase.followsDao().deleteFollowedUser(userFeed)
            .retry(1)
            .doOnComplete { followsChangedSubject.onNext(Unit) }
            .subscribeOn(Schedulers.io())
    }

    fun getUserFeedFromDbByName(userName: String): Maybe<UserFeed> {
        logcat { "getUserFeedByName: userName = $userName" }
        return Maybe.create { emitter ->
            getAllFollowsFromDb()
                .subscribeOn(Schedulers.io())
                .subscribeBy(
                    onSuccess = { feeds ->
                        val matchingFeed = feeds.firstOrNull { it.name == userName }
                        matchingFeed
                            ?.let { emitter.onSuccess(matchingFeed) }
                            // If there was no matching UserFeed in the db.
                            ?: emitter.onComplete()
                    },
                    onError = { e ->
                        logcat(LogPriority.ERROR) { "Could not get follows from database! Message = ${e.message}" }
                        // If we encounter a db error.
                        emitter.onError(e)
                    })
                .addTo(disposables)
        }
    }

    fun getUserFeedFromDbByNameLike(query: String): Single<List<UserFeed>> {
        logcat { "getUserFeedFromDbByNameLike: query = $query" }
        return followsDatabase.followsDao().getFollowedUsersByNameLike(query)
    }
}