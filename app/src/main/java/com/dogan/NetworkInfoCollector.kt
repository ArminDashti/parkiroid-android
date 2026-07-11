package com.dogan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import java.net.Inet4Address
import java.net.NetworkInterface

/** Collects network signal, type, and local IP address. */
object NetworkInfoCollector {
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isInternetReachable(): Boolean {
        return try {
            val request = okhttp3.Request.Builder()
                .url("https://www.google.com")
                .head()
                .build()
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun measureInternetLatencyMs(): Long {
        val start = System.currentTimeMillis()
        return try {
            val request = okhttp3.Request.Builder()
                .url("https://www.google.com")
                .head()
                .build()
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use {
                    if (it.isSuccessful) System.currentTimeMillis() - start else -1L
                }
        } catch (_: Exception) {
            -1L
        }
    }

    fun getNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "NONE"
        val caps = cm.getNetworkCapabilities(network) ?: return "NONE"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WIFI"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return getCellularGeneration(context)
        }
        return "UNKNOWN"
    }

    fun getSignalStrengthDbm(context: Context): Int {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signal = tm.signalStrength
                signal?.cellSignalStrengths?.firstOrNull()?.dbm ?: -1
            } else {
                -1
            }
        } catch (_: Exception) {
            -1
        }
    }

    fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress ?: "0.0.0.0"
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }

    private fun getCellularGeneration(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_NR -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A -> "3G"
                else -> "CELLULAR"
            }
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }
}
