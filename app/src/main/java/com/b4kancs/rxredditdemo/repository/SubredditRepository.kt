package com.b4kancs.rxredditdemo.repository

import com.b4kancs.rxredditdemo.data.database.SubredditDatabase
import com.b4kancs.rxredditdemo.data.networking.RedditJsonClient
import com.b4kancs.rxredditdemo.model.Subreddit
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class SubredditRepository {

    companion object {
        private const val DEFAULT_SUBREDDIT_PREFERENCE_KEY = "default_subreddit"
        private const val DEFAULT_SUBREDDIT_PREFERENCE_DEFAULT_VALUE = "user/kjoneslol/m/sfwpornnetwork"
    }

    val subredditsChangedSubject: PublishSubject<Unit> = PublishSubject.create()
    var defaultSubreddit = Subreddit("SFWPornNetwork", "user/kjoneslol/m/sfwpornnetwork", Subreddit.Status.FAVORITED)

    private val subredditDatabase: SubredditDatabase by inject(SubredditDatabase::class.java)
    private val redditJsonClient: RedditJsonClient by inject(RedditJsonClient::class.java)
    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val disposables = CompositeDisposable()

    fun getAllSubredditsFromDb(): Single<List<Subreddit>> {
        logcat { "getAllSubredditsFromDb" }
        return subredditDatabase.subredditDao().getSubreddits()
            .subscribeOn(Schedulers.io())
            .retry(2)
            .doOnSuccess { subs -> logcat { "${subs.size} subreddits loaded from DB." } }
    }

    fun getSubredditFromDbByAddress(address: String): Maybe<Subreddit> {
        logcat { "getSubredditFromDbByName: name = $address" }
        return subredditDatabase.subredditDao()
            .getSubredditByAddress(address)
            .subscribeOn(Schedulers.io())
    }

    fun getSubredditsFromDbByNameLike(keyword: String): Single<List<Subreddit>> {
        logcat { "getSubredditsFromDbByNameLike: keyword = $keyword" }
        return subredditDatabase.subredditDao()
            .getSubredditsByNameLike("%${keyword}%")
            .subscribeOn(Schedulers.io())
    }

    fun getSubredditsFromNetworkByNameLike(keyword: String): Single<List<Subreddit>> {
        logcat { "getSubredditsFromNetworkByNameLike: keyword = $keyword" }
        return redditJsonClient.getSubredditsByKeyword(keyword)
            .subscribeOn(Schedulers.io())
            .retry(2)
    }

    fun saveSubredditToDb(subreddit: Subreddit): Completable {
        return Completable.create { emitter ->
            subredditDatabase.subredditDao().insertSubreddit(subreddit)
                .subscribeOn(Schedulers.io())
                .retry(2)
                .subscribeBy(
                    onComplete = {
                        logcat(LogPriority.INFO) { "Subreddit ${subreddit.name} inserted into the database." }
                        subredditsChangedSubject.onNext(Unit)
                        emitter.onComplete()
                    },
                    onError = { e ->
                        logcat(LogPriority.ERROR) { "Unable to save subreddit ${subreddit.name} to db. Message: ${e.message}" }
                        emitter.onError(e)
                    }
                )
                .addTo(disposables)
        }
    }

    fun deleteSubredditFromDb(subreddit: Subreddit): Completable {
        logcat { "removeSubredditFromDb: subreddit = $subreddit" }
        return subredditDatabase.subredditDao().deleteSubreddit(subreddit)
            .subscribeOn(Schedulers.io())
            .doOnComplete {
                logcat(LogPriority.INFO) { "Subreddit removed from DB." }
                subredditsChangedSubject.onNext(Unit)
            }
            .doOnError { e -> logcat(LogPriority.ERROR) { "Couldn't remove subreddit ${subreddit.name} from DB. Message: ${e.message}" } }
    }

    fun setAsDefaultSubreddit(subreddit: Subreddit): Completable {
        logcat { "setAsDefaultSubreddit: subreddit = $subreddit" }

        val newSub = Subreddit(subreddit.name, subreddit.address, Subreddit.Status.FAVORITED, subreddit.nsfw)
        try {
            rxSharedPreferences
                .getString(DEFAULT_SUBREDDIT_PREFERENCE_KEY)
                .set(newSub.address)
            defaultSubreddit = newSub
            logcat(LogPriority.INFO) { "${newSub.address} is made the default subreddit." }
            subredditsChangedSubject.onNext(Unit)
        } catch (e: Exception) {
            // I am not sure what error could occur here, but let's be safe!
            logcat(LogPriority.ERROR) {
                "Failed to save subreddit ${subreddit.address} as default subreddit to the shared preferences. Message: ${e.message} "
            }
            return Completable.error(e)
        }

        // Next, we save/update the new default sub's data to the DB.
        // Every result is a good result, since we have already saved the preference, and that's what matters primarily.
        return Completable.create { emitter ->
            saveSubredditToDb(newSub)
                .subscribeBy(
                    onComplete = { emitter.onComplete() },
                    onError = { _ -> emitter.onComplete() } // This outcome will not cause any significant problems, so we still complete.
                ).addTo(disposables)
        }
    }

    fun loadDefaultSubreddit(): Completable {
        logcat { "loadDefaultSubreddit" }
        rxSharedPreferences
            .getString(DEFAULT_SUBREDDIT_PREFERENCE_KEY)
            .takeIf { it.get().isNotEmpty() }
            ?.let { preference ->
                return Completable.create { emitter ->
                    subredditDatabase.subredditDao()
                        .getSubredditByAddress(preference.get())
                        .subscribeOn(Schedulers.io())
                        .subscribeBy(
                            onSuccess = { subreddit ->
                                defaultSubreddit = subreddit
                                logcat(LogPriority.INFO) { "Default subreddit loaded: ${subreddit.name}" }
                                emitter.onComplete()
                            },
                            onComplete = {
                                logcat(LogPriority.WARN) { "Did not find subreddit ${preference.get()} in DB." }
                                emitter.onComplete()
                            },
                            onError = { e ->
                                logcat(LogPriority.ERROR) {
                                    "Could not load default subreddit ${preference.get()} from DB. Message: ${e.message}"
                                }
                                emitter.onComplete()
                            }
                        ).addTo(disposables)
                }
            }
            ?: run {
                logcat(LogPriority.INFO) { "No default subreddit preference detected." }
                return Completable.complete()
            }
    }
}