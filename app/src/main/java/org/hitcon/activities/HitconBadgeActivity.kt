package org.hitcon.activities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.hitcon.BadgeProvider
import org.hitcon.data.qrcode.HitconQrCode
import org.hitcon.data.qrcode.InitializeContent
import org.hitcon.getTxn
import org.json.JSONException
import org.json.JSONObject
import org.kethereum.model.Transaction
import org.ligi.kaxt.letIf
import org.walleth.BuildConfig
import org.walleth.R
import org.walleth.data.AppDatabase
import org.walleth.data.networks.NetworkDefinition
import org.walleth.data.networks.NetworkDefinitionProvider
import org.walleth.data.transactions.TransactionEntity
import org.walleth.data.transactions.TransactionState
import org.walleth.data.transactions.toEntity
import org.walleth.kethereum.android.TransactionParcel
import java.io.IOException
import java.security.cert.CertPathValidatorException

fun Intent.hasHitconQrCode() = this.hasExtra(HitconBadgeActivity.KeyInitQrCode)
fun Intent.getHitconQrCode() = this.getParcelableExtra<HitconQrCode>(HitconBadgeActivity.KeyInitQrCode)!!
fun Intent.hasBadgeAddress() = this.hasExtra(HitconBadgeActivity.KeyAddress)
fun Intent.getBadgeAddress() = this.getStringExtra(HitconBadgeActivity.KeyAddress)
fun Intent.hasTransaction() = this.hasExtra(HitconBadgeActivity.KeyTransaction)
fun Intent.getTransaction() = this.getParcelableExtra<TransactionParcel>(HitconBadgeActivity.KeyTransaction)
fun Activity.startBadgeActivityForInitialize(context: Context, code: HitconQrCode) = startActivityForResult(Intent(context, HitconBadgeActivity::class.java).apply { putExtra(HitconBadgeActivity.KeyInitQrCode, code) }, HitconBadgeActivity.REQUEST_BADGE_INITIALIZE)
fun Activity.startBadgeActivityForConnection(context: Context) = startActivityForResult(Intent(context, HitconBadgeActivity::class.java), HitconBadgeActivity.REQUEST_BADGE_CONNECT)
fun Activity.startBadgeActivityForTransfer(context: Context, transfer: TransactionParcel) = startActivityForResult(Intent(context, HitconBadgeActivity::class.java).apply { putExtra(HitconBadgeActivity.KeyTransaction, transfer) }, HitconBadgeActivity.REQUEST_BADGE_TRANSFER)
class HitconBadgeActivity : AppCompatActivity() {
    companion object {
        const val TAG = "HitconBadgeActivity"
        const val MessageReceiveTxn = 0
        const val MessageRestartProcess = 1
        const val KeyTransaction = "transfer"
        const val KeyAddress = "address"
        const val KeyInitQrCode = "init_qr_code"
        const val REQUEST_PERMISSION = 999
        const val REQUEST_BADGE_INITIALIZE = 9901
        const val REQUEST_BADGE_CONNECT = 9902
        const val REQUEST_BADGE_TRANSFER = 9903
    }

    private val lazyKodein = LazyKodein(appKodein)
    private val badgeProvider: BadgeProvider by lazyKodein.instance()
    private val appDatabase: AppDatabase by lazyKodein.instance()
    val networkProvider: NetworkDefinitionProvider by lazyKodein.instance()
    private val okHttpClient: OkHttpClient by lazyKodein.instance()
    private val handler = Handler(this)
    private var dialog: ProgressDialog? = null
    private val receiverTxn = TxnReceiver(this)
    private var transaction: Transaction? = null

    private val dialogCancelListener = DialogInterface.OnClickListener { dialog, _ ->
        dialog.dismiss()
        this@HitconBadgeActivity.setResult(android.app.Activity.RESULT_CANCELED)
        this@HitconBadgeActivity.finish()
    }


    private val mainProcess = Runnable {
        if (intent.hasHitconQrCode()) {
            handleInitialize(intent.getHitconQrCode().data)
        } else if (!badgeProvider.connected) {
            if (badgeProvider.device == null) {
                if (badgeProvider.entity == null) {
                    AlertDialog.Builder(this)
                            .setMessage(getString(R.string.message_need_init))
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                dialog.dismiss()
                                this@HitconBadgeActivity.finish()
                            }
                            .create().show()
                } else {
                    dialog = ProgressDialog.show(this@HitconBadgeActivity, getString(R.string.badge_title), getString(R.string.message_connecting_device))
                    badgeProvider.startScanDevice(object : BadgeProvider.BadgeCallback {
                        override fun onMtuChanged() {
                            dialog?.dismiss()
                            handler.sendEmptyMessage(MessageRestartProcess)
                        }

                        override fun onServiceDiscovered(bound: Boolean) {

                        }

                        override fun onTimeout() {
                            dialog?.dismiss()
                            AlertDialog.Builder(this@HitconBadgeActivity)
                                    .setMessage(getString(R.string.message_connect_timeout))
                                    .setCancelable(false)
                                    .setPositiveButton(android.R.string.ok, dialogCancelListener)
                                    .create().show()
                        }

                        override fun onDeviceFound(device: BluetoothDevice) {
                            dialog?.setMessage(getString(R.string.message_connecting_gatt))
                            badgeProvider.startConnectGatt(device, this)
                        }
                    })
                }
            } else { //gatt connect is fail
                dialog = ProgressDialog.show(this@HitconBadgeActivity, getString(R.string.badge_title), getString(R.string.message_connecting_gatt))
                badgeProvider.startConnectGatt(badgeProvider.device!!, object : BadgeProvider.BadgeCallback {
                    override fun onMtuChanged() {
                        dialog?.dismiss()
                        handler.sendEmptyMessage(MessageRestartProcess)
                    }

                    override fun onServiceDiscovered(bound: Boolean) {
                    }

                    override fun onTimeout() {
                        dialog?.dismiss()
                        AlertDialog.Builder(this@HitconBadgeActivity)
                                .setMessage(getString(R.string.message_connect_timeout))
                                .setCancelable(false)
                                .setPositiveButton(android.R.string.ok, dialogCancelListener)
                                .create().show()
                    }

                    override fun onDeviceFound(device: BluetoothDevice) {
                    }
                })
            }
        } else if (intent.hasTransaction()) {
            //intent has text, need sign
            transaction = intent.getTransaction().transaction
            transaction?.let {
                dialog = ProgressDialog.show(this@HitconBadgeActivity, getString(R.string.badge_title), getString(R.string.message_waiting_transaction))
                badgeProvider.startTransaction(it)
            }
        } else {
            setResult(Activity.RESULT_OK, Intent().apply { putExtra(KeyAddress, badgeProvider.entity?.address) })
            finish()
        }

    }

    private class TxnReceiver(val activity: HitconBadgeActivity) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            activity.dialog?.dismiss()
            val txn = intent?.getTxn()
            txn?.let {
                if (it.length > 4) {

//                    AlertDialog.Builder(activity)
//                            .setMessage("send or save?")
//                            .setPositiveButton(android.R.string.ok) { dialog, _ ->
//                                dialog.dismiss()
//                                async(UI) {
//                                    async(CommonPool) {
//                                        activity.getEtherscanResult("module=proxy&action=eth_sendRawTransaction&hex=$it", activity.networkProvider.getCurrent())
//                                    }.await()
//                                }
//                                activity.setResult(Activity.RESULT_OK)
//                                activity.finish()
//                            }
//                            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
//                                dialog.dismiss()
//                                activity.transaction?.let { transaction ->
//                                    async(UI) {
//                                        async(CommonPool) {
//                                            activity.appDatabase.transactions.upsert(transaction.toEntity(null, TransactionState(isPending = false, needsSigningConfirmation = false)))
//                                        }.await()
//                                    }
//                                }
//                                activity.setResult(Activity.RESULT_OK)
//                                activity.finish()
//                            }
//                            .create().show()
                    async(UI) {
                        async(CommonPool) {
                            val res = activity.getEtherscanResult("module=proxy&action=eth_sendRawTransaction&hex=$it", activity.networkProvider.getCurrent())
                            if(res != null && !res.has("error"))
                                activity.setResult(Activity.RESULT_OK)
                        }.await()
                    }
                    activity.finish()
                } else {
                    AlertDialog.Builder(activity)
                            .setCancelable(false)
                            .setTitle(R.string.badge_title)
                            .setMessage(R.string.message_transfer_cancel)
                            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                dialog.dismiss()
                                activity.setResult(Activity.RESULT_OK)
                                activity.finish()
                            }
                            .create().show()
                }
            }
        }
    }

    private fun getEtherscanResult(requestString: String, networkDefinition: NetworkDefinition) = try {
        getEtherscanResult(requestString, networkDefinition, true)
    } catch (e: CertPathValidatorException) {
        getEtherscanResult(requestString, networkDefinition, true)
    }

    private fun getEtherscanResult(requestString: String, networkDefinition: NetworkDefinition, httpFallback: Boolean): JSONObject? {
        try {
            val baseURL = networkDefinition.getBlockExplorer().baseAPIURL.letIf(httpFallback) {
                replace("https://", "http://") // :-( https://github.com/walleth/walleth/issues/134 )
            }
            val urlString = "$baseURL/api?$requestString&apikey=$" + BuildConfig.ETHERSCAN_APIKEY
            val url = Request.Builder().url(urlString).build()
            val newCall: Call = okHttpClient.newCall(url)


            val resultString = newCall.execute().body().use { it?.string() }
            resultString.let {
                return JSONObject(it)
            }
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }

        return null
    }

    private class Handler(val activity: HitconBadgeActivity) : android.os.Handler() {

        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
//                MessageReceiveTxn -> {
//                    activity.dialog?.dismiss()
//                    val tx = msg.data.getString(Txn)
//
//                    async(UI) {
//                        if (tx.length > 4) {
//                            async(CommonPool) {
//                                val url = "module=proxy&action=eth_sendRawTransaction&hex=$tx"
//                                val result = activity.getEtherscanResult(url, activity.networkDefinitionProvider.value!!)
//
//                                if (result != null) {
//                                    if (result.has("error")) {
//                                        var message = result.getJSONObject("error").getString("message")
//                                        activity.setResult(99, Intent().apply { putExtra("error", message) })
//                                    } else
//                                        activity.setResult(Activity.RESULT_OK, Intent().apply { putExtra(Txn, tx) })
//                                }
//                            }.await()
//                        } else {
//                            activity.setResult(99, Intent().apply { putExtra("error", "Transaction cancel") })
//                        }
//                        activity.finish()
//                    }
//
//
//                }
                MessageRestartProcess -> activity.handler.post(activity.mainProcess)
            }


        }
    }

    override fun finish() {
        releaseInstance()
        super.finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_badge)
        supportActionBar?.setSubtitle(R.string.badge_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED -> ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_PERMISSION)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED -> ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), REQUEST_PERMISSION)
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED -> ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_ADMIN), REQUEST_PERMISSION)
            !adapter.isEnabled -> startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_PERMISSION)
            else -> handler.post(mainProcess)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiverTxn, IntentFilter(BadgeProvider.ActionReceiveTxn))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiverTxn)
    }

    private fun handleInitialize(data: Map<String, String>) {
        val init = InitializeContent(data)
        Log.d(TAG, "service uuid: ${init.service}")
        dialog = ProgressDialog.show(this@HitconBadgeActivity, getString(R.string.badge_title), getString(R.string.message_connecting_device))
        badgeProvider.initializeBadge(init, object : BadgeProvider.BadgeCallback {
            private var bound = false
            override fun onMtuChanged() {
                dialog?.dismiss()
                this@HitconBadgeActivity.setResult(if (bound) Activity.RESULT_OK else 0, Intent().apply { putExtra(KeyAddress, init.address) })
                this@HitconBadgeActivity.finish()
            }

            override fun onServiceDiscovered(bound: Boolean) {
                this.bound = bound
            }

            override fun onTimeout() {
                dialog?.dismiss()
                AlertDialog.Builder(this@HitconBadgeActivity)
                        .setMessage(R.string.message_connect_timeout)
                        .setPositiveButton(android.R.string.ok, dialogCancelListener)
                        .setCancelable(false)
                        .create().show()
            }

            override fun onDeviceFound(device: BluetoothDevice) {
                dialog?.setMessage(getString(R.string.message_connecting_gatt))
                badgeProvider.startConnectGatt(device, this)
            }
        })
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                AlertDialog.Builder(this).setCancelable(false)
                        .setTitle(R.string.badge_title)
                        .setMessage(R.string.message_need_permission)
                        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                        .create().show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_PERMISSION -> when (resultCode) {
                Activity.RESULT_OK -> handler.post(mainProcess)
                else -> AlertDialog.Builder(this)
                        .setMessage(R.string.message_permission_bluetooth)
                        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                        .setCancelable(false)
                        .create().show()
            }
        }
    }
}