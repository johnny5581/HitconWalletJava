package org.walleth.activities.hitcon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import org.walleth.R
import org.walleth.activities.trezor.TREZOR_REQUEST_CODE
import org.walleth.activities.trezor.TrezorSignTransactionActivity
import org.walleth.activities.trezor.startTrezorActivity
import org.walleth.data.AppDatabase

private const val ADDRESS_HEX_KEY = "badge_address_hex"
fun Intent.hasBadgeAddressResult() = hasExtra(ADDRESS_HEX_KEY)
fun Intent.getBadgeAddressResult() = getStringExtra(ADDRESS_HEX_KEY)


abstract class HitconBadgeActivity : AppCompatActivity() {

    private val appDatabase: AppDatabase by LazyKodein(appKodein).instance()
    protected val handler = Handler()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_badge)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    protected val mainRunnable: Runnable = object : Runnable {
        override fun run() {

        }
    }
}