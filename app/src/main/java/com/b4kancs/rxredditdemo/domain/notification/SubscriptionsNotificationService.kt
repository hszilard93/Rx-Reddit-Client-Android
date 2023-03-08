package com.b4kancs.rxredditdemo.domain.notification

import android.app.job.JobParameters
import android.app.job.JobService
import logcat.LogPriority
import logcat.logcat


class SubscriptionsNotificationService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        logcat(LogPriority.INFO) { "onStartJob" }

        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        logcat(LogPriority.INFO) { "onStopJob" }

        // TODO: Reschedule job depending on success

        return false
    }
}