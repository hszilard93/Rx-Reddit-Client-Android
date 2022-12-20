package com.b4kancs.rxredditdemo.repository

import com.b4kancs.rxredditdemo.data.database.FollowsDatabase
import com.b4kancs.rxredditdemo.model.UserFeed
import io.reactivex.rxjava3.core.Completable
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
        val defaultUserFeed = UserFeed("AGGREGATE")
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
                    if (it.value == null) it.onNext(follows.isEmpty())
                    else if (it.value == false && follows.isNotEmpty()) it.onNext(true)
                    else if (it.value == true && follows.isEmpty()) it.onNext(false)
                }
            }
            .doOnError { e ->
                logcat(LogPriority.ERROR) { "Could not get followed users from DB! Message: ${e.message}" }
            }
    }

    fun addFollowedUserToDb(userFeed: UserFeed): Completable {
        logcat(LogPriority.INFO) { "addFollowedUserToDb: post = ${userFeed.name}" }
        return followsDatabase.followsDao().insertFollowedUser(userFeed)
            .subscribeOn(Schedulers.io())
            .retry(1)
    }

    fun removeFollowedUserFromDb(userFeed: UserFeed): Completable {
        logcat(LogPriority.INFO) { "removeFollowedUserFromDb: user = ${userFeed.name}" }
        return followsDatabase.followsDao().deleteFollowedUser(userFeed)
            .retry(1)
            .subscribeOn(Schedulers.io())
    }

    fun getUserFeedByName(userName: String): Single<UserFeed> {
        logcat { "getUserFeedByName: userName = $userName" }
        return Single.create { emitter ->
            getAllFollowsFromDb()
                .subscribeOn(Schedulers.io())
                .subscribeBy(
                    onSuccess = { feeds ->
                        val matchingFeed = feeds.firstOrNull { it.name == userName }
                        matchingFeed
                            ?.let { emitter.onSuccess(matchingFeed) }
                            ?: emitter.onSuccess(UserFeed(userName))
                    },
                    onError = { e ->
                        logcat(LogPriority.ERROR) { "Could not get follows from database! Message = ${e.message}" }
                        logcat(LogPriority.WARN) { "Returning new userFeed!" }
                        emitter.onSuccess(UserFeed(userName))
                    })
                .addTo(disposables)
        }

//        return getAllFollowsFromDb()
//            .subscribeOn(Schedulers.io())
//            .doOnError { e ->
//                logcat(LogPriority.ERROR) { "Could not get follows from database! Message = ${e.message}" }
//                logcat(LogPriority.WARN) { "Returning new userFeed!" }
//                Single.just(UserFeed(userName))
//            }
//            .flatMap { feeds ->
//                val matchingFeed = feeds.firstOrNull { it.name == userName }
//                if (matchingFeed != null)
//                    Single.just(matchingFeed)
//                else
//                    Single.just(UserFeed(userName))
//            }
    }
}