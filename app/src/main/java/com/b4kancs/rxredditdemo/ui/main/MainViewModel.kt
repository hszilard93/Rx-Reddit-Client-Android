package com.b4kancs.rxredditdemo.ui.main

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import com.b4kancs.rxredditdemo.repository.SubredditRepository
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.observables.ConnectableObservable
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class MainViewModel : ViewModel() {
    var isActionBarShowing: Boolean = true
    var isNavBarShowing: Boolean = true

    init {
        logcat { "init" }
    }
}