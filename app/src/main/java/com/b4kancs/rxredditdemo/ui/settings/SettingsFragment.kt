package com.b4kancs.rxredditdemo.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.domain.notification.SubscriptionsNotificationManager
import com.b4kancs.rxredditdemo.domain.notification.SubscriptionsNotificationScheduler
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.b4kancs.rxredditdemo.utils.getPreference
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import logcat.logcat
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat() {

    private val rxPreferences: RxSharedPreferences by inject()
    private val disposables = CompositeDisposable()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logcat { "onViewCreated" }

        StringBuilder().let { builder ->
            builder.append("Current preferences:\n")
            rxPreferences.getPreference().all.forEach { entry ->
                builder.append("\t${entry.key} -> ${entry.value}\n")
            }
            logcat { builder.toString() }
        }

        (activity as MainActivity).apply {
            // Without waiting for the activity's binding to 'get ready', the app crashes when the theme preference is changed.
            var bindingCheckerDisposable: Disposable? = null
            bindingCheckerDisposable = Observable.interval(0, 100, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter { isBindingAvailable() }
                .take(1)
                .subscribe {
                    disableMenu()
                    animateHideBottomNavBar()
                    lockDrawerClosed()
                    bindingCheckerDisposable?.dispose()
                }
        }

        handleNotificationFrequencyPreferenceChanges()

        super.onViewCreated(view, savedInstanceState)
    }

    private fun handleNotificationFrequencyPreferenceChanges() {
        logcat { "handleNotificationFrequencyPreferenceChanges" }

        rxPreferences.getString("pref_list_notifications").asObservable().toV3Observable()
            .distinctUntilChanged()
            .doOnNext { logcat { "Notification frequency preference value changed! New value = $it" } }
            .subscribe { preferenceValue ->
                if (preferenceValue == "never")
                    return@subscribe

                val notificationManager: SubscriptionsNotificationManager by inject()
                val notificationScheduler: SubscriptionsNotificationScheduler by inject()
                notificationManager.askNotificationPermissionIfNecessaryAndReturnPermissionStatus(requireActivity())
                    .subscribeBy(
                        onSuccess = { isPermissionGranted ->
                            if (isPermissionGranted) {
                                notificationScheduler.scheduleNewNotification()
                            }
                            else {
                                // This provides visual feedback to the user that the notifications aren't active.

                                rxPreferences.getString("pref_list_notifications").set("never")
                            }
                        },
                        onComplete = {
                            // We don't need a permission.
                            notificationScheduler.scheduleNewNotification()
                        },
                        onError = {
                            // Show a snack message.
                            makeSnackBar(
                                this.listView,
                                R.string.common_error_something_went_wrong,
                                type = SnackType.ERROR
                            ).show()
                        }
                    ).addTo(disposables)
            }.addTo(disposables)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        logcat { "onCreatePreferences" }
        setPreferencesFromResource(R.xml.preferences, null)
    }
}