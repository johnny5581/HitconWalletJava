package org.walleth.activities.hitcon

import android.os.Bundle
import org.walleth.R

class HitconBadgeGetAddressActivity : HitconBadgeActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        handler.post(mainRunnable)
    }
}
