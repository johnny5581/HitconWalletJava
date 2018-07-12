package org.hitcon

import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.hitcon.data.badge.BadgeEntity
import org.hitcon.data.badge.getLastBadge
import org.hitcon.data.qrcode.HitconBadgeServices
import org.hitcon.data.qrcode.InitializeContent
import org.walleth.data.AppDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


fun Intent.hasTxn() = this.hasExtra(KeyTxn)
fun Intent.getTxn() = this.getStringExtra(KeyTxn)
const val KeyTxn = "Txn"
const val KeyMtu = "Mtu"
/**
 * Hitcon Badge Service Provider
 */
class BadgeProvider(private val context: Context, private val appDatabase: AppDatabase) : Handler() {
    companion object {
        const val MessageReceiveTxn = 1
        const val ActionReceiveTxn = "Action_ReceiveTxn"


        const val MessageGattConnectionChanged = 2

        const val MessageMtuFailure = 3


        const val MessageStopScanDevices = 4

        const val MessageStartScanGattService = 5

    }

    var entity: BadgeEntity? = null
    var device: BluetoothDevice? = null
    var scanning: Boolean = false
    val services: LinkedHashMap<HitconBadgeServices, BluetoothGattCharacteristic> = LinkedHashMap()
    var connected: Boolean = false
    var initializeContent: InitializeContent? = null


    private var scanDeviceCallback: BadgeScanCallback? = null
    private var gattScanCallback: GattScanCallback? = null
    private val delayStopScanRunnable = Runnable { stopScanDevice(true) }

    private var gatt: BluetoothGatt? = null
    private var adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var mtu = 512
    private val iv: ByteArray = ByteArray(16)

    init {
        async(CommonPool) {
            entity = appDatabase.badges.getLastBadge()
        }
    }

    override fun handleMessage(msg: Message?) {
        when (msg?.what) {
            MessageStopScanDevices -> {
                stopScanDevice()
            }
            MessageMtuFailure -> {
            }
            MessageReceiveTxn -> {
                context.sendBroadcast(Intent().apply {
                    action = ActionReceiveTxn
                    putExtra(KeyTxn, getDecryptText(msg.data.getByteArray(KeyTxn)))
                })
            }
            MessageGattConnectionChanged -> {
            }
            MessageStartScanGattService-> {
                device?.run {
                    startConnectGatt()
                }
            }
        }

    }


    interface BadgeCallback {
        fun onDeviceFound(device: BluetoothDevice)
        fun onTimeout()
        fun onServiceDiscover()
    }


    private class BadgeScanCallback(val badgeProvider: BadgeProvider, val badgeCallback: BadgeCallback?) : BluetoothAdapter.LeScanCallback {
        override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            var uuids = parseUUIDList(scanRecord!!)
            if (uuids.size> 0 && uuids.first().toString() == badgeProvider.initializeContent?.service) {
                badgeProvider.device = device
                badgeCallback?.onDeviceFound(device!!)
                badgeProvider.sendEmptyMessage(MessageStopScanDevices)
                badgeProvider.sendEmptyMessage(MessageStartScanGattService)
            }
        }

        private fun parseUUIDList(bytes: ByteArray): ArrayList<UUID> {
            var list = ArrayList<UUID>()
            var offset = 0
            while (offset < bytes.size - 2) {
                var len = bytes[offset++].toInt()
                if (len == 0)
                    break

                val type = bytes[offset++].toInt()
                when (type) {
                    0x02, 0x03 ->
                        while (len > 1) {
                            var uuid16 = bytes[offset++].toInt()
                            uuid16 += bytes[offset++].toInt() shl 8
                            len -= 2
                            list.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)))
                        }
                    0x06, 0x07 ->
                        while (len >= 16) {
                            try {
                                val buffer = ByteBuffer.wrap(bytes, offset++, 16).order(ByteOrder.LITTLE_ENDIAN)
                                val mostSignificantBit = buffer.getLong()
                                val leastSignificantBit = buffer.getLong()
                                list.add(UUID(leastSignificantBit, mostSignificantBit))
                            } catch (e: IndexOutOfBoundsException) {
                                continue
                            } finally {
                                offset += 15
                                len -= 16
                            }
                        }
                    else -> offset += len - 1
                }
            }
            return list
        }
    }

    private class GattScanCallback(val badgeProvider: BadgeProvider, val badgeCallback: BadgeCallback?) : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            badgeProvider.connected = newState == BluetoothGatt.STATE_CONNECTED
            badgeProvider.sendEmptyMessage(MessageGattConnectionChanged)
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    badgeProvider.gatt = gatt
                    gatt?.requestMtu(badgeProvider.mtu)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    badgeProvider.mtu = mtu
                    gatt?.discoverServices()
                }
                BluetoothGatt.GATT_FAILURE -> {
                    var tmp = mtu / 2
                    if (tmp < 128)
                        badgeProvider.sendMessage(Message().apply {
                            what = MessageMtuFailure
                            data = Bundle().apply { putInt(KeyMtu, mtu) }
                        })
                    else
                        gatt?.requestMtu(tmp)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                badgeProvider.services.clear()
                for(service in gatt?.services!!) {
                    var uuidstr = service.uuid.toString()
//                    if (uuidstr == badgeProvider.initializeContent?.service) {
//                        service.characteristics.forEach {
//                            var name = badgeProvider.initializeContent?.getUuidName(it.uuid)
//                            if (name != null)
//                                badgeProvider.services[name] = it
//                        }
//                        badgeCallback?.onServiceDiscover()
//                    }
                    for(ch in service.characteristics) {
                        var chstr = ch.uuid.toString()
                        var name = badgeProvider.initializeContent?.getUuidName(ch.uuid)
                            if (name != null)
                                badgeProvider.services[name] = ch
                    }
                    badgeCallback?.onServiceDiscover()
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic?.uuid == badgeProvider.services[HitconBadgeServices.Txn]?.uuid) {
                badgeProvider.sendMessage(Message().apply {
                    what = MessageReceiveTxn
                    data = Bundle().apply { putByteArray(KeyTxn, characteristic?.value) }
                })
            }

        }


    }

    /**
     * Enable notification
     */
    fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean {
        gatt?.setCharacteristicNotification(characteristic, true)
        var desc = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt?.let { it.writeDescriptor(desc) } ?: false
    }

    /**
     * Stat scan device, use initialize content
     */
    fun startScanDevice(onDeviceFound: BadgeCallback? = null) {
        if (scanning) stopScanDevice()
        scanDeviceCallback = BadgeScanCallback(this, onDeviceFound)
        adapter.startLeScan(scanDeviceCallback)
        postDelayed(delayStopScanRunnable, 10 * 1000)
    }


    /**
     * Stop scan, if timeout then call callback
     */
    private fun stopScanDevice(timeout: Boolean = false) {
        if (scanning && scanDeviceCallback != null) {
            if (timeout) {
                scanDeviceCallback!!.badgeCallback?.onTimeout()
            } else {
                removeCallbacks(delayStopScanRunnable)
            }
            adapter.stopLeScan(scanDeviceCallback)
            scanning = false
            scanDeviceCallback = null
        }
    }

    fun startConnectGatt(leScanCallback: BadgeCallback? = null) {
        if(connected) return
        gattScanCallback = GattScanCallback(this, leScanCallback)
        device?.connectGatt(context, false, gattScanCallback)
    }

    fun initializeBadge(initializeContent: InitializeContent, leScanCallback: BadgeCallback? = null) {
        this.initializeContent = initializeContent
        startScanDevice(leScanCallback)
    }



    private fun getDecryptText(cyber: ByteArray) : String {
        return decrypt(iv, initializeContent!!.key.toByteArray(), cyber).toString(Charset.defaultCharset())
    }


    private fun encrypt(iv: ByteArray, key: ByteArray, text: ByteArray): ByteArray {
        return try {
            val algParamSpec = IvParameterSpec(iv)
            val secretKeySpe = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.ENCRYPT_MODE, secretKeySpe, algParamSpec)
            }
            cipher.doFinal(text)
        } catch (ex: Exception) {
            ByteArray(0)
        }

    }

    private fun decrypt(iv: ByteArray, key: ByteArray, cyber: ByteArray): ByteArray {
        return try {
            val algParamSpec = IvParameterSpec(iv)
            val secretKeySpe = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, secretKeySpe, algParamSpec)
            }
            cipher.doFinal(cyber)
        } catch (ex: Exception) {
            ByteArray(0)
        }

    }
}


