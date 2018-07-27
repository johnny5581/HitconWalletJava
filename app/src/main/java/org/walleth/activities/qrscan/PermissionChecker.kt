package org.walleth.activities.qrscan

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.widget.Toast
import org.walleth.R

abstract class PermissionChecker(val activity: Activity, private val reqCode:Int, private val permission:String) {
    abstract fun onPermissionDenied();

    fun handleRequestResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == reqCode) {
            if (!grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
//                Toast.makeText(activity, R.string.no_camera_permission, Toast.LENGTH_LONG).show()
//                activity.finish()
                onPermissionDenied()
            }
        }
    }

    private fun getPermission() = ActivityCompat.checkSelfPermission(activity, permission)
    fun isGranted() = getPermission() == PackageManager.PERMISSION_GRANTED

    fun request() {
        ActivityCompat.requestPermissions(activity,
                arrayOf(permission),
                reqCode)
    }
}