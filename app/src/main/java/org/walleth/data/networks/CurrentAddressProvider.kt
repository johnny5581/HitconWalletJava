package org.walleth.data.networks

import android.arch.lifecycle.MutableLiveData
import org.ethereum.geth.Account
import org.ethereum.geth.Accounts
import org.kethereum.model.Address
import org.walleth.data.addressbook.AddressBookEntry
import org.walleth.data.config.Settings

open class CurrentAddressProvider(val settings: Settings) : MutableLiveData<Address>() {

    fun setCurrent(value: Address) {
        settings.accountAddress = value.hex
        settings.badgeFlag = false
        setValue(value)
    }

    fun setCurrentAccount(value: AddressBookEntry) {
        settings.accountAddress = value.address.hex
        settings.badgeFlag = value.hitconBadgeFlag
        setValue(value.address)

    }

    fun getCurrent() = value!!
}