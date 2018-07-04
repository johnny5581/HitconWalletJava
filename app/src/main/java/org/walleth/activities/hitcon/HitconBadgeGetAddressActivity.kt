package org.walleth.activities.hitcon

import android.os.Bundle
import org.walleth.R

class HitconBadgeGetAddressActivity : HitconBadgeActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setSubtitle(R.string.badge_title)
    }

    override fun onResume() {
        super.onResume()
        handler.post(mainRunnable)
    }
}
