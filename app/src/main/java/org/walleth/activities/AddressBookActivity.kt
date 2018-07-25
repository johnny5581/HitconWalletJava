package org.walleth.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import kotlinx.android.synthetic.main.transaction_item.*
import org.hitcon.BadgeProvider
import org.hitcon.activities.*
import org.hitcon.activities.HitconBadgeActivity.Companion.REQUEST_BADGE_INITIALIZE
import org.hitcon.data.qrcode.isHitconQrCodeUri
import org.hitcon.data.qrcode.toHitconQrCode
import org.walleth.R
import org.walleth.activities.qrscan.REQUEST_CODE
import org.walleth.activities.qrscan.startScanActivityForResult
import org.walleth.activities.trezor.getAddressResult
import org.walleth.data.addressbook.AddressBookEntry

open class AddressBookActivity : BaseAddressBookActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.subtitle = getString(R.string.address_book_subtitle)

        if (intent.hasHitconQrCode()) {
            onFabClick()
        }
    }

    override fun onFabClick() {
        var i = Intent(baseContext, CreateAccountActivity::class.java)
        if (intent.hasHitconQrCode())
            i.putExtra(KeyHitconQrCode, intent.getHitconQrCode())
        startActivity(i)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_CODE -> {
                data?.getStringExtra("SCAN_RESULT")?.let {
                    if(it.isHitconQrCodeUri()) {
                        val code = it.toHitconQrCode()
                        if(code.valid)
                            startBadgeActivityForInitialize(this@AddressBookActivity, code)
                    }
                }
            }
            REQUEST_BADGE_INITIALIZE -> {
                setResult(Activity.RESULT_OK, Intent().apply { putExtra("HEX", data?.getAddressResult()) })
                finish()
            }

        }
    }

    override fun onAddressClick(addressEntry: AddressBookEntry) {
        setResult(Activity.RESULT_OK, Intent().apply { putExtra("HEX", addressEntry.address.hex) })
        finish()
    }

}
