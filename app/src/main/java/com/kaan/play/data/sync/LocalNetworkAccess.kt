package com.kaan.play.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Android 17 / targetSdk 37+ için LAN erişimi runtime izne bağlıdır. */
object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun requiresRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 37 && context.applicationInfo.targetSdkVersion >= 37

    fun isGranted(context: Context): Boolean =
        !requiresRuntimePermission(context) ||
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}
