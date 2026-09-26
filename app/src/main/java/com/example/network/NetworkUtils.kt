package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String {
        try {
            // First check Wi-Fi connection
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                @Suppress("DEPRECATION")
                val formattedIp = Formatter.formatIpAddress(ipInt)
                if (formattedIp != "0.0.0.0") return formattedIp
            }

            // Iterate through network interfaces (wlan0, p2p0, ap0, rndis0)
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    fun isConnectedToLocalNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun calculateSha256(inputStream: InputStream, onProgress: ((bytesRead: Long) -> Unit)? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var read: Int
        var total = 0L
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
            total += read
            onProgress?.invoke(total)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 KB/s"
        val mbPerSec = bytesPerSecond.toDouble() / (1024 * 1024)
        return if (mbPerSec >= 1.0) {
            "%.1f MB/s".format(mbPerSec)
        } else {
            "%.0f KB/s".format(bytesPerSecond.toDouble() / 1024)
        }
    }
}
