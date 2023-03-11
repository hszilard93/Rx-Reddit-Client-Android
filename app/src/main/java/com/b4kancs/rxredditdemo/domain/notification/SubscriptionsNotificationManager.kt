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
import com.f2prateek.rx.preferences2.RxSharedPreferences
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

        if (checkHasNotificationPermission().blockingGet(true).not()) {
            logcat(LogPriority.INFO) { "Can't show notification: permission not granted!" }
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)

        // Create notification channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && notificationManager.notificationChannels.isEmpty()) {
            logcat(LogPriority.INFO) { "Creating new notification channel." }
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

        logcat(LogPriority.INFO) { "Posting notification with message: $message" }
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // Returns Maybe.empty if no permission is required; Maybe.success(false) if the permission has been denied;
    // and Maybe.just(true) if it has been granted.
    fun askNotificationPermissionIfNecessaryAndReturnPermissionStatus(activity: FragmentActivity): Maybe<Boolean> {
        logcat { "checkForNotificationPermissionAndAskPermissionIfNecessary" }

        return Maybe.create { emitter ->
            checkHasNotificationPermission()
                .subscribeBy(
                    onSuccess = { hasPermission ->
                        if (hasPermission) {
                            emitter.onSuccess(true)
                        }
                        else { // We need to ask for a permission.
                            askForNotificationPermission(activity)
                                .subscribeBy(
                                    onSuccess = { isPermissionGranted ->
                                        if (!isPermissionGranted) {
                                            val rxPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
                                            rxPreferences.getString("pref_list_notifications").set("never")
                                        }
                                        emitter.onSuccess(isPermissionGranted)
                                    },
                                    onError = { e -> emitter.onError(e) }
                                )
                                .addTo(disposables)
                        }
                    },
                    onComplete = { // No permission required.
                        emitter.onComplete()
                    },
                    onError = { e ->
                        logcat(LogPriority.ERROR) { "Error checking for notification permission. Message: ${e.message}" }
                        emitter.onError(e)
                    }
                )
                .addTo(disposables)
        }
    }

    fun checkHasNotificationPermission(): Maybe<Boolean> {
        logcat { "checkHasNotificationPermission" }

        return Maybe.create { emitter ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // We don't need permission, return empty Maybe.
                logcat { "Notification permission not required." }
                emitter.onComplete()
            }
            else {
                // We do need permission. Return whether or not we already have it.
                logcat { "Notification permission needed." }
                val permissionStatusCode = applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                val hasPermission = permissionStatusCode == PackageManager.PERMISSION_GRANTED
                if (hasPermission)
                    logcat(LogPriority.INFO) { "The app has notification permission." }
                else
                    logcat(LogPriority.WARN) { "The app doesn't have notification permission!" }

                emitter.onSuccess(hasPermission)
            }
        }
    }

    @SuppressLint("InlinedApi")
    fun askForNotificationPermission(activity: FragmentActivity): Single<Boolean> {
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