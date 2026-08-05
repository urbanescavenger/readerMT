@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.legado.app.utils

import io.legado.app.constant.AppLog
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URL
import java.util.BitSet
import java.util.Enumeration

/**
 * 从 readerMT `utils/NetworkUtils` 抽出的纯 JVM 域名工具子集。
 *
 * 原 `NetworkUtils` 含大量 Android 依赖(ConnectivityManager/Build/splitties/hutool Validator/
 * AppConfig);此处仅抽取 cookie/URL 处理需要的纯 JVM 部分(getBaseUrl/isIPAddress/getSubDomain)。
 * Android 专属方法(联网判断等)留 `:app`。同包同名,当前两端不在同一 classpath,无冲突;
 * switchover 时 app 端 Android-only 方法需另立对象或内联。
 */
object NetworkUtils {

    private val ipv4Regex = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")

    fun getBaseUrl(url: String?): String? {
        url ?: return null
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            val index = url.indexOf("/", 9)
            return if (index == -1) url else url.substring(0, index)
        }
        return null
    }

    /** 纯 JVM IP 判定(原版用 hutool Validator;此处用正则 IPv4 + ':' IPv6,足够 cookie 域名键用)。 */
    fun isIPAddress(input: String?): Boolean {
        if (input == null) return false
        if (ipv4Regex.matches(input)) return true
        return input.contains(":")
    }

    /**
     * 获取域名,供 cookie 保存和读取,处理失败返回传入的 url。
     * http://1.2.3.4 => 1.2.3.4 ; https://www.example.com => example.com
     */
    fun getSubDomain(url: String): String {
        val baseUrl = getBaseUrl(url) ?: return url
        return kotlin.runCatching {
            val mURL = URL(baseUrl)
            val host: String = mURL.host
            if (isIPAddress(host)) return host
            // PublicSuffixDatabase 需平台初始化(读 public suffix list);未初始化时返回 null -> 回退 host。
            PublicSuffixDatabase.get().getEffectiveTldPlusOne(host) ?: host
        }.getOrDefault(baseUrl)
    }

    fun getSubDomainOrNull(url: String): String? {
        return kotlin.runCatching { getSubDomain(url) }.getOrNull()
    }

    fun getDomain(url: String): String {
        val baseUrl = getBaseUrl(url) ?: return url
        return kotlin.runCatching {
            URL(baseUrl).host
        }.getOrDefault(baseUrl)
    }

    /** IPv4 判定(原 app 用 hutool Validator.isIpv4;引擎以正则近似,parity 留 §5c)。 */
    fun isIPv4Address(input: String?): Boolean {
        if (input == null || input.isEmpty()) return false
        return ipv4Regex.matches(input)
    }

    /** 本机非 loopback 的 IPv4 地址(app WebService 展示本机 IP 用)。 */
    fun getLocalIPAddress(): List<InetAddress> {
        val enumeration: Enumeration<NetworkInterface>
        try {
            enumeration = NetworkInterface.getNetworkInterfaces()
        } catch (e: SocketException) {
            e.printOnDebug()
            return emptyList()
        }
        val addressList = mutableListOf<InetAddress>()
        while (enumeration.hasMoreElements()) {
            val nif = enumeration.nextElement()
            val addresses = nif.inetAddresses ?: continue
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && isIPv4Address(address.hostAddress)) {
                    addressList.add(address)
                }
            }
        }
        return addressList
    }

    // ---- URL 编码判定(batch 2a,从 app NetworkUtils 抽纯 JVM 子集)----

    private val notNeedEncodingQuery: BitSet by lazy {
        val bitSet = BitSet(256)
        for (i in 'a'.code..'z'.code) bitSet.set(i)
        for (i in 'A'.code..'Z'.code) bitSet.set(i)
        for (i in '0'.code..'9'.code) bitSet.set(i)
        for (char in "!\$&()*+,-./:;=?@[\\]^_`{|}~") bitSet.set(char.code)
        bitSet
    }

    private val notNeedEncodingForm: BitSet by lazy {
        val bitSet = BitSet(256)
        for (i in 'a'.code..'z'.code) bitSet.set(i)
        for (i in 'A'.code..'Z'.code) bitSet.set(i)
        for (i in '0'.code..'9'.code) bitSet.set(i)
        for (char in "*-._") bitSet.set(char.code)
        bitSet
    }

    private fun isDigit16Char(c: Char): Boolean {
        return c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
    }

    fun encodedQuery(str: String): Boolean {
        var needEncode = false
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (notNeedEncodingQuery.get(c.code)) { i++; continue }
            if (c == '%' && i + 2 < str.length) {
                val c1 = str[++i]
                val c2 = str[++i]
                if (isDigit16Char(c1) && isDigit16Char(c2)) { i++; continue }
            }
            needEncode = true
            break
        }
        return !needEncode
    }

    fun encodedForm(str: String): Boolean {
        var needEncode = false
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (notNeedEncodingForm.get(c.code)) { i++; continue }
            if (c == '%' && i + 2 < str.length) {
                val c1 = str[++i]
                val c2 = str[++i]
                if (isDigit16Char(c1) && isDigit16Char(c2)) { i++; continue }
            }
            needEncode = true
            break
        }
        return !needEncode
    }

    /**
     * 获取绝对地址
     */
    fun getAbsoluteURL(baseURL: String?, relativePath: String): String {
        if (baseURL.isNullOrEmpty()) return relativePath.trim()
        var absoluteUrl: URL? = null
        try {
            absoluteUrl = URL(baseURL.substringBefore(","))
        } catch (e: Exception) {
            e.printOnDebug()
        }
        return getAbsoluteURL(absoluteUrl, relativePath)
    }

    fun getAbsoluteURL(baseURL: URL?, relativePath: String): String {
        val relativePathTrim = relativePath.trim()
        if (baseURL == null) return relativePathTrim
        if (relativePathTrim.isAbsUrl()) return relativePathTrim
        if (relativePathTrim.isDataUrl()) return relativePathTrim
        if (relativePathTrim.startsWith("javascript")) return ""
        var relativeUrl = relativePathTrim
        try {
            val parseUrl = URL(baseURL, relativePath)
            relativeUrl = parseUrl.toString()
            return relativeUrl
        } catch (e: Exception) {
            AppLog.put("网址拼接出错\n${e.localizedMessage}", e)
        }
        return relativeUrl
    }
}