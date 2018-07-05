package org.walleth.data


import android.arch.lifecycle.MutableLiveData
import android.bluetooth.*
import android.content.Context
import org.ligi.tracedroid.logging.Log
import org.walleth.data.ble.Badge
import org.walleth.data.ble.BadgeState
import java.util.*

class BadgeProvider(context: Context, appDatabase: AppDatabase) : MutableLiveData<Badge>() {
    private var badge:Badge? = null
    private var mGatt: BluetoothGatt? = null
    private val database: AppDatabase = appDatabase
    private val mContext: Context = context

    fun setBadge(device: BluetoothDevice) {
        badge = Badge(device)
        super.setValue(badge)
        mGatt = badge?.mDevice?.let { it.connectGatt(mContext, false, gattCallback) }
    }

    fun isBadgeReady(): Boolean {
        return mGatt != null && value != null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    for (service in gatt!!.services) {
                        for (characteristic in service.characteristics) {

                        }
                    }
                }
                else -> Log.e("GATT service discover status: $status")
            }
            value?.state = BadgeState.CONNECTED
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            return when (status) {
                BluetoothGatt.GATT_SUCCESS -> Log.d("MTU change to $mtu success.")
                else -> Log.e("MTU change to $mtu fail.")
            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (status) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt!!.requestMtu(512)
                    gatt!!.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    value?.state = BadgeState.DISCONNECTED
                }
                else -> Log.d("Connect State Changed: $status")
            }

        }
    }


}
