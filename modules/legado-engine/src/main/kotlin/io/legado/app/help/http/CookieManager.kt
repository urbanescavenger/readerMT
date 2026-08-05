@file:Suppress("unused")

package io.legado.app.help.http

import io.legado.app.constant.AppLog
import io.legado.app.help.CacheManager
import io.legado.app.utils.NetworkUtils
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Connection

/**
 * 引擎版 CookieManager(从 readerMT `help/http/CookieManager` 抽取平台无关部分)。
 *
 * `saveResponse`/`loadRequest`/session cookie 逻辑经引擎 `CookieStore`/`CacheManager`/`NetworkUtils`,
 * 纯 JVM(不触 android.webkit);`applyToWebView`(android.webkit.CookieManager 同步)留 `:app`。
 */
object CookieManager {

    /** 书源启用 cookieJar 时的请求头键。 */
    const val cookieJarHeader = "CookieJar"

    /** 合并多个 cookie 字符串(后者覆盖前者同键)。 */
    fun mergeCookies(vararg cookies: String?): String? {
        val cookieMap = CookieUtils.mergeCookiesToMap(*cookies)
        return CookieUtils.mapToCookie(cookieMap)
    }

    /** 从 okhttp 响应保存 cookies。 */
    fun saveResponse(response: Response) {
        val url = response.request.url
        val headers = response.headers
        saveCookiesFromHeaders(url, headers)
    }

    /** 从 jsoup 响应保存 cookies。 */
    fun saveResponse(response: Connection.Response) {
        val url = response.url().toHttpUrlOrNull() ?: return
        val headerMap = response.multiHeaders()
        val headers = Headers.Builder().apply {
            headerMap.forEach { (k, v) -> v.forEach { add(k, it) } }
        }.build()
        saveCookiesFromHeaders(url, headers)
    }

    private fun saveCookiesFromHeaders(url: HttpUrl, headers: Headers) {
        val domain = NetworkUtils.getSubDomain(url.toString())
        val cookies = Cookie.parseAll(url, headers)

        val sessionCookie = cookies.filter { !it.persistent }.getString()
        updateSessionCookie(domain, sessionCookie)

        val cookieString = cookies.filter { it.persistent }.getString()
        CookieStore.replaceCookie(domain, cookieString)
    }

    /** 加载 cookies 到请求。 */
    fun loadRequest(request: Request): Request {
        val url = request.url.toString()
        val domain = NetworkUtils.getSubDomain(url)

        val cookie = CookieStore.getCookie(domain)
        val requestCookie = request.header("Cookie")

        val newCookie = mergeCookies(requestCookie, cookie) ?: return request

        kotlin.runCatching {
            return request.newBuilder()
                .header("Cookie", newCookie)
                .build()
        }.onFailure {
            CookieStore.removeCookie(url)
            val msg = "设置cookie出错，已清除cookie $domain cookie:$newCookie\n$it"
            AppLog.put(msg, it)
        }

        return request
    }

    private fun getSessionCookieMap(domain: String): MutableMap<String, String>? {
        return getSessionCookie(domain)?.let { CookieStore.cookieToMap(it) }
    }

    fun getSessionCookie(domain: String): String? {
        return CacheManager.getFromMemory("${domain}_session_cookie") as? String
    }

    private fun updateSessionCookie(domain: String, cookies: String) {
        val sessionCookie = getSessionCookie(domain)
        if (sessionCookie.isNullOrEmpty()) {
            CacheManager.putMemory("${domain}_session_cookie", cookies)
            return
        }
        val ck = mergeCookies(sessionCookie, cookies) ?: return
        CacheManager.putMemory("${domain}_session_cookie", ck)
    }

    private fun List<Cookie>.getString() = buildString {
        this@getString.forEachIndexed { index, cookie ->
            if (index > 0) append(";")
            append(cookie.name).append('=').append(cookie.value)
        }
    }
}