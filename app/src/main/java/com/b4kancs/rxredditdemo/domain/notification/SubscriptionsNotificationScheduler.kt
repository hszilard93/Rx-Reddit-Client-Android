package com.b4kancs.rxredditdemo.domain.notification

import android.content.Context
import androidx.work.*
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.disposables.CompositeDisposable
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class SubscriptionsNotificationScheduler(val context: Context) {

    companion object {
        const val PERIODIC_WORK_REQUEST_NAME = "PERIODIC_SUBSCRIPTIONS_NOTIFICATION_WORK_REQUEST"
        const val IMMEDIATE_WORK_REQUEST_NAME = "IMMEDIATE_SUBSCRIPTIONS_NOTIFICATION_WORK_REQUEST"
    }

    private val rxPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val disposables = CompositeDisposable()
    private val notificationManager: SubscriptionsNotificationManager by inject(SubscriptionsNotificationManager::class.java)
    private val hasNotificationPermission = notificationManager.checkHasNotificationPermission().blockingGet(false)

    fun scheduleImmediateNotification() {   // For testing purposes.
        logcat { "scheduleImmediateNotification" }
        val workRequest = OneTimeWorkRequestBuilder<SubscriptionsNotificationWorker>()
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_REQUEST_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun checkForScheduledNotificationAndRescheduleIfMissingDelayElse() {
        logcat { "scheduleNotificationIfNecessary" }

        val workManager = WorkManager.getInstance(context)
        val scheduledWorkList = workManager.getWorkInfosForUniqueWork(PERIODIC_WORK_REQUEST_NAME).get()
        if (scheduledWorkList.isNotEmpty()) {
            // A notification is already scheduled.
            return
        }

        scheduleNewNotification()
    }

    fun scheduleNewNotification() {
        logcat { "scheduleNewNotification" }

        if (!hasNotificationPermission) {
            // The user doesn't want our notifications.
            return
        }

        val notificationPreferenceValue = rxPreferences.getString("pref_list_notifications").get()
        val workFrequency = when (notificationPreferenceValue) {
            "twice_day" -> 12L to TimeUnit.HOURS
            "once_day" -> 24L to TimeUnit.HOURS
            "twice_week" -> 3L to TimeUnit.DAYS
            "once_week" -> 6L to TimeUnit.DAYS
            "never" -> return  // The user doesn't want to hear from us. Oh well!
            else -> throw java.lang.IllegalStateException("Illegal value as notification preference! value = $notificationPreferenceValue")
        }

        val workConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SubscriptionsNotificationWorker>(workFrequency.first, workFrequency.second)
            .setConstraints(workConstraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                1L,
                TimeUnit.HOURS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_REQUEST_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }
}