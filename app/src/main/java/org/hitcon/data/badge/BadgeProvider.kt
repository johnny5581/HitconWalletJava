package org.hitcon

import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.hitcon.data.badge.BadgeEntity
import org.hitcon.data.badge.BadgeServiceEntity
import org.hitcon.data.badge.getLastBadge
import org.hitcon.data.badge.upsertBadge
import org.hitcon.data.qrcode.InitializeContent
import org.hitcon.helper.toHex
import org.walleth.data.AppDatabase
import org.walleth.khex.hexToByteArray
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


fun Intent.hasTxn() = this.hasExtra(KeyTxn)
fun Intent.getTxn() = this.getStringExtra(KeyTxn)
const val KeyTxn = "Txn"
const val KeyMtu = "Mtu"

enum class HitconBadgeServices {
    Transaction,
    Txn,
    AddERC20,
    Balance,
    GeneralPurposeCmd,
    GeneralPurposeData
}

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

        val serviceNames = arrayOf(
                HitconBadgeServices.Transaction,
                HitconBadgeServices.Txn,
                HitconBadgeServices.AddERC20,
                HitconBadgeServices.Balance,
                HitconBadgeServices.GeneralPurposeCmd,
                HitconBadgeServices.GeneralPurposeData)
    }

    var entity: BadgeEntity? = null
    var device: BluetoothDevice? = null
    var scanning: Boolean = false
    val services: LinkedHashMap<HitconBadgeServices, BluetoothGattCharacteristic> = LinkedHashMap()
    var connected: Boolean = false
    //var initializeContent: InitializeContent? = null


    private var scanDeviceCallback: BadgeScanCallback? = null
    private var gattScanCallback: GattScanCallback? = null
    private val delayStopScanRunnable = Runnable { stopScanDevice(true) }

    private var gatt: BluetoothGatt? = null
    private var adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var mtu = 512
    private var iv: ByteArray = ByteArray(16)

    init {
        async(CommonPool) {
            entity = appDatabase.badges.getLastBadge()
        }
    }

    override fun handleMessage(msg: Message?) {
        Log.d("Badge", "Receive message: ${msg?.what}")
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

    private fun getServiceEntityList(service: String) : List<BadgeServiceEntity> {
        val list = ArrayList<BadgeServiceEntity>()
        for(name in serviceNames)
            list.add(BadgeServiceEntity(service, name.name))
        return list
    }

    private class BadgeScanCallback(val badgeProvider: BadgeProvider, val badgeCallback: BadgeCallback?) : BluetoothAdapter.LeScanCallback {
        override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            Log.d("Badge", "Found device: ${device!!.address}")
            var uuids = parseUUIDList(scanRecord!!)
            if (uuids.size> 0 && uuids.first().toString() == badgeProvider.entity?.identify) {
            //if (uuids.size> 0 ) {
                badgeProvider.device = device
                badgeCallback?.onDeviceFound(device!!)
                badgeProvider.sendEmptyMessage(MessageStopScanDevices)
                badgeProvider.sendEmptyMessage(MessageStartScanGattService)
            }
            else
                Log.d("Badge", "...Not badge")

        }

        private fun parseUUIDList(bytes: ByteArray): ArrayList<UUID> {
            val list = ArrayList<UUID>()
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
                    val tmp = mtu / 2
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
                    if (service.uuid.toString() == badgeProvider.entity?.identify) {
                        for(ch in service.characteristics) {
                            var name = badgeProvider.entity?.getUuidName(ch.uuid)
                            if (name != null)
                                badgeProvider.services[name] = ch
                        }

                        badgeProvider.saveEntity()
                        badgeCallback?.onServiceDiscover()
                    }
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
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean {
        gatt?.setCharacteristicNotification(characteristic, true)
        val desc = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt?.writeDescriptor(desc) ?: false
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

    fun saveEntity(){
//        val service = initializeContent!!.service
//        val address = initializeContent!!.address
//        val list = ArrayList<BadgeServiceEntity>()
//        for(kv in services) {
//            list.add(BadgeServiceEntity(service, kv.key.name, kv.value.uuid))
//        }
//        entity = BadgeEntity(service, address, services = list)
        async(CommonPool) {
            appDatabase.badges.upsertBadge(entity!!)
        }
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

    fun startTransaction(transaction: org.kethereum.model.Transaction) {
        val hvalue = BigDecimal(transaction.value).toHex()
        val hgaslimit = transaction.gasLimit.toBigDecimal().toHex()
        val hgas = BigDecimal(transaction.gasPrice).toHex()
        val hnoice = transaction.nonce?.toBigDecimal()?.toHex()
        val hdata = transaction.txHash
        val transArray = "01"+String.format("%02X", transaction.to.toString().length/2)+transaction.to.toString() +
                "02"+String.format("%02X", hvalue.length/2)+hvalue+
                "03"+String.format("%02X", hgas.length/2)+hgas+
                "04"+String.format("%02X", hgaslimit.length/2)+hgaslimit+
                "05"+String.format("%02X", hnoice!!.length/2)+hnoice+
                "06"+String.format("%02X", hdata!!.length/2)+hdata

        SecureRandom(iv)
        val aeskey = entity!!.key!!.hexToByteArray()
        val ptext = transArray.hexToByteArray()
        val enc = (iv.toHex() + encrypt(iv, aeskey!!, ptext).toHex()).hexToByteArray()
        val cha = services[HitconBadgeServices.Transaction]
        cha?.value = enc
        gatt?.setCharacteristicNotification(cha!!, true)
        enableNotifications(services[HitconBadgeServices.Txn]!!)
    }




    private fun BigDecimal.toHex() : String {
        var result = String.format("%X", this.toBigInteger())
        if(result.length%2 == 1)
            result = "0$result"
        return result
    }



    fun startConnectGatt(leScanCallback: BadgeCallback? = null) {
        if(connected) return
        gattScanCallback = GattScanCallback(this, leScanCallback)
        device?.connectGatt(context, false, gattScanCallback)
    }

    fun initializeBadge(init: InitializeContent, leScanCallback: BadgeCallback? = null) {
        //this.initializeContent = initializeContent
        entity = BadgeEntity(init.service, init.address, key=init.key, services = getServiceEntityList(init.service))
        stopScanDevice(false)
        startScanDevice(leScanCallback)
    }



    private fun getDecryptText(cyber: ByteArray) : String {
        return decrypt(iv, entity!!.key!!.toByteArray(), cyber).toString(Charset.defaultCharset())
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


