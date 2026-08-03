@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.legado.app.utils

import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import java.net.URL

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
}