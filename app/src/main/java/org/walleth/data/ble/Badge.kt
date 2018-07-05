package org.walleth.data.ble

import android.bluetooth.BluetoothDevice
import java.util.*

const val SERVICE_TRANSACTION = "Transaction"
const val SERVICE_TXN = "Txn"
const val SERVICE_ADD_ERC20 = "AddERC20"
const val SERVICE_BALANCE = "Balance"
const val SERVICE_GENERAL_PURPOSE_CMD = "Cmd"
const val SERVICE_GENERAL_PURPOSE_DATA = "Data"

val BadgeServiceNames = arrayOf(
        SERVICE_TRANSACTION,
        SERVICE_TXN,
        SERVICE_ADD_ERC20,
        SERVICE_BALANCE,
        SERVICE_GENERAL_PURPOSE_CMD,
        SERVICE_GENERAL_PURPOSE_DATA
)

enum class BadgeState {
    INIT,
    DISCONNECTED,
    CONNECTED,
    BUSY
}


class Badge {
    constructor(device: BluetoothDevice) {
        setBluetoothDevice(device)
    }

    var services = createBadgeServices()
    var state = BadgeState.INIT
    var address: String = ""
    var mDevice: BluetoothDevice? = null

    fun setBluetoothDevice(device: BluetoothDevice) {
        address = device.address
    }

    fun setBadgeService(name: String, uuid: UUID?) {
        services.get(BadgeServiceNames.indexOf(name)).service_uuid = uuid
    }

    private fun createBadgeServices(): ArrayList<BadgeService> {
        var array = arrayListOf<BadgeService>()
        for(name in BadgeServiceNames)
            array.add(BadgeService(service_name = name, badge_address = address, service_uuid = null ))
        return array
    }

}