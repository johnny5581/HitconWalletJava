package org.walleth.core

import android.arch.lifecycle.LifecycleService
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein

class HitconBadgeService : LifecycleService() {
    private val lazyKodein = LazyKodein(appKodein)
}
