package com.jim.sharetocomputer.ext

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.OpenableColumns
import com.jim.sharetocomputer.Application
import com.jim.sharetocomputer.R
import com.jim.sharetocomputer.logging.MyLog
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface


internal fun Context.getAppName(): String = getString(R.string.app_name)

internal fun Context.getFileName(uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        contentResolver.query(uri, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result!!.lastIndexOf('/')
        if (cut != -1) {
            result = result!!.substring(cut + 1)
        }
    }
    return result ?: "unknown"
}

internal fun Context.getPrimaryIp(): String {
    MyLog.i("Primary Ip: ${(applicationContext as Application).primaryIp.value!!}")
    return (applicationContext as Application).primaryIp.value!!
}

public fun getUrl(ip: String, port: Int) = "http://$ip:$port"

internal fun Context.getPrimaryUrl(port: Int): String = getUrl(getPrimaryIp(), port)

public fun prioritizedIps(): List<String> {
    val intfs = ArrayList(NetworkInterface.getNetworkInterfaces().toList())
    intfs.sortByDescending {
        if (it.displayName.matches(Regex("wlan.*"))) {
            return@sortByDescending 1000
        }
        if (it.displayName.matches(Regex("rmnet.*"))) {
            return@sortByDescending 500
        }
        return@sortByDescending 10
    }
    return intfs.flatMap { intf ->
        Iterable { intf.inetAddresses.asSequence().filter { addr ->
            !addr.isLoopbackAddress && !addr.isLinkLocalAddress
        }.iterator() }
    }.sortedByDescending { ip ->
        if (ip is Inet4Address) {
            1000
        } else {
            0
        }
    }.map { it.hostAddress }.filterNotNull()
}

public fun getServerBaseUrlsFromIpPort(primaryIp: String, port: Int): String {
    val ipAddrs = prioritizedIps()

    MyLog.i("IP address: ${ipAddrs.joinToString()}")
    return ipAddrs.map { "http://$it:$port" }.joinToString("\n")
}

internal fun Context.getServerBaseUrls(port: Int): String = getServerBaseUrlsFromIpPort(getPrimaryIp(), port)

internal fun Context.getAppVersionName(): String {
    var v = ""
    try {
        v = packageManager.getPackageInfo(packageName, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
    }
    return v
}

internal fun Context.getAppVersionCode(): Long {
    var v = 0L
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            v = packageManager.getPackageInfo(packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            v = packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
        }
    } catch (e: PackageManager.NameNotFoundException) {
    }
    return v
}

internal fun Context.isOnWifi(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager? ?: return false
    connectivityManager.allNetworks.forEach {
        if (connectivityManager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) return true
    }
    return false
}

internal fun Context.convertDpToPx(dp: Float): Float {
    return dp * this.resources.displayMetrics.density
}
