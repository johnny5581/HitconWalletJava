package org.walleth.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import org.hitcon.BadgeProvider
import org.walleth.R
import org.walleth.activities.qrscan.startScanActivityForResult
import org.walleth.data.addressbook.AddressBookEntry

class SwitchAccountActivity : BaseAddressBookActivity() {
    private val badgeProvider: BadgeProvider  by LazyKodein(appKodein).instance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.subtitle = getString(R.string.nav_drawer_accounts)
    }

    override fun onAddressClick(addressEntry: AddressBookEntry) {
        if (addressEntry.hitconBadgeFlag) {
            AlertDialog.Builder(this)
                    .setTitle(addressEntry.name)
                    .setMessage("Rescan badge ?")
                    .setPositiveButton("Yes, Rescan") { _, _ ->
                        startScanActivityForResult(this)
                    }
                    .setNegativeButton("No, Just select") { _, _ ->
                        setResult(Activity.RESULT_OK, Intent().apply { putExtra("HEX", addressEntry.address.hex) })
                        finish()
                    }
                    .create().show()

        } else {
            currentAddressProvider.setCurrentAccount(addressEntry)
            finish()
        }

    }
}
