package io.legado.app.help.http

import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * 引擎版 SSLHelper(从 readerMT `help/http/SSLHelper` 移植,去 android)。
 *
 * 只移植书源 HTTP 需要的 **trust-all** 三件套(`unsafeTrustManager`/
 * `unsafeSSLSocketFactory`/`unsafeHostnameVerifier`),让引擎能抓 https 自签/坏证书站点
 * (与 app 的 unsafe SSL 行为一致)。去掉了 android 的 `@SuppressLint`、
 * `X509TrustManagerExtensions`(仅 SSLHelper 自身/CronetHelper 用)以及 BKS/证书双向认证
 * 的 `getSslSocketFactory` 变体(书源核心不需要,需要时再补)。
 *
 * ⚠ trust-all 有安全风险(接受任意证书),沿用 readerMT 行为;parity 由 §5c 守。
 */
@Suppress("unused")
object SSLHelper {

    val unsafeTrustManager: X509TrustManager = object : X509TrustManager {
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            // 接受任意客户端证书
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            // 接受任意服务端证书
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val unsafeSSLSocketFactory: SSLSocketFactory by lazy {
        try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, arrayOf(unsafeTrustManager), SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    val unsafeHostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }
}