package com.b4kancs.rxredditdemo.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.utils.getPreference
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import logcat.logcat
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat() {

    private val rxPreferences: RxSharedPreferences by inject()

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

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        logcat { "onCreatePreferences" }
        setPreferencesFromResource(R.xml.preferences, null)
    }
}