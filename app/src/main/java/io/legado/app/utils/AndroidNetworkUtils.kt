package io.legado.app.utils

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import splitties.systemservices.connectivityManager

/**
 * Android 专属网络工具 —— 引擎 `NetworkUtils` 是纯 JVM 子集(不含网络态检测),联网判断留 `:app`。
 * 以顶层扩展函数挂在引擎 object [NetworkUtils] 上,调用点 `NetworkUtils.isAvailable()` 语法不变。
 */
@SuppressLint("ObsoleteSdkInt")
@Suppress("DEPRECATION")
fun NetworkUtils.isAvailable(): Boolean {
    if (Build.VERSION.SDK_INT < 23) {
        val mWiFiNetworkInfo = connectivityManager.activeNetworkInfo
        if (mWiFiNetworkInfo != null) {
            return mWiFiNetworkInfo.type == ConnectivityManager.TYPE_WIFI ||
                mWiFiNetworkInfo.type == ConnectivityManager.TYPE_MOBILE ||
                mWiFiNetworkInfo.type == ConnectivityManager.TYPE_ETHERNET ||
                mWiFiNetworkInfo.type == ConnectivityManager.TYPE_VPN
        }
    } else {
        val network = connectivityManager.activeNetwork
        if (network != null) {
            val nc = connectivityManager.getNetworkCapabilities(network)
            if (nc != null) {
                return nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        }
    }
    return false
}
