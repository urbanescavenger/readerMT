@file:Suppress("unused")

package io.legado.app.help.http

import io.legado.app.platform.Platform
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 引擎版 okHttpClient(从 readerMT `help/http/HttpHelper` 拆出的平台无关基础 client)。
 *
 * 与 readerMT 的差异(架构切分):
 * - **trust-all SSL**:用引擎 [SSLHelper](JVM)替 android SSLHelper。
 * - **UA 头**:从 `Platform.appConfig.userAgent` 取(替 `AppConfig.userAgent`)。
 * - **去掉 android 专属**:`CookieManager` 的 cookieJar 网络拦截器(引擎由 AnalyzeUrl
 *   经 CookieStore 处理 cookie)、`AppConfig.addressCache` 自定义 DNS、`Cronet`。
 *   这些 app 增强在 switchover 时由 app 端在 `okHttpClient.newBuilder()` 上再叠加。
 * - 保留:OkHttpExceptionInterceptor + DecompressInterceptor + Keep-Alive 头 +
 *   followRedirects + trust-all + 线程工厂(OkhttpUncaughtExceptionHandler)。
 */
val okHttpClient: OkHttpClient by lazy {
    val specs = arrayListOf(
        ConnectionSpec.MODERN_TLS,
        ConnectionSpec.COMPATIBLE_TLS,
        ConnectionSpec.CLEARTEXT
    )
    val builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .sslSocketFactory(SSLHelper.unsafeSSLSocketFactory, SSLHelper.unsafeTrustManager)
        .retryOnConnectionFailure(true)
        .hostnameVerifier(SSLHelper.unsafeHostnameVerifier)
        .connectionSpecs(specs)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(OkHttpExceptionInterceptor)
        .addInterceptor { chain ->
            val request = chain.request()
            val b = request.newBuilder()
            if (request.header("User-Agent") == null) {
                b.addHeader("User-Agent", Platform.appConfig.userAgent)
            } else if (request.header("User-Agent") == "null") {
                b.removeHeader("User-Agent")
            }
            b.addHeader("Keep-Alive", "300")
            b.addHeader("Connection", "Keep-Alive")
            b.addHeader("Cache-Control", "no-cache")
            chain.proceed(b.build())
        }
        .addInterceptor(DecompressInterceptor)
    builder.build().apply {
        val executor = dispatcher.executorService as ThreadPoolExecutor
        executor.threadFactory = ThreadFactory { r ->
            Thread(r, "OkHttp Dispatcher").apply {
                isDaemon = false
                uncaughtExceptionHandler = OkhttpUncaughtExceptionHandler
            }
        }
    }
}