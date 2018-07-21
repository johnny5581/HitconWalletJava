package org.hitcon.activities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import org.hitcon.BadgeProvider
import org.hitcon.data.qrcode.HitconQrCode
import org.hitcon.data.qrcode.InitializeContent
import org.hitcon.getTxn
import org.walleth.R
import org.walleth.activities.qrscan.KEY_SCAN_RESULT
import org.walleth.kethereum.android.TransactionParcel


const val KeyHitconQrCode = "hitcon_qr_code";
const val KeyHitconBadgeAddress = "hitcon_badge_address";
fun Intent.hasHitconQrCode() = this.hasExtra(KeyHitconQrCode)
fun Intent.getHitconQrCode() = this.getParcelableExtra<HitconQrCode>(KeyHitconQrCode)!!
fun Intent.hasBadgeAddress() = this.hasExtra(KeyHitconBadgeAddress)
fun Intent.getBadgeAddress() = this.getStringExtra(KeyHitconBadgeAddress)
fun Intent.hasTx() = this.hasExtra("TX")
fun Intent.getTx() = this.getParcelableExtra<TransactionParcel>("TX").transaction
class HitconBadgeActivity : AppCompatActivity() {
    companion object {
        const val TAG = "HitconBadge"
        const val MessageReceiveTxn = 0
        const val MessageRestartProcess = 1
        const val Txn = "TXN"
        const val REQUEST_LOCALE = 1000
    }

    private val badgeProvider: BadgeProvider by LazyKodein(appKodein).instance()

    private val handler = Handler(this)
    private val receiverTxn = TxnReceiver(handler)
    private val mainProcess = Runnable {
        if (intent.hasHitconQrCode()) {
            handleInitialize(intent.getHitconQrCode().data)
        } else if (!badgeProvider.connected) {
            //check connect, prepared connection
            if (badgeProvider.device == null) {
                if (badgeProvider.entity == null) {
                    AlertDialog.Builder(this)
                            .setMessage("Need init first")
                            .setPositiveButton("OK") { _, _ ->
                                this@HitconBadgeActivity.finish()
                            }
                            .create().show()
                } else {
                    val dialog = ProgressDialog.show(this@HitconBadgeActivity, "Hitcon Badge", "Connecting...")
                    badgeProvider.startScanDevice(object : BadgeProvider.BadgeCallback {
                        override fun onServiceDiscover() {
                            dialog.dismiss()
                            handler.sendEmptyMessage(MessageRestartProcess)
                        }

                        override fun onTimeout() {
                            dialog.dismiss()
                            AlertDialog.Builder(this@HitconBadgeActivity)
                                    .setMessage("Connection Timeout")
                                    .setPositiveButton("OK") { _, _ ->
                                        this@HitconBadgeActivity.finish()
                                    }
                                    .create().show()
                        }

                        override fun onDeviceFound(device: BluetoothDevice) {
                            dialog.setMessage("Device found, Connecting Gatt...")
                            badgeProvider.startConnectGatt(device, this)
                        }
                    })
                }
            } else { //gatt connect is fail
                val dialog = ProgressDialog.show(this@HitconBadgeActivity, "Hitcon Badge", "Device found, Connecting Gatt...")
                badgeProvider.startConnectGatt(badgeProvider.device!!, object : BadgeProvider.BadgeCallback {
                    override fun onServiceDiscover() {
                        dialog.dismiss()
                        handler.sendEmptyMessage(MessageRestartProcess)
                    }

                    override fun onTimeout() {
                    }

                    override fun onDeviceFound(device: BluetoothDevice) {
                    }
                })
            }
        } else if (intent.hasTx()) {
            //intent has text, need sign
            badgeProvider.startTransaction(intent.getTx())
        }
    }


    private class TxnReceiver(val handler: android.os.Handler) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.sendMessage(Message().apply {
                what = MessageReceiveTxn
                data.putString(Txn, intent?.getTxn())
            })
        }
    }


    private class BadgeStateReceiver(val handler: android.os.Handler) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.sendMessage(Message().apply {
                what = MessageReceiveTxn
                data.putString(Txn, intent?.getTxn())
            })
        }
    }

    private class Handler(val activity: HitconBadgeActivity) : android.os.Handler() {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                MessageReceiveTxn -> {
                    /* do some?  */
                }
                MessageRestartProcess -> activity.handler.post(activity.mainProcess)
            }


        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_badge)
        supportActionBar?.setSubtitle(R.string.badge_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiverTxn, IntentFilter(BadgeProvider.ActionReceiveTxn))


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCALE)
        else {
            handler.post(mainProcess)
        }
    }

    private fun handleInitialize(data: Map<String, String>) {
        val init = InitializeContent(data)
        Log.d(TAG, "service uuid: ${init.service}")
        val dialog = ProgressDialog.show(this@HitconBadgeActivity, "Hitcon Badge", "Connecting...")
        badgeProvider.initializeBadge(init, object : BadgeProvider.BadgeCallback {
            override fun onServiceDiscover() {
                dialog.dismiss()
                this@HitconBadgeActivity.setResult(Activity.RESULT_OK, Intent().apply { putExtra(KEY_SCAN_RESULT, init.address) })
                this@HitconBadgeActivity.finish()
            }

            override fun onTimeout() {
                dialog.dismiss()
                AlertDialog.Builder(this@HitconBadgeActivity)
                        .setMessage("Connection Timeout")
                        .setPositiveButton("OK") { _, _ ->
                            this@HitconBadgeActivity.finish()
                        }
                        .create().show()
            }

            override fun onDeviceFound(device: BluetoothDevice) {
                dialog.setMessage("Device found, Connecting Gatt...")
                badgeProvider.startConnectGatt(device, this)
            }
        })
    }

    override fun onDestroy() {
        Log.w(TAG, "destroy badge activity")
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiverTxn)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_badge, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_badge_connect).isEnabled = !badgeProvider.connected
        menu.findItem(R.id.menu_badge_disconnect).isEnabled = badgeProvider.connected
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.menu_badge_connect -> true.also {
            badgeProvider.startScanDevice()
        }
        R.id.menu_badge_disconnect -> true.also {

        }
        R.id.menu_badge_create -> true.also {

        }
        R.id.menu_badge_tx -> true.also {

        }
        R.id.menu_badge_update -> true.also {

        }
        android.R.id.home -> true.also {
            finish()
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_LOCALE) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Need locale permission.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    }
}