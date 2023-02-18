package com.b4kancs.rxredditdemo.utils

import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Observable as V1Observable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.CompletableEmitter
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit

fun <T : Any> V1Observable<T>.toV3Observable(): io.reactivex.rxjava3.core.Observable<T> =
    RxJavaBridge.toV3Observable(this)

fun Boolean.toIntValue() = if (this) 1 else 0

fun CompletableEmitter.fromCompletable(completable: Completable) =
    completable.subscribeBy(
        onComplete = { this.onComplete() },
        onError = { e -> this.onError(e) }
    )

fun executeTimedDisposable(
    delayInMillis: Long,
    scheduler: Scheduler? = null,
    func: () -> Unit
) {
    val disposable = CompositeDisposable()
    Observable.timer(delayInMillis, TimeUnit.MILLISECONDS)
        .observeOn(scheduler ?: AndroidSchedulers.mainThread())
        .subscribe {
            func()
            disposable.dispose()
        }.addTo(disposable)
}
