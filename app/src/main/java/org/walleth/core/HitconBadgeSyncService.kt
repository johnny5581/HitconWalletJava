package org.walleth.core

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleService
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Intent
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.walleth.data.BadgeProvider

class HitconBadgeSyncService : LifecycleService() {
    private val lazyKodein = LazyKodein(appKodein)
    private val currentBadgeProvider: BadgeProvider by lazyKodein.instance()

    companion object {
        private var timing = 15_000 // in MilliSeconds
        private var last_run = 0L
        private var shortcut = false
    }

    class TimingModifyingLifecycleObserver : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun connectListener() {
            timing = 15_000
            shortcut = true
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun disconnectListener() {
            timing = 60_000
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        lifecycle.addObserver(HitconBadgeSyncService.TimingModifyingLifecycleObserver())

        launch {
            while (true) {
                HitconBadgeSyncService.last_run = System.currentTimeMillis()




                while ((HitconBadgeSyncService.last_run + HitconBadgeSyncService.timing) > System.currentTimeMillis() && !HitconBadgeSyncService.shortcut) {
                    delay(100)
                }
                HitconBadgeSyncService.shortcut = false
            }
        }

        return START_STICKY
    }
}
