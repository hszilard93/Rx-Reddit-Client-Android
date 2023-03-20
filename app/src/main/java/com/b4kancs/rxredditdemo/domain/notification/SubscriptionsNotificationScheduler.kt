package com.b4kancs.rxredditdemo.domain.notification

import android.content.Context
import androidx.work.*
import com.f2prateek.rx.preferences2.RxSharedPreferences
import logcat.LogPriority
import logcat.logcat
import java.util.concurrent.TimeUnit

// Notification scheduling logic:
// Upon opening the app, check if a notification is scheduled in the next (some interval depending on notification frequency).
// If there is one, reschedule the notification (by some time depending on notification frequency).
// Schedule notifications upon
//      a. first subscription to a user
//      b. just got notification permission
//      c. changed notification frequency preference in Settings
class SubscriptionsNotificationScheduler(
    val context: Context,
    private val rxPreferences: RxSharedPreferences,
    private val notificationManager: SubscriptionsNotificationManager

) {

    companion object {
        const val PERIODIC_WORK_REQUEST_NAME = "PERIODIC_SUBSCRIPTIONS_NOTIFICATION_WORK_REQUEST"
        const val IMMEDIATE_WORK_REQUEST_NAME = "IMMEDIATE_SUBSCRIPTIONS_NOTIFICATION_WORK_REQUEST"
    }

    private val hasNotificationPermission = notificationManager.checkHasNotificationPermission().blockingGet(true)

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
            logcat(LogPriority.INFO) { "A previously scheduled notification already exists. Returning." }
            return

            // I did not find a way to get the scheduled time out of the notification in order to delay it,
            // and I haven't figured out a sure-fire way to record the next scheduled notifications time,
            // as a Preference for example, either. TODO: Think harder (ง •̀_•́)ง
        }

        logcat { "Scheduling new notification." }
        scheduleNewNotification()
    }

    fun scheduleNewNotification() {
        logcat { "scheduleNewNotification" }

        if (!hasNotificationPermission) {
            // The user doesn't want our notifications.
            logcat(LogPriority.WARN) { "The app doesn't have notification permission. Returning." }
            return
        }

        val notificationPreferenceValue = rxPreferences.getString("pref_list_notifications").get()
        val workFrequency = when (notificationPreferenceValue) {
            "twice_day" -> 12L to TimeUnit.HOURS
            "once_day" -> 24L to TimeUnit.HOURS
            "twice_week" -> 3L to TimeUnit.DAYS
            "once_week" -> 6L to TimeUnit.DAYS
            "never" -> {
                logcat(LogPriority.INFO) { "Notification frequency preference value is 'never'. Returning." }
                return
            }  // The user doesn't want to hear from us. Oh well!
            else -> throw java.lang.IllegalStateException("Illegal notification frequency preference! value = $notificationPreferenceValue")
        }

        val workConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
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