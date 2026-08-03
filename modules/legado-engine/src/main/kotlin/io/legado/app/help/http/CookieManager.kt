@file:Suppress("unused")

package io.legado.app.help.http

/**
 * 引擎版 CookieManager(极简子集,从 readerMT `help/http/CookieManager` 抽取)。
 *
 * readerMT 的 `CookieManager` 含大量 android.webkit.CookieManager + appDb + session cookie 逻辑;
 * 引擎只取 AnalyzeUrl 需要的 **`cookieJarHeader` 常量** 与 **`mergeCookies`** 合并助手。
 * 其余 session/saveResponse/loadRequest/applyToWebView 留 `:app`(android.webkit 耦合)。
 */
object CookieManager {

    /** 书源启用 cookieJar 时的请求头键。 */
    const val cookieJarHeader = "CookieJar"

    /** 合并多个 cookie 字符串(后者覆盖前者同键)。 */
    fun mergeCookies(vararg cookies: String?): String? {
        val cookieMap = CookieUtils.mergeCookiesToMap(*cookies)
        return CookieUtils.mapToCookie(cookieMap)
    }
}