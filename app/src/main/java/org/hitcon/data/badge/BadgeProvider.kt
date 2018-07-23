package org.hitcon

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.hitcon.activities.KeyTransaction
import org.hitcon.data.badge.BadgeEntity
import org.hitcon.data.badge.BadgeServiceEntity
import org.hitcon.data.badge.getLastBadge
import org.hitcon.data.badge.upsertBadge
import org.hitcon.data.qrcode.InitializeContent
import org.hitcon.helper.toHex
import org.kethereum.model.Transaction
import org.walleth.data.AppDatabase
import org.walleth.data.tokens.Token
import org.walleth.functions.toHexString
import org.walleth.kethereum.android.TransactionParcel
import org.walleth.khex.clean0xPrefix
import org.walleth.khex.hexToByteArray
import org.walleth.khex.toHexString
import java.lang.reflect.InvocationTargetException
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
fun String.padZero() = if(this.length % 2 == 0) this else "0$this"
fun Double.toByteArray() = ByteBuffer.allocate(8).putDouble(this).array().reversed()
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
        const val TAG = "HitconBadge"
        const val InitializeBadgeProvider = 0
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


    private var scanDeviceCallback: BadgeScanCallback? = null
    private var scanDeviceCallback2: BadgeScanCallbackNew? = null
    private var gattScanCallback: GattScanCallback? = null
    private val delayStopScanRunnable = Runnable { stopScanDevice(true) }

    private var gatt: BluetoothGatt? = null
    private var adapter: BluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var leScanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
    private var mtu = 512
    private val delay = 15 * 1000L
    private var iv: ByteArray = ByteArray(16)
    private var transaction:Transaction? = null
    private var transacting = transaction != null

    init {
        async(UI) {
            async(CommonPool) {
                entity = appDatabase.badges.getLastBadge()
            }.await()
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
                transaction = null
                val txn  = getDecryptText(msg.data.getByteArray(KeyTxn))
                val transaction = TransactionParcel(Transaction())
                context.sendBroadcast(Intent().apply {
                    action = ActionReceiveTxn
                    putExtra(KeyTransaction, transaction)
                })
            }
            MessageGattConnectionChanged -> {
            }
            MessageStartScanGattService -> {
                device?.let {
                    startConnectGatt(it)
                }
            }
        }

    }


    interface BadgeCallback {
        fun onDeviceFound(device: BluetoothDevice)
        fun onTimeout()
        fun onServiceDiscover()
        fun onMtuChanged()
    }

    private fun getServiceEntityList(init: InitializeContent): List<BadgeServiceEntity> {
        val list = ArrayList<BadgeServiceEntity>()
        for (name in serviceNames)
            list.add(BadgeServiceEntity(init.service, name.name, init.getUUID(name)))
        return list
    }

    /**
     * Android Version < 21 Adapter Scanner
     */
    private class BadgeScanCallback(val badgeProvider: BadgeProvider, val badgeCallback: BadgeCallback?) : BluetoothAdapter.LeScanCallback {
        override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            Log.d(TAG, "onLeScan: ${device?.toString()}")
            val uuids = parseUUIDList(scanRecord!!)
            if (uuids.size > 0 && uuids.any { u -> u.toString() == badgeProvider.entity?.identify }) {
                badgeProvider.device = device
                device?.let { badgeCallback?.onDeviceFound(it) }
                badgeProvider.sendEmptyMessage(MessageStopScanDevices)
//                badgeProvider.sendEmptyMessage(MessageStartScanGattService)
            }
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

    /***
     * Android Version > 21 LeScanner Callback
     */
    private class BadgeScanCallbackNew(val badgeProvider: BadgeProvider, val badgeCallback: BadgeCallback?) : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "onBadgeScanResult: ${result?.scanRecord?.toString()}")
            val uuids = result?.scanRecord?.serviceUuids
            val device = result?.device
            uuids?.let {
                if (it.size > 0 && it.any { u -> u.toString() == badgeProvider.entity?.identify }) {
                    badgeProvider.device = device
                    device?.let { badgeCallback?.onDeviceFound(it) }
                    badgeProvider.sendEmptyMessage(MessageStopScanDevices)
//                    badgeProvider.sendEmptyMessage(MessageStartScanGattService)
                }
            }

        }
    }

    /**
     * Gatt callback
     */
    private class GattScanCallback(val badgeProvider: BadgeProvider, val badgeCallback: BadgeCallback?) : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.i(TAG, "onConnectionStateChange: $status -> $newState")
            badgeProvider.connected = newState == BluetoothGatt.STATE_CONNECTED
            badgeProvider.sendEmptyMessage(MessageGattConnectionChanged)
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    Log.d(TAG, "start discoverService")
                    gatt?.discoverServices()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i(TAG, "onMtuChanged, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.d(TAG, "mtu change to $mtu")
                    badgeProvider.mtu = mtu
                    badgeCallback?.onMtuChanged()
                }
                BluetoothGatt.GATT_FAILURE -> {
                    Log.e(TAG, "mtu fail: $mtu")
                    val tmp = mtu / 2
                    if (tmp < 128)
                        badgeProvider.sendMessage(Message().apply {
                            what = MessageMtuFailure
                            data = Bundle().apply { putInt(KeyMtu, mtu) }
                        })
                    else {
                        Log.e(TAG, "reset mtu to $tmp")
                        gatt?.requestMtu(tmp)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.i(TAG, "onServicesDiscovered, success: ${status == BluetoothGatt.GATT_SUCCESS}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                badgeProvider.services.clear()
                Log.d(TAG, "pair service: ${badgeProvider.entity?.identify}")
                for (service in gatt!!.services) {
                    Log.d(TAG, "matching service: ${service.uuid}")
                    if (service.uuid.toString() == badgeProvider.entity?.identify) {
                        for (ch in service.characteristics) {
                            Log.d(TAG, "matching characteristic: ${ch.uuid}")
                            var name = badgeProvider.entity?.getUuidName(ch.uuid)
                            if (name != null) {
                                Log.d(TAG, "find! ${name.name}")
                                badgeProvider.services[name] = ch
                            }
                        }
                    }
                }
                badgeCallback?.onServiceDiscover()
                Log.d(TAG, "start change mtu: ${badgeProvider.mtu}")
                gatt?.requestMtu(badgeProvider.mtu)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if(characteristic?.uuid == badgeProvider.services[HitconBadgeServices.Txn]?.uuid)
                gatt?.readCharacteristic(characteristic)
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
        Log.d(TAG, "enableNotification: ${characteristic.uuid}")
        gatt?.setCharacteristicNotification(characteristic, true)
        val desc = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gatt?.writeDescriptor(desc) ?: false
    }

    /**
     * Stat scan device, use initialize content
     */
    fun startScanDevice(badgeCallback: BadgeCallback? = null) {
        Log.i(TAG, "... startScanDevice .... ")
        if (scanning) stopScanDevice()
        scanning = true
        if (leScanner == null) {
            scanDeviceCallback = BadgeScanCallback(this, badgeCallback)
            adapter.startLeScan(scanDeviceCallback)
        } else {
            scanDeviceCallback2 = BadgeScanCallbackNew(this, badgeCallback)
            leScanner!!.startScan(scanDeviceCallback2)
        }
        postDelayed(delayStopScanRunnable, delay)
    }

    fun startConnectGatt(device: BluetoothDevice, leScanCallback: BadgeCallback? = null) {
        Log.i(TAG, "... startConnectGatt .... ")
        if (connected) {
            Log.d(TAG, "Gatt is connected, disconnect first")
            gatt?.disconnect()
        }
        if (gatt == null) {
            Log.d(TAG, "no gatt instance, connect create")
            gattScanCallback = GattScanCallback(this, leScanCallback)
            gatt = device?.connectGatt(context, false, gattScanCallback)
        } else {
            Log.d(TAG, "gatt instance exist, reconnect")
        }
        gatt?.disconnect()
        gatt?.connect()
        //gatt?.let { refreshGatt(it) }
    }

    /**
     * Stop scan, if timeout then call callback
     */
    private fun stopScanDevice(timeout: Boolean = false) {
        if (scanning) {
            Log.i(TAG, "stopScanDevice, timeout flag: $timeout")
            if (timeout) {
                scanDeviceCallback?.badgeCallback?.onTimeout()
                scanDeviceCallback2?.badgeCallback?.onTimeout()
            } else {
                removeCallbacks(delayStopScanRunnable)
            }

            scanDeviceCallback?.let { adapter.stopLeScan(it) }
            scanDeviceCallback2?.let { leScanner?.stopScan(it) }
            scanning = false
        }
    }

    fun saveEntity() {
        entity?.let {
            async(UI) {
                async(CommonPool) {
                    appDatabase.badges.upsertBadge(it)
                }.await()
            }
        }
    }

    fun refreshGatt(gatt: BluetoothGatt) {
        if (gatt != null) {
            //add_info("Connected!\n");
            try {
                val method = gatt.javaClass.getMethod("refresh")
                method?.invoke(gatt)
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: InvocationTargetException) {
                e.printStackTrace()
            }

        }
    }


    fun startTransaction(transaction: org.kethereum.model.Transaction) {
        this.transaction = transaction
        val haddress = transaction.to.toString().clean0xPrefix()
        val hvalue = transaction.value.toHexString().clean0xPrefix().padZero()
        val hgaslimit = transaction.gasLimit.toHexString().clean0xPrefix().padZero()
        val hgas = transaction.gasPrice.toHexString().clean0xPrefix().padZero()
        val hnoice = transaction.nonce!!.toHexString().clean0xPrefix().padZero()
        val hdata =  transaction.input.toHexString().clean0xPrefix()
        var transArray =
                        "01" + String.format("%02X", haddress.length / 2) + haddress +
                        "02" + String.format("%02X", hvalue.length / 2) + hvalue +
                        "03" + String.format("%02X", hgas.length / 2) + hgas +
                        "04" + String.format("%02X", hgaslimit.length / 2) + hgaslimit +
                        "05" + String.format("%02X", hnoice!!.length / 2) + hnoice +
                        "06" + String.format("%02X", hdata!!.length / 2) + hdata

        SecureRandom(iv)
        val aeskey = entity!!.key!!.hexToByteArray()
        val ptext = transArray.hexToByteArray()
        val enc = (iv.toHex() + encrypt(iv, aeskey!!, ptext).toHex()).hexToByteArray()
        val cha = services[HitconBadgeServices.Transaction]
        cha?.value = enc
        gatt?.writeCharacteristic(cha)
        gatt?.setCharacteristicNotification(cha!!, true)
        enableNotifications(services[HitconBadgeServices.Txn]!!)
    }

    fun startUpdateBalance(token: Token, balance:String) {
        if(entity == null || !connected || transacting) return

        val haddress = token.address.cleanHex
        val balanceValue = balance.toBigDecimal().scaleByPowerOfTen(-token.decimals).toDouble()
        //val balanceValue = 100.0
        val hvalue = balanceValue.toByteArray().toHexString().clean0xPrefix()
        var transArray =
                        "01" + String.format("%02X", haddress.length / 2) + haddress +
                        "02" + String.format("%02X", hvalue.length / 2) + hvalue

        SecureRandom(iv)
        val aeskey = entity!!.key!!.hexToByteArray()
        val ptext = transArray.hexToByteArray()
        val enc = (iv.toHex() + encrypt(iv, aeskey!!, ptext).toHex()).hexToByteArray()
        val cha = services[HitconBadgeServices.Balance]
        cha?.value = enc
        gatt?.writeCharacteristic(cha)

    }


    private fun BigDecimal.toHex(): String {
        var result = String.format("%X", this.toBigInteger())
        if (result.length % 2 == 1)
            result = "0$result"
        return result
    }


    fun initializeBadge(init: InitializeContent, leScanCallback: BadgeCallback? = null) {
        entity = BadgeEntity(init.service, null, init.address, key = init.key, services = getServiceEntityList(init))
        saveEntity()
        startScanDevice(leScanCallback)
    }


    private fun getDecryptText(cyber: ByteArray): String {
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


