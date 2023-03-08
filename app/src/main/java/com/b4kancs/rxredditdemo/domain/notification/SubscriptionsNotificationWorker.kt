package com.b4kancs.rxredditdemo.domain.notification

import android.content.Context
import androidx.work.RxWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.reactivex.Single
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

// Notification scheduling logic:
// Upon opening the app, check if a notification is scheduled in the next (some interval depending on notification frequency)
// If there is one, reschedule the notification (by some time depending on notification frequency)
// Schedule notifications upon
//      a. first subscription to a user
//      b. just got notification permission
//      c. changed notification frequency preference in Settings

class SubscriptionsNotificationWorker(val context: Context, workerParams: WorkerParameters) : RxWorker(context, workerParams) {

    override fun createWork(): Single<Result> {
        logcat { "createWork" }

        val notificationManager: SubscriptionsNotificationManager by inject(SubscriptionsNotificationManager::class.java)
        notificationManager.showNotification(context, "TEST")
        return Single.just(Result.success())
    }
}