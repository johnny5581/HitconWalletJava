package org.hitcon.activities

import android.Manifest
import android.app.Activity
import android.widget.Toast
import org.walleth.R
import org.walleth.activities.qrscan.PermissionChecker

class LocalePermission(activity: Activity) : PermissionChecker(activity, 33, Manifest.permission.ACCESS_COARSE_LOCATION) {
    override fun onPermissionDenied() {
        Toast.makeText(activity, R.string.message_need_permission, Toast.LENGTH_LONG).show()
        activity.finish()
    }
}