package com.pfe.gestionetudiantmobile.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object ConnectivityMonitor {

    fun isOnline(context: Context?): Boolean {
        if (context == null) return true
        val connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
