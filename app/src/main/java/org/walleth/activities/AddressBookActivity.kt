package org.walleth.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import org.hitcon.activities.KeyHitconQrCode
import org.hitcon.activities.getHitconQrCode
import org.hitcon.activities.hasHitconQrCode
import org.walleth.R
import org.walleth.data.addressbook.AddressBookEntry

open class AddressBookActivity : BaseAddressBookActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.subtitle = getString(R.string.address_book_subtitle)

        if(intent.hasHitconQrCode()) {
            onFabClick()
        }
    }

    override fun onFabClick() {
        var i = Intent(baseContext, CreateAccountActivity::class.java)
        if(intent.hasHitconQrCode())
            i.putExtra(KeyHitconQrCode, intent.getHitconQrCode())
        startActivity(i)
    }



    override fun onAddressClick(addressEntry: AddressBookEntry) {
        setResult(Activity.RESULT_OK, Intent().apply { putExtra("HEX", addressEntry.address.hex) })
        finish()
    }

}
