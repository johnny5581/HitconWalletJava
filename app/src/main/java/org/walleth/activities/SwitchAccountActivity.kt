package org.walleth.activities

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import kotlinx.android.synthetic.main.transaction_item.*
import org.hitcon.BadgeProvider
import org.hitcon.activities.HitconBadgeActivity.Companion.REQUEST_BADGE_CONNECT
import org.hitcon.activities.HitconBadgeActivity.Companion.REQUEST_BADGE_INITIALIZE
import org.hitcon.activities.getBadgeAddress
import org.hitcon.activities.getHitconQrCode
import org.hitcon.activities.startBadgeActivityForConnection
import org.hitcon.activities.startBadgeActivityForInitialize
import org.hitcon.data.qrcode.*
import org.walleth.R
import org.walleth.activities.qrscan.REQUEST_CODE
import org.walleth.activities.qrscan.startScanActivityForResult
import org.walleth.data.addressbook.AddressBookEntry

class SwitchAccountActivity : BaseAddressBookActivity() {


    private val badgeProvider: BadgeProvider  by LazyKodein(appKodein).instance()
    private var entry: AddressBookEntry? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.subtitle = getString(R.string.nav_drawer_accounts)
    }

    override fun onAddressClick(addressEntry: AddressBookEntry) {
        if (addressEntry.hitconBadgeFlag) {
            entry = addressEntry
            AlertDialog.Builder(this)
                    .setTitle(addressEntry.name)
                    .setMessage("Rescan badge ?")
                    .setPositiveButton("Yes, Rescan") { _, _ ->
                        startScanActivityForResult(this)
                    }
                    .setNeutralButton("No, Just select") { _, _ ->
                        startBadgeActivityForConnection(this)
                    }
                    .create().show()

        } else {
            badgeProvider.disconnectBadge()
            currentAddressProvider.setCurrentAccount(addressEntry)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return

        if (requestCode == REQUEST_CODE) {
            val uri = data?.getStringExtra("SCAN_RESULT")
            if(uri != null && uri!!.isHitconQrCodeUri()) {
                val code = uri.toHitconQrCode()
                if (code?.type == HitconQrCodeType.Initialize)
                    startBadgeActivityForInitialize(this, code)
            }
        } else {
            val address = data?.getBadgeAddress()
            if (address?.toLowerCase() != entry?.address?.cleanHex?.toLowerCase())
                AlertDialog.Builder(this)
                        .setMessage("not same badge")
                        .create().show()
            else {
                currentAddressProvider.setCurrentAccount(entry!!)
                finish()
            }
        }
    }
}
