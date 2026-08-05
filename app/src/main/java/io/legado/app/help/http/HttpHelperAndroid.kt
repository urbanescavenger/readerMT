package io.legado.app.help.http

import io.legado.app.help.CacheManager
import io.legado.app.help.glide.progress.ProgressManager
import io.legado.app.help.glide.progress.ProgressResponseBody
import io.legado.app.model.ReadManga
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.splitNotBlank
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * 应用端网络层 —— 引擎 `HttpHelper` 只含纯 JVM `okHttpClient`/`getProxyClient`;
 * 以下 app 增强(漫画 client、cookieJar、WebView cookie 同步)留 `:app`(Android/glide 耦合)。
 */

/** cookieJar(原 `HttpHelper.cookieJar`;临时存书源启用 cookie 选项)。 */
val cookieJar: CookieJar by lazy {
    object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            val cookieBuilder = StringBuilder()
            cookies.forEachIndexed { index, cookie ->
                if (index > 0) cookieBuilder.append(";")
                cookieBuilder.append(cookie.name).append('=').append(cookie.value)
            }
            val domain = NetworkUtils.getSubDomain(url.toString())
            CacheManager.putMemory("${domain}_cookieJar", cookieBuilder.toString())
        }
    }
}

/** 漫画专用 client(原 `HttpHelper.okHttpClientManga`,加进度 + 限流拦截器)。 */
val okHttpClientManga: OkHttpClient by lazy {
    okHttpClient.newBuilder().run {
        val interceptors = interceptors()
        interceptors.add(1) { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            response.newBuilder()
                .body(ProgressResponseBody(request.url.toString(), ProgressManager.LISTENER, response.body))
                .build()
        }
        interceptors.add(1) { chain ->
            ReadManga.rateLimiter.withLimitBlocking {
                chain.proceed(chain.request())
            }
        }
        build()
    }
}

/** 同步 cookie 到 WebView(原 `CookieManager.applyToWebView`;android.webkit)。 */
fun CookieManager.applyToWebView(url: String) {
    val baseUrl = NetworkUtils.getBaseUrl(url) ?: return
    val cookies = CookieStore.getCookie(url).splitNotBlank(";")
    val webkitCookieManager = android.webkit.CookieManager.getInstance()
    webkitCookieManager.removeSessionCookies(null)
    cookies.forEach {
        webkitCookieManager.setCookie(baseUrl, it)
    }
}
