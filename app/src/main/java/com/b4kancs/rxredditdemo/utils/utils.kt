package com.b4kancs.rxredditdemo.utils

import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Observable
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.CompletableEmitter
import io.reactivex.rxjava3.kotlin.subscribeBy

fun <T : Any> Observable<T>.toV3Observable(): io.reactivex.rxjava3.core.Observable<T> =
    RxJavaBridge.toV3Observable(this)

fun Boolean.toIntValue() = if (this) 1 else 0

fun CompletableEmitter.fromCompletable(completable: Completable) =
    completable.subscribeBy(
        onComplete = { this.onComplete() },
        onError = { e -> this.onError(e) }
    )

