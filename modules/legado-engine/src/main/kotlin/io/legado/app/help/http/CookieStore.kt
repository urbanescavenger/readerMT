@file:Suppress("unused")

package io.legado.app.help.http

import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Cookie
import io.legado.app.help.http.api.CookieManagerInterface
import io.legado.app.platform.repo.Repositories
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * 引擎版 CookieStore(从 readerMT `help/http/CookieStore` 重构,实现 [CookieManagerInterface])。
 *
 * 与 readerMT 的差异(架构切分,计划 §3.4):
 * - **弃 `android.webkit.CookieManager`**:WebView cookie 同步移到 app 端 `WebViewRenderer`
 *   实现,引擎不碰;`setWebCookie` 与 `removeCookie` 里的 WebView 调用不在引擎。
 * - **`appDb.cookieDao` → `Repositories.cookie`**(SPI;NoOp 时引擎独立编译/单测)。
 * - **内存缓存**:`CacheManager` 的内存 LRU 换成本对象自带的 `ConcurrentHashMap`
 *   (`cookieMemory` 持久 cookie 缓存 + `sessionMemory` 会话 cookie),不再借 android 的 CacheManager。
 * - **`TextUtils` → `StringUtils`**;helper(`getCookieNoSession`/`getSessionCookie`/`removeCookieKey`)
 *   从 readerMT `CookieManager` 对象折叠进本对象(去 android/webkit)。
 */
object CookieStore : CookieManagerInterface {

    private val cookieMemory = ConcurrentHashMap<String, String>()
    private val sessionMemory = ConcurrentHashMap<String, String>()

    override fun setCookie(url: String, cookie: String?) {
        try {
            val domain = NetworkUtils.getSubDomain(url)
            cookieMemory[domain] = cookie ?: ""
            Repositories.cookie.insert(Cookie(domain, cookie ?: ""))
        } catch (e: Exception) {
            AppLog.put("保存Cookie失败\n$e", e)
        }
    }

    override fun replaceCookie(url: String, cookie: String) {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(cookie)) return
        val oldCookie = getCookieNoSession(url)
        if (StringUtils.isEmpty(oldCookie)) {
            setCookie(url, cookie)
        } else {
            val cookieMap = cookieToMap(oldCookie)
            cookieMap.putAll(cookieToMap(cookie))
            val newCookie = mapToCookie(cookieMap)
            setCookie(url, newCookie ?: "")
        }
    }

    override fun getCookie(url: String): String {
        val domain = NetworkUtils.getSubDomain(url)
        val cookie = getCookieNoSession(url)
        val sessionCookie = getSessionCookie(domain)
        val cookieMap = CookieUtils.mergeCookiesToMap(cookie, sessionCookie)
        var ck = mapToCookie(cookieMap) ?: ""
        while (ck.length > 4096) {
            val removeKey = cookieMap.keys.random()
            removeCookieKey(url, removeKey)
            cookieMap.remove(removeKey)
            ck = mapToCookie(cookieMap) ?: ""
        }
        return ck
    }

    fun getKey(url: String, key: String): String {
        val domain = NetworkUtils.getSubDomain(url)
        val cookie = getCookie(url)
        val sessionCookie = getSessionCookie(domain)
        val cookieMap = CookieUtils.mergeCookiesToMap(cookie, sessionCookie)
        return cookieMap[key] ?: ""
    }

    override fun removeCookie(url: String) {
        val domain = NetworkUtils.getSubDomain(url)
        Repositories.cookie.delete(domain)
        cookieMemory.remove(domain)
        sessionMemory.remove(domain)
        // android.webkit.CookieManager.getInstance().removeCookie(url) -> app 端 WebViewRenderer impl
    }

    override fun cookieToMap(cookie: String): MutableMap<String, String> = CookieUtils.cookieToMap(cookie)

    override fun mapToCookie(cookieMap: Map<String, String>?): String? = CookieUtils.mapToCookie(cookieMap)

    fun clear() {
        Repositories.cookie.deleteOkHttp()
    }

    // ---- 折叠的 helper(原 readerMT `CookieManager` 对象方法,去 android/webkit) ----

    /** 无会话 cookie:内存命中优先,否则 `Repositories.cookie` 持久化读取。原 getCookieNoSession。 */
    private fun getCookieNoSession(url: String): String {
        val domain = NetworkUtils.getSubDomain(url)
        val cached = cookieMemory[domain]
        if (cached != null) return cached
        return Repositories.cookie.get(domain)?.cookie ?: ""
    }

    /** 会话 cookie 字符串(原 `<domain>_session_cookie`)。 */
    private fun getSessionCookie(domain: String): String = sessionMemory[domain] ?: ""

    /** 删除单个 cookie 键(原 `CookieManager.removeCookie(url,key)`,去 android/CacheManager)。 */
    private fun removeCookieKey(url: String, key: String) {
        val domain = NetworkUtils.getSubDomain(url)
        sessionMemory[domain]?.let { cookieToMap(it) }
            ?.apply { remove(key) }
            ?.let { mapToCookie(it) }
            ?.let { ck -> sessionMemory[domain] = ck }
        val cookie = getCookieNoSession(url)
        if (cookie.isNotEmpty()) {
            val cookieMap = cookieToMap(cookie).apply { remove(key) }
            mapToCookie(cookieMap)?.let { setCookie(url, it) }
        }
    }
}