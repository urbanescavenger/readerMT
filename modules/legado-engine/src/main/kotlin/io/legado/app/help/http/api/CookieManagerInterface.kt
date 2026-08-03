package io.legado.app.help.http.api

/**
 * Cookie 管理 SPI(从 readerMT `help/http/api/CookieManagerInterface` 移植,纯接口)。
 *
 * 引擎 `CookieStore` 实现此接口;android.webkit 相关的 WebView cookie 同步不在引擎,
 * 由 app 端 `WebViewRenderer` 实现负责。
 */
interface CookieManagerInterface {

    /**
     * 保存cookie
     */
    fun setCookie(url: String, cookie: String?)

    /**
     * 替换cookie
     */
    fun replaceCookie(url: String, cookie: String)

    /**
     * 获取cookie
     */
    fun getCookie(url: String): String

    /**
     * 移除cookie
     */
    fun removeCookie(url: String)

    fun cookieToMap(cookie: String): MutableMap<String, String>

    fun mapToCookie(cookieMap: Map<String, String>?): String?
}