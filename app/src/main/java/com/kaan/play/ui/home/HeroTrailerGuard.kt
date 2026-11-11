package com.kaan.play.ui.home

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Cihaz donanımı ve ağ durumunu denetleyen yardımcı nesne.
 */
object HeroTrailerGuard {

    fun isWifiOrEthernet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        // Mobil veride ASLA
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        if (isCellular) return false

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return isWifi || isEthernet || isUnmetered
    }

    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isLowRamDevice
    }

    fun canPlay(
        context: Context,
        trailerSettingEnabled: Boolean,
        heroAutoAdvanceSetting: Boolean,
        perfPolicyHeroEnabled: Boolean,
    ): Boolean = HeroTrailerRules.canPlayTrailer(
        trailerSettingEnabled = trailerSettingEnabled,
        heroAutoAdvanceSetting = heroAutoAdvanceSetting,
        perfPolicyHeroEnabled = perfPolicyHeroEnabled,
        isLowRamDevice = isLowRam(context),
        isWifiOrEthernet = isWifiOrEthernet(context),
    )
}
