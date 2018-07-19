package org.hitcon.activities

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.github.salomonbrys.kodein.LazyKodein
import com.github.salomonbrys.kodein.android.appKodein
import com.github.salomonbrys.kodein.instance
import kotlinx.android.synthetic.main.activity_badge.*
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
        const val ReceiveTxn = 0
        const val Txn = "TXN"
        const val REQUEST_LOCALE = 1000
    }

    private val badgeProvider: BadgeProvider by LazyKodein(appKodein).instance()

    private val handler = Handler(this)
    private val receiverTxn = TxnReceiver(handler)
    private var inited = false

    private class TxnReceiver(val handler: android.os.Handler) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.sendMessage(Message().apply {
                what = ReceiveTxn
                data.putString(Txn, intent?.getTxn())
            })
        }
    }


    private class BadgeStateReceiver(val handler: android.os.Handler) : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.sendMessage(Message().apply {
                what = ReceiveTxn
                data.putString(Txn, intent?.getTxn())
            })
        }
    }

    private class Handler(val activity: HitconBadgeActivity) : android.os.Handler() {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                ReceiveTxn -> {
                    /* do some?  */
                }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_LOCALE)
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCALE)
        }




        //if entity is not null, check device
        // if device is null then scan it
        // else if device is not null, then check device status and device service list is null
        //   if service list is null, scan it
        //   else, service is ready to receive command
//        if (badgeProvider.entity != null) {
//            if (badgeProvider.device == null)
//                badgeProvider.startScanDevice()
//            else if (badgeProvider.services.size == 0 || !badgeProvider.connected)
//                badgeProvider.startConnectGatt()
//        }


        if (intent.hasHitconQrCode()) {
            val init = InitializeContent(intent.getHitconQrCode().data)
            hitcon_badge_status.text = "Connecting..."
            badgeProvider.initializeBadge(init, object: BadgeProvider.BadgeCallback {
                override fun onServiceDiscover() {
                    this@HitconBadgeActivity.setResult(Activity.RESULT_OK, Intent().apply { putExtra(KEY_SCAN_RESULT, init.address) })
                    this@HitconBadgeActivity.finish()
                }

                override fun onTimeout() {
                    this@HitconBadgeActivity.finish()
                }

                override fun onDeviceFound(device: BluetoothDevice) {

                }
            })
        }else if(intent.hasTx()) {
            //begin sign
            badgeProvider.startTransaction(intent.getTx())
        }


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
        if(requestCode == REQUEST_LOCALE) {
            if(grantResults.any{ it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Need locale permission.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    }
}