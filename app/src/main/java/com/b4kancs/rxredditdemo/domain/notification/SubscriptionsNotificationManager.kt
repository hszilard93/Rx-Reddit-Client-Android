package com.b4kancs.rxredditdemo.domain.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.FragmentActivity
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.tbruyelle.rxpermissions3.RxPermissions
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

object SubscriptionsNotificationManager {

    private const val CHANNEL_ID = "NOTIFICATIONS_SUBSCRIPTIONS"
    private const val NOTIFICATION_ID = 1

    private val applicationContext: Context by inject(Context::class.java)
    private val disposables = CompositeDisposable()


    fun showNotification(context: Context, message: String) {
        logcat { "showNotification" }

        val notificationManager = NotificationManagerCompat.from(context)
        // Create notification channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.notificationChannels.isNotEmpty())
                return

            val channelName = context.resources.getString(R.string.notification_channel_name_subscriptions)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gallery_256)
            .setContentTitle(applicationContext.getString(R.string.notification_channel_name_subscriptions))
            .setContentText(message)
            .setContentIntent(createNewSubscriptionsIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // Returns Maybe.empty if no permission is required; Maybe.success(false) if the permission has been denied;
    // and Maybe.just(true) if it has been granted.
    fun checkForAndAskNotificationPermissionIfNecessary(activity: FragmentActivity): Maybe<Boolean> {
        logcat { "checkForNotificationPermissionAndAskPermissionIfNecessary" }

        return Maybe.create { emitter ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                emitter.onComplete()
                // This return is here so that the linter recognizes the end of the execution path and doesn't complain about SDK checks,
                // but it also makes it easier for us to spot the return points so I don't mind.
                return@create
            }

            val permissionStatusCode = applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (permissionStatusCode == PackageManager.PERMISSION_GRANTED) {
                emitter.onSuccess(true)
                return@create
            }

            // Ask for permission here.
            askForNotificationPermission(activity)
                .subscribeBy(
                    onSuccess = { result -> emitter.onSuccess(result) },
                    onError = { e -> emitter.onError(e) }
                )
                .addTo(disposables)
        }
    }

    @SuppressLint("InlinedApi")
    private fun askForNotificationPermission(activity: FragmentActivity): Single<Boolean> {
        logcat { "askForNotificationPermission" }

        return Single.create { emitter ->
            val permissions = RxPermissions(activity)
            permissions.request(Manifest.permission.POST_NOTIFICATIONS)
                .doOnError { e ->
                    logcat(LogPriority.ERROR) { "Error while asking for post notification permission. Message: ${e.message}" }
                    emitter.onError(e)
                }
                .subscribe { isPermissionGranted ->
                    if (isPermissionGranted) {
                        logcat(LogPriority.INFO) { "Post notification permission has been granted." }
                        emitter.onSuccess(true)
                    }
                    else {
                        logcat(LogPriority.WARN) { "Post notification permission not granted." }
                        emitter.onSuccess(false)
                    }
                }.addTo(disposables)
        }
    }

    private fun createNewSubscriptionsIntent(): PendingIntent {
        logcat { "createNewSubscriptionsIntent" }
        val subscriptionIntent = Intent(applicationContext, MainActivity::class.java)
        subscriptionIntent.putExtra(MainActivity.INTENT_TYPE_EXTRA, MainActivity.INTENT_TYPE_SUBSCRIPTION)

        val stackBuilder = TaskStackBuilder.create(applicationContext)
        stackBuilder.addNextIntent(subscriptionIntent)
        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)!!
    }
}